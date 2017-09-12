package iproduct.classifier1

import java.sql.Connection

import anorm._
import iproduct.utils.DatabaseUtils
import iproduct.utils.Utils.using

case class UrlRawLabels(url: String, pageType: String, patentNumbersInPage: Boolean, truePatentNumbers: Option[Boolean], pageAboutPatents: Boolean)

// "runMain application.ImportUrlRawLabelsToMysql /IPRoduct/results/classifier_urlLabels.tsv $dbUrl UrlRawLabels"
object ImportUrlRawLabelsToMysql {
  def main(args: Array[String]) {
    val urlLabelsFile = args(0)
    val dbUrl = args(1)
    val tableName = args(2)

    using(DatabaseUtils.getDbConnection(dbUrl)) { implicit conn =>
      createTable(tableName)
      val list = readUrlRawLabels(urlLabelsFile)

      def insertToDb(urlRawLabels: UrlRawLabels)(implicit conn: Connection) {
        import urlRawLabels._
        SQL"insert into #$tableName (url, pageType, patentNumbersInPage, truePatentNumbers, pageAboutPatents) VALUES($url, $pageType, $patentNumbersInPage, $truePatentNumbers, $pageAboutPatents)".executeUpdate()
      }

      list.foreach(insertToDb)
    }
  }

  def createTable(tableName: String)(implicit conn: Connection) {
    SQL"drop table if exists #$tableName".executeUpdate()
    val validPageTypeMaxLength = validPageTypes.map(_.length).max
    SQL"""
      create table #$tableName (
      url varchar(10000) not null, pageType char(#$validPageTypeMaxLength) not null, patentNumbersInPage boolean not null, truePatentNumbers boolean, pageAboutPatents boolean not null,
      index(url), index(pageType), index(patentNumbersInPage), index(truePatentNumbers), index(pageAboutPatents))
      """.executeUpdate()
  }

  val validPageTypes = List("svpm", "cvpm", "hvpm", "ovpm", "patents", "news", "other")

  def readUrlRawLabels(urlLabelsFile: String): List[UrlRawLabels] = {
    def stringOption(s: String): Option[String] = if (s.isEmpty) None else Some(s)

    val toBoolean = Map("1" -> true, "0" -> false)

    def parseLine(line: String): Option[UrlRawLabels] = {
      line.split("\t") match {
        case Array(_, pageType, patentNumbersInPage, truePatentNumbers, pageAboutPatentes, _, url, _*)
          if validPageTypes.contains(pageType) && patentNumbersInPage.nonEmpty && pageAboutPatentes.nonEmpty && url.nonEmpty
        => Some(UrlRawLabels(url, pageType, toBoolean(patentNumbersInPage), stringOption(truePatentNumbers).map(toBoolean), toBoolean(pageAboutPatentes)))
        case _ => println("ignored: " + line); None
      }
    }

    scala.io.Source.fromFile(urlLabelsFile).getLines.flatMap(parseLine).toList
  }
}
