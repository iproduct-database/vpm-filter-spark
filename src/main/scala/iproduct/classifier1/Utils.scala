package iproduct.classifier1

import java.sql.Connection

import anorm._
import iproduct.CrawlerFilterConfig
import play.api.libs.json._

object Utils {
  implicit val sequenceFormats: OFormat[Sequence] = Json.format[Sequence]
  implicit val matchFormats: OFormat[Match] = Json.format[Match]
  implicit val pageFormats: OFormat[Page] = Json.format[Page]

  case class DBOut(
                    dbUrl: String,
                    createTables: Boolean = true,
                    pagesTable: String = "pages",
                    pagePatentsTable: String = "pagePatents",
                    pageRegexsTable: String = "pageRegexs"
                  )

  def createTables()(implicit conn: Connection, dbOut: DBOut): Unit = {
    val cmds = List(
      s"drop table if exists ${dbOut.pagesTable}",
      s"drop table if exists ${dbOut.pagePatentsTable}",
      s"drop table if exists ${dbOut.pageRegexsTable}",
      s"create table ${dbOut.pagesTable} (id int not null auto_increment primary key, url varchar(10000), domain varchar(1000), numPatents int, patentNumbersJson longtext, patentNumbersText longtext, additionalTextMatchesJson longtext, additionalUrlMatchesJson longtext, numWords int, numDistinctWords int, distinctWordsText longtext, patentSequencesJson longtext, longestPatentSequenceSize int)",
      s"create table ${dbOut.pagePatentsTable} (id int not null auto_increment primary key, pageId int, url varchar(10000), domain varchar(1000), explicitUs boolean, patentNumber varchar(30), patentNumberFull varchar(30))",
      s"create table ${dbOut.pageRegexsTable} (id int not null auto_increment primary key, pageId int, url varchar(10000), domain varchar(1000), field varchar(100), regex varchar(1000), numMatches int)"
    )
    cmds.foreach(c => SQL(c).executeUpdate())
  }

  def importToMysql(page: Page)(implicit conn: Connection, dbOut: DBOut): Unit = {
    import page._

    def seqText[T](list: Traversable[T]) = " " + list.mkString(" ") + " "
    val patentNumbersText = seqText(patentNumbers.map(_.text))
    def json1(value: List[Match]) = Json.stringify(Json.toJson(value))
    def json2(value: Map[String, List[Match]]) = Json.stringify(Json.toJson(value))
    def json3(value: Seq[Sequence]) = Json.stringify(Json.toJson(value))

    val pageIdOpt: Option[Long] =
      SQL"""
      insert into #${dbOut.pagesTable}
        (url, domain, numPatents, patentNumbersJson, patentNumbersText, additionalTextMatchesJson, additionalUrlMatchesJson, numWords, numDistinctWords, distinctWordsText, patentSequencesJson, longestPatentSequenceSize)
        values (${page.url.take(10000)}, $domain, ${patentNumbers.size}, ${json1(patentNumbers)}, $patentNumbersText, ${json2(additionalTextMatches)}, ${json2(additionalUrlMatches)}, $numWords, ${distinctWords.size}, ${seqText(distinctWords)}, ${json3(patentSequences)}, $longestPatentSequenceSize)
      """.executeInsert()

    for {pageId <- pageIdOpt; patentNumberFull <- patentNumbers.map(_.text) } {
      val explicitUs = false
      val patentNumber = CrawlerFilterConfig.normalizePatentNumber(patentNumberFull)
      SQL"""
      insert into #${dbOut.pagePatentsTable} (pageId, url, domain, explicitUs, patentNumber, patentNumberFull) values($pageId, $url, $domain, $explicitUs, $patentNumber, $patentNumberFull)
        """.executeInsert()
    }

    for {pageId <- pageIdOpt} {
      def importAdditionalMatches(field: String, additionalMatches: Map[String, List[Match]]) = {
        for {m: (String, List[Match]) <- additionalMatches}
          SQL"""insert into #${dbOut.pageRegexsTable} (pageId, url, domain, field, regex, numMatches) values($pageId, $url, $domain, $field, ${m._1}, ${m._2.size})""".executeInsert()
      }
      importAdditionalMatches("text", additionalTextMatches)
      importAdditionalMatches("url", additionalUrlMatches)
    }
  }

//  val r: Regex = "(US)?(.*)".r

//  def explicitUs(us: String): String =
//    if ("US".equalsIgnoreCase(us)) "1"
//    else if (us == null) "0"
//    else throw new Exception("unexpected patent auth: " + us)

//  def normalizePatent(text: String): (String, String) = text match {
//    case r(us, number) => (explicitUs(us), number.replaceAll("[^0-9]", ""))
//    case _ => throw new IllegalArgumentException(s"unexpected patent number: $text")
//  }

//  def additionalRegexsMatches(map: Map[String, List[Match]]): List[Int] = {
//    require(map.size == additionalRegexs.size)
//    additionalRegexs.map(r => map(r).size)
//  }

  def getNewContext(initialContextLength: Int, newContextLength: Int)(context: String): String = {
    require(newContextLength <= initialContextLength)
    val diff = initialContextLength - newContextLength
    //    context.substring(diff, context.length - diff)
    context.substring(diff, (context.length - diff).max(diff)) // temporal workaround
  }

  def printPatterns[T](p: Map[String, List[String]]) {
    p.toList
      .sortBy(-_._2.size)
      .foreach { case (pattern, items) => println(s"${items.size}\t${patternToString(pattern)}\t${patternsToString(items.take(10))}") }
  }

  def patternsToString(list: List[String]): String =
    list.map(patternToString).mkString("\t")

  def patternToString(s: String): String =
    "[" + s + "]"
}
