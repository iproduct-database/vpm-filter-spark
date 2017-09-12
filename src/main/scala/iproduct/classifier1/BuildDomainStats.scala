package iproduct.classifier1

import anorm._

import scala.collection.immutable.Seq

object BuildDomainStats extends App {
  val dbUrl = args(0)
  Class.forName("com.mysql.jdbc.Driver").newInstance()
  implicit val conn = java.sql.DriverManager.getConnection(dbUrl)

  println("create domainStats table")
  SQL"create table domainStats(domain varchar(1000), numDistinctMatchedPatents int, numDistinctHanNames int, shareTop1 double, shareTop2 double, shareTop3 double, shareTop4 double, shareTop5 double, hanNamesHerfindhalIndex double)".executeUpdate()

  val query =
    """
      |select
      |  A.domain as domain,
      |  B.HAN_NAME as hanName,
      |  count(distinct(A.patentNumber)) as numDistinctMatchedPatents
      |from
      |  pagePatents A
      |  inner join patstat_us_one_author_per_patent B ON (A.patentNumber = B.PUBLN_NR)
      |group by A.domain, B.HAN_NAME
      |order by A.domain, numDistinctMatchedPatents desc;
    """.stripMargin

  case class DomainHanNames(domain: String, hanName: String, numDistinctMatchedPatents: Int)
  val parser = Macro.namedParser[DomainHanNames]
  println("sql query")
  val list: List[DomainHanNames] = SQL(query).as(parser.*)


  def computeShare(nTop: Int, numPatents: Seq[Int]): Double = {
    numPatents.take(nTop).sum.toDouble / numPatents.sum
  }

  def computeHanNamesHerfindhalIndex(numPatents: Seq[Int]): Double = {
    val totalPatents = numPatents.sum
    numPatents.map(n => Math.pow(n.toDouble/totalPatents, 2)).sum
  }

  case class DomainStats(domain: String, numDistinctMatchedPatents: Int, numDistinctHanNames: Int, shareTop: List[Double], hanNamesHerfindhalIndex: Double)
  println("compute results")
  val results = list.groupBy(_.domain).map { case (domain: String, n: Seq[DomainHanNames]) =>
    val numDistinctMatchedPatents = n.map(_.numDistinctMatchedPatents)
    val numTotalDistinctMatchedPatents = numDistinctMatchedPatents.sum
    val numDistinctHanNames = n.size
    val shareTop = (1 to 5).toList.map(i => computeShare(i, numDistinctMatchedPatents))
    DomainStats(domain, numTotalDistinctMatchedPatents, numDistinctHanNames, shareTop, computeHanNamesHerfindhalIndex(numDistinctMatchedPatents))
  }

  println("insert results to db")
  results.foreach { r => import r._
    SQL"insert into domainStats VALUES($domain, $numDistinctMatchedPatents, $numDistinctHanNames, $shareTop, $hanNamesHerfindhalIndex)".executeUpdate()
  }

  println("compute numDistinctPatents, distinctMatchedPatentsRatio")
  SQL"alter table domainStats add numDistinctPatents int after numDistinctMatchedPatents, add distinctMatchedPatentsRatio double after numDistinctPatents".executeUpdate()
  SQL"update domainStats A left join domains B on (A.domain=B.domain) set A.numDistinctPatents = B.numDistinctPatents, A.distinctMatchedPatentsRatio = A.numDistinctMatchedPatents / B.numDistinctPatents".executeUpdate()
}
