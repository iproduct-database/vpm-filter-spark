package iproduct.classifier1

import java.util.regex.Pattern

import iproduct.CrawlerFilterConfig
import iproduct.classifier1.Utils.DBOut
import iproduct.utils.Utils.using
import iproduct.utils._
import org.apache.spark.rdd.RDD
import org.warcbase.spark.archive.io.ArchiveRecord
import org.warcbase.spark.matchbox.{RecordLoader, RemoveHTML}
import org.warcbase.spark.rdd.RecordRDD._

import scala.util.matching.Regex
import scalax.file.Path

object BuildPages {
  val appName = "BuildPages"

  val additionalUrlRegexs: List[String] = List(
    "(?i)virtual.?patent.?marking",
    "(?i)America.?Invents.?Act",
    "(?<!\\d)287(?!\\d)",
    "(?i)\\bvpm\\b"
  )

  val additionalTextRegexs: List[String] = additionalUrlRegexs ::: List(
    "(?i)protected by([^ ]+ )? patents",
    "(?i)following.*list.*products.*inclusive",
    "(?i)additional patents.*pending",
    "(?i)this.*provided.*satisfy.*patent"
  )

  case class Config(
                     in: String = "",
                     discardedDomainsFile: Option[String] = Some(CrawlerFilterConfig.discardedDomainsFile),
                     out: Option[String] = None,
                     dbOut: DBOut = DBOut(dbUrl = ""),
                     patentKeywordPattern: String = CrawlerFilterConfig.patentKeywordPattern,
                     patentNumberRegex: String = CrawlerFilterConfig.patentNumberRegex,
                     additionalTextRegexs: List[String] = additionalTextRegexs,
                     additionalUrlRegexs: List[String] = additionalUrlRegexs,
                     contextLength: Int = 10,
                     s3Conf: SparkUtils.S3Conf = SparkUtils.S3Conf()
                   )

  val parser = new scopt.OptionParser[Config](appName) {
    override def showUsageOnError = true

    def stringOption(value: String): Option[String] = if (value.isEmpty) None else Some(value)

    opt[String]("in").required.action((x, c) =>
      c.copy(in = x)).text("input warc.gz (file or folder)")

    opt[String]("discardedDomainsFile").action((x, c) =>
      c.copy(discardedDomainsFile = stringOption(x))).text("file with a list of domain to discard. default = " + Config().discardedDomainsFile)

    opt[String]("out").action((x, c) =>
      c.copy(out = stringOption(x))).text("output file")

    opt[String]("dbUrl").action((x, c) =>
      c.copy(dbOut = c.dbOut.copy(dbUrl = x))).text("dbUrl, where tables cc_pages and cc_page_patents will be created")

    opt[Boolean]("createTables").action((x, c) =>
      c.copy(dbOut = c.dbOut.copy(createTables = x))).text("default=" + Config().dbOut.createTables)

    opt[String]("pagesTable").action((x, c) =>
      c.copy(dbOut = c.dbOut.copy(pagesTable = x))).text("default=" + Config().dbOut.pagesTable)

    opt[String]("pagePatentsTable").action((x, c) =>
      c.copy(dbOut = c.dbOut.copy(pagePatentsTable = x))).text("default=" + Config().dbOut.pagePatentsTable)

    opt[String]("pageRegexsTable").action((x, c) =>
      c.copy(dbOut = c.dbOut.copy(pageRegexsTable = x))).text("default=" + Config().dbOut.pageRegexsTable)

    opt[String]("patentKeywordPattern").action((x, c) =>
      c.copy(patentKeywordPattern = x)).text("patentKeywordPattern. default=" + Config().patentKeywordPattern)

    opt[String]("patentNumberRegex").action((x, c) =>
      c.copy(patentNumberRegex = x)).text("patentNumberRegex. default=" + Config().patentNumberRegex)

    opt[String]("additionalTextRegexs...").unbounded().optional().action((x, c) =>
      c.copy(additionalTextRegexs = c.additionalTextRegexs :+ x)).text("optional additional text regexs. default=" + Config().additionalTextRegexs)

    opt[String]("additionalUrlRegexs...").unbounded().optional().action((x, c) =>
      c.copy(additionalUrlRegexs = c.additionalUrlRegexs :+ x)).text("optional additional url regexs. default=" + Config().additionalUrlRegexs)

    opt[Int]("contextLength").action((x, c) =>
      c.copy(contextLength = x)).text("context size from left and right of the patentNumberRegex match. default=" + Config().contextLength)

    opt[String]("AWS_ACCESS_KEY_ID").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsAccessKeyId = x)))

    opt[String]("AWS_SECRET_ACCESS_KEY").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsSecretAccessKey = x)))

    help("help").text("prints this usage text")
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        println("+++ config: " + config.copy(s3Conf = null))

        // for local file system. TODO for s3n
        config.out.foreach { out =>
          Path.fromString(out).deleteRecursively(continueOnFailure = false)
//          Path.fromString(out).doCreateDirectory()
        }

        val dbOutOpt = if (config.dbOut.dbUrl.isEmpty) None else Some(config.dbOut)

        require(config.out.isDefined || dbOutOpt.isDefined, "either out or dbUrl must be defined")

        dbOutOpt.foreach(implicit dbOut => if (dbOut.createTables)
          using(DatabaseUtils.getDbConnection(dbOut.dbUrl)) { implicit conn =>
            Utils.createTables()
          }
        )

        run(
          config.in,
          URLUtils.discardedDomains(config.discardedDomainsFile).map(Pattern.compile),
          config.out,
          dbOutOpt,
          Pattern.compile(config.patentKeywordPattern),
          config.patentNumberRegex.r,
          config.additionalTextRegexs.map(_.r),
          config.additionalUrlRegexs.map(_.r),
          config.contextLength,
          config.s3Conf
        )

      case None => sys.exit(1)
    }
  }

  def run(
           in: String,
           discardedDomains: Set[Pattern],
           outOpt: Option[String],
           dbOutOpt: Option[DBOut],
           patentKeywordPattern: Pattern,
           patentNumberRegex: Regex,
           additionalTextRegexs: List[Regex],
           additionalUrlRegexs: List[Regex],
           contextLength: Int,
           s3Conf: SparkUtils.S3Conf) {

    EnvUtils.showJavaAndScalaProps()

    val sc = SparkUtils.initSpark(appName)
    SparkUtils.setupS3(sc, s3Conf)
    EnvUtils.showSparkVersion(sc)

    def pageHasPatentKeyword(text: String) =
      patentKeywordPattern.matcher(text).find

    def findAllMatchesIn(regex: Regex, text: String): List[Match] =
      regex.findAllMatchIn(text).map(m => Match(m.matched, getContext(text, m.start, m.end))).toList

    def getContext(text: String, start: Int, end: Int) =
      text.substring((start - contextLength).max(0), (end + contextLength).min(text.length))

    def countWords(text: String) = {
      val words = text.toLowerCase.replaceAll("[^\\p{IsAlphabetic}]+", " ").split(" ")
      val numWords = words.size
      val distinctWords = words.toSet
      (numWords, distinctWords)
    }

    val urlFromDiscardedDomain = URLUtils.urlIsInDomains(discardedDomains) _

    val webPages: RDD[ArchiveRecord] = RecordLoader.loadArchives(in, sc)

    val htmlPages: RDD[(String, String)] = webPages
      .filter(r => !urlFromDiscardedDomain(r.getUrl))
      .keepValidPages()
      .map(r => (URLUtils.normalizeUrl(r.getUrl).getOrElse(r.getUrl), CrawlerFilterConfig.cleanSpaces(RemoveHTML(r.getContentString))))

    val pagesWithPatentKeyword: RDD[(String, String)] =
      htmlPages.filter { case (url, text) => pageHasPatentKeyword(text) }

    def extractInfo(url: String, text: String): Option[Page] = {
      val domain = URLUtils.getTopDomainFromUrl(url).getOrElse("")
      val patentNumbers = findAllMatchesIn(patentNumberRegex, text)
      val additionalTextMatches = additionalTextRegexs.map(r => (r.toString, findAllMatchesIn(r, text))).toMap
      val additionalUrlMatches = additionalUrlRegexs.map(r => (r.toString, findAllMatchesIn(r, url))).toMap

      if (patentNumbers.nonEmpty) {
        val (numWords, distinctWords) = countWords(text)
        val s: List[Sequence] = patentSequences(patentNumberRegex)(text)
        Some(Page(url, domain, patentNumbers, additionalTextMatches, additionalUrlMatches, numWords, distinctWords, patentSequences = s, longestPatentSequenceSize(s)))
      } else None
    }

    val patentPages: RDD[Page] =
      pagesWithPatentKeyword.flatMap { case (url, text) => extractInfo(url, text) }

    outOpt.foreach(out =>
      patentPages.saveAsObjectFile(out)
    )

    dbOutOpt.foreach(implicit dbOut =>
      patentPages.foreachPartition(iter => {
        using(DatabaseUtils.getDbConnection(dbOut.dbUrl)) { implicit conn =>
          iter.foreach { (e: Page) =>
            try { Utils.importToMysql(e) }
            catch {
              case t: Throwable =>
                println("+++ error exporting to mysql: " + e.url)
                println("+++ error exporting to mysql, details: " + e)
                t.printStackTrace()
            }
          }
        }
      })
    )

    sc.stop()

    println("+++ END.")
  }

  val nonWordCharacterR: Regex = "[^\\p{IsAlphabetic}0-9]".r
  val someWordsR = "(?i)\\b(patents?|US|U.?S.?)\\b".r
  val patentNumberReplacement = "patentnumberfound"
  def patentSequences(patentNumberRegex: Regex)(textPage: String): List[Sequence] = {
    val tmp1 = patentNumberRegex.replaceAllIn(textPage, " " + patentNumberReplacement + " ")
    val tmp2 = someWordsR.replaceAllIn(tmp1, " ")
    val tmp3 = nonWordCharacterR.replaceAllIn(tmp2, " ")
    val tmp4 = tmp3.replaceAll(" +", " ")
    sequences(tmp4.split(" ").map(_ == patentNumberReplacement))
  }

  def longestPatentSequenceSize(seqs: List[Sequence]): Int = (0 :: seqs.map(_.length)).max

  def sequences(list: Seq[Boolean]): List[Sequence] = {
    val initialSsequences = List.empty[Sequence]
    val initialCurrSequence: Option[(Int, Int)] = None
    val (finalSequences, finalCurrSequence) =
      list.zipWithIndex.foldLeft((initialSsequences, initialCurrSequence)) { case (acc@(sequences, currSequence), (value, index)) => (currSequence, value) match {
        case (Some((start, end)), true) => (sequences, Some(start, index))
        case (Some(seq@(start, end)), false) => (Sequence(seq._1, seq._2) :: sequences, None)
        case (None, true) => (sequences, Some(index, index))
        case (None, false) => acc
      }
      }

    finalCurrSequence.map(seq => Sequence(seq._1, seq._2) :: finalSequences).getOrElse(finalSequences).reverse
  }
}
