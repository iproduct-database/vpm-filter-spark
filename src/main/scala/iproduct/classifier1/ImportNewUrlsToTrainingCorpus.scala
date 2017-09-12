package iproduct.classifier1

import java.sql.Connection

import anorm._
import iproduct.utils.{DatabaseUtils, URLUtils}

import scala.util.{Success, Try}

object ImportNewUrlsToTrainingCorpus extends App {
  val proposedUrlsFile = args(0)

  def discardInvalid[T](list: Set[Try[T]]): Set[T] = {
    println("discarded: " + list.filter(_.isFailure))
    list.collect{case Success(x) => x}
  }

  def getUrlsInDB(implicit conn: Connection): Set[String] = {
    println("+++ getUrlsInDB. START.")
    val list = SQL"select url from UrlRawLabels order by url".as(SqlParser.str(1).*)
    val dups = list.diff(list.distinct).toSet
    println("+++ duplicated urls in db:")
    dups.toList.sorted.foreach(println)

    val l2 = list.distinct
    val l3 = l2.map(URLUtils.normalizeUrl(_).get)
    println(s"+++ urls in db not normalized:")
    l2.zip(l3).foreach { case (u, n) =>
        if (u != n) println(s"$u\t$n")
    }

    println("+++ getUrlsInDB. END.")
    l3.toSet
  }

  println("+++ proposedUrls")
  val proposedUrls = discardInvalid(scala.io.Source.fromFile(proposedUrlsFile).getLines.toSet.map(URLUtils.normalizeUrl))
  proposedUrls.toList.sorted.foreach(println)

  implicit val conn = DatabaseUtils.getDbConnection
  val urlsInDB = getUrlsInDB

  println("+++ already in DB")
  proposedUrls.intersect(urlsInDB).toList.sorted.foreach(println)
  // todo: check they are normalized

  val newUrls = proposedUrls -- urlsInDB
  println("+++ newUrls")
  newUrls.toList.sorted.foreach(println)

  println("+++ proposedDomains")
  val proposedDomains: Map[String, Set[String]] = newUrls.groupBy(URLUtils.getTopDomainFromUrl(_).get)
  proposedDomains.keys.toList.sorted.foreach(println)

//  println("+++ domains in DB")
  val domainsInDB: Map[String, Set[String]] = urlsInDB.groupBy(URLUtils.getTopDomainFromUrl(_).get)
//  domainsInDB.keys.toList.sorted.foreach(println)

  println("+++ proposed domains already in DB")
  proposedDomains.keys.toSet.intersect(domainsInDB.keys.toSet).toList.sorted.foreach(println)

  println("+++ new domains")
  (proposedDomains.keys.toSet -- domainsInDB.keys.toSet).toList.sorted.foreach(println)


  println("+++ proposed domains already in DB")
  proposedDomains.keys.toSet.intersect(domainsInDB.keys.toSet).toList.sorted.foreach { domain =>
    val proposed = proposedDomains(domain)
    val inDb = domainsInDB(domain)
    println(s"+++ $domain urls in both")
    proposed.intersect(inDb).toList.sorted.foreach(println)

    println(s"+++ $domain urls only in db")
    (inDb -- proposed).toList.sorted.foreach(println)

    println(s"+++ $domain urls only in proposed")
    (proposed -- inDb).toList.sorted.foreach(println)
  }

  println("+++ new domains")
  (proposedDomains.keys.toSet -- domainsInDB.keys.toSet).toList.sorted.foreach { domain =>
    println(domain)
    proposedDomains(domain).foreach(url => println(s"  $url"))
  }

}
