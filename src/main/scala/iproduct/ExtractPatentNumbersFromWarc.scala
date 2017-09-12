package iproduct

import java.util.regex.Pattern

import iproduct.utils.{EnvUtils, SparkUtils}
import org.apache.spark.rdd.RDD
import org.warcbase.spark.matchbox.{RecordLoader, RemoveHTML}
import org.warcbase.spark.rdd.RecordRDD._

import scala.util.matching.Regex
import scalax.file.Path

object ExtractPatentNumbersFromWarc {
  val appName = "ExtractPatentNumbersFromWarc"
  case class Config(
                     in: String = "",
                     out: String = "",
                     patentKeywordPattern: String = CrawlerFilterConfig.patentKeywordPattern,
                     patentNumberRegex: String = CrawlerFilterConfig.patentNumberRegex,
                     s3Conf: SparkUtils.S3Conf = SparkUtils.S3Conf()
                   )

  val parser = new scopt.OptionParser[Config](appName) {
    override def showUsageOnError = true

    head("Takes a web archive, and writes a text file with a line for each page with the url and patent numbers found.\n(It filters the pages who have the patent keyword and at least one patent number)")

    opt[String]("in").required.action((x, c) =>
      c.copy(in = x)).text("input warc.gz (file or folder)")

    opt[String]("out").required.action((x, c) =>
      c.copy(out = x)).text("output")

    opt[String]("patentKeywordPattern").action((x, c) =>
      c.copy(patentKeywordPattern = x)).text("patentKeywordPattern. default=" + Config().patentKeywordPattern)

    opt[String]("patentNumberRegex").action((x, c) =>
      c.copy(patentNumberRegex = x)).text("patentNumberRegex. default=" + Config().patentNumberRegex)

    opt[String]("AWS_ACCESS_KEY_ID").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsAccessKeyId = x)))

    opt[String]("AWS_SECRET_ACCESS_KEY").action((x, c) =>
      c.copy(s3Conf = c.s3Conf.copy(awsSecretAccessKey = x)))

    version("version")
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        println("+++ config: " + config.copy(s3Conf = null))

        // for local file system. TODO for s3n
        Path.fromString(config.out).deleteRecursively(continueOnFailure = false)

        run(
          config.in,
          config.out,
          Pattern.compile(config.patentKeywordPattern),
          config.patentNumberRegex.r,
          config.s3Conf
        )

      case None => sys.exit(1)
    }
  }

  def run(
           in: String,
           out: String,
           patentKeywordPattern: Pattern,
           patentNumberRegex: Regex,
           s3Conf: SparkUtils.S3Conf) {

    EnvUtils.showJavaAndScalaProps()

    val sc = SparkUtils.initSpark(appName, s3Conf = s3Conf)
    EnvUtils.showSparkVersion(sc)

    def pageHasPatentKeyword(text: String) =
      patentKeywordPattern.matcher(text).find

    def findAllPatentNumbersIn(text: String): List[String] =
      patentNumberRegex.findAllIn(text).toList

    case class Page(url: String, patentNumbers: List[String])

    val pages: RDD[Page] = RecordLoader.loadArchives(in, sc)
      .keepValidPages()
      .map(r => (r.getUrl, RemoveHTML(r.getContentString)))
      .filter { case (url, text) => pageHasPatentKeyword(text) }
      .flatMap { case (url, text) =>
        val patentNumbers = findAllPatentNumbersIn(text)
        if (patentNumbers.nonEmpty) Some(Page(url, patentNumbers))
        else None
      }

    pages.map(p => p.url + "\t" + p.patentNumbers.mkString("\t"))
      .saveAsTextFile(out)

    sc.stop()

    println("+++ END.")
  }
}
