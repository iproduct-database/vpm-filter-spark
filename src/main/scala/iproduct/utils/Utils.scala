package iproduct.utils

import java.io.File
import java.sql.ResultSet

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Try}

object Utils {
  def parseCSVFile(file: String): List[List[String]] = {
    import java.nio.charset.StandardCharsets

    import org.apache.commons.csv._

    import collection.JavaConverters._

    val csvFormat2 = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces(true)
    CSVParser
      .parse(new File(file), StandardCharsets.UTF_8, csvFormat2)
      .getRecords.toArray(Array[CSVRecord]()).toList
      .map(_.iterator.asScala.toList)
  }

  def tlog[T](o: T): T = {
    pprint.pprintln(o, width = 200)
    o
  }

  def using[X <: {def close()}, A](resource : X)(f : X => A): A = {
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }

  def printList[T](title: String, list: Traversable[T]) {
    println(s"$title, count: ${list.size}")
    list.foreach(println)
    println()
  }

  def count[A](list: Traversable[A]): Map[A, Int] =
    list.groupBy(identity).mapValues(_.size)

  def countSortAndPrint[A](title: String, list: Traversable[A], countOfCount: Boolean = false) {
    val size = list.size
    val l = count(list).toList.sortBy(- _._2)
    println(s"$title, count: ${size}, count unique: ${l.size}")
    l.foreach(p => println(p._2 + "\t"  + p._1))
    println()

    if (countOfCount) {
      println(s"count of count")
      val counts = count(l.map(_._2.toLong)).toList.sortBy(- _._1)
      counts.foreach(p => println(s"${p._2}\t${p._1}\t${100 * p._1 * p._2 / size}%"))
      println()
    }
  }

  def runWithTimeout[T](timeOutSeconds: Long)(f: => T)(implicit ec: ExecutionContext): T = {
    import scala.concurrent.duration._
    Await.result(Future { f }, timeOutSeconds seconds)
  }

  def tryAndContinue[T](f: => T) {
    try { f } catch {
      case t: Exception => t.printStackTrace()
    }
  }

  def tryAndPrint[T](f: => T): Try[T] = {
    Try(f).recoverWith { case t: Throwable => t.printStackTrace(); Failure(t)}
  }

  implicit class RichIterator[A](it: Iterator[A]) {
    def foreachTryAndContinue[U](f: A => U) { it.foreach(e => tryAndContinue(f(e))) }
    def mapTryAndIgnore[B](f: A => B): Iterator[B] = it.flatMap(e => tryAndPrint(f(e)).toOption)
  }

  def resultSetItr(resultSet: ResultSet): Iterator[ResultSet] = {
    new Iterator[ResultSet] {
      def hasNext: Boolean = resultSet.next()
      def next(): ResultSet = resultSet
    }
  }

  def getCdx: CDXIndex = {
    val cdxFile = System.getenv("CDXFILE")
    require(cdxFile != null)
    new CDXIndex(cdxFile, new File(cdxFile).getParent)
  }

  def loadFileFromArchive(url: String): Array[Byte] =
    getCdx.firstByUrl(url)

  def loadPageFromArchive(url: String): String =
    new String(loadFileFromArchive(url))
}
