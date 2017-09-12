package iproduct

import java.util.regex.Pattern

import iproduct.classifier1.BuildPages.Config
import iproduct.utils.{FilterArchive, SparkUtils, URLUtils}
import org.warcbase.spark.archive.io.ArchiveRecord
import org.warcbase.spark.matchbox.RemoveHTML

import scalax.file.Path

object FilterArchivePatents {
  val appName = "FilterArchivePatents"

  case class Config(
                     in: String = "",
                     discardedDomainsFile: Option[String] = Some(CrawlerFilterConfig.discardedDomainsFile),
                     outDir: String = "",
                     patentKeywordPattern: String = CrawlerFilterConfig.patentKeywordPattern,
                     patentNumberPattern: String = CrawlerFilterConfig.patentNumberRegex,
                     s3Conf: SparkUtils.S3Conf = SparkUtils.S3Conf()
                   )
  val parser = new scopt.OptionParser[Config](appName) {
    override def showUsageOnError = true

    head("Takes a web archive, filters the pages who have the patent keyword and at least one patent number, and writes an output web archive.")

    def stringOption(value: String): Option[String] = if (value.isEmpty) None else Some(value)

    opt[String]("in").required.action((x, c) =>
      c.copy(in = x)).text("input warc.gz (file or folder)")

    opt[String]("discardedDomainsFile").action((x, c) =>
      c.copy(discardedDomainsFile = stringOption(x))).text("file with a list of domain to discard. default = " + Config().discardedDomainsFile)

    opt[String]("outDir").action((x, c) =>
      c.copy(outDir = x)).text("output dir")

    opt[String]("patentKeywordPattern").action((x, c) =>
      c.copy(patentKeywordPattern = x)).text("patentKeywordPattern. default=" + Config().patentKeywordPattern)

    opt[String]("patentNumberPattern").action((x, c) =>
      c.copy(patentNumberPattern = x)).text("patentNumberRegex. default=" + Config().patentNumberPattern)

    opt[String]("AWS_ACCESS_KEY_ID").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsAccessKeyId = x)))

    opt[String]("AWS_SECRET_ACCESS_KEY").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsSecretAccessKey = x)))

    help("help").text("prints this usage text")
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        println("config: " + config.copy(s3Conf = null))

        // for local file system. TODO for s3n
        Path.fromString(config.outDir).deleteRecursively(continueOnFailure = false)
        Path.fromString(config.outDir).doCreateDirectory()

        run(
          config.in,
          URLUtils.discardedDomains(config.discardedDomainsFile).map(Pattern.compile),
          config.outDir,
          Pattern.compile(config.patentKeywordPattern),
          Pattern.compile(config.patentNumberPattern),
          config.s3Conf
        )

      case None => sys.exit(1)
    }
  }

  def run(
           in: String,
           discardedDomains: Set[Pattern],
           outDir: String,
           patentKeywordPattern: Pattern,
           patentNumberPattern: Pattern,
           s3Conf: SparkUtils.S3Conf) {

    val sc = SparkUtils.initSpark(appName)
    SparkUtils.setupS3(sc, s3Conf)

    val urlFromDiscardedDomain = URLUtils.urlIsInDomains(discardedDomains) _

    def pageHasPatentKeyword(text: String) =
      patentKeywordPattern.matcher(text).find

    def pageHasPatentNumber(text: String) =
      patentNumberPattern.matcher(text).find

    def filter(r: ArchiveRecord): Boolean = {
      if (urlFromDiscardedDomain(r.getUrl)) false
      else {
        val text = CrawlerFilterConfig.cleanSpaces(RemoveHTML(r.getContentString))
        if (!pageHasPatentKeyword(text)) false
        else pageHasPatentNumber(text)
      }
    }

    FilterArchive.run(in, outDir, filter, sc)
  }
}
