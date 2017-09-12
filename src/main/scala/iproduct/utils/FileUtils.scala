package iproduct.utils

import java.io.File
import java.nio.file.{Files, Path, Paths}

//import java.nio.file.StandardOpenOption._
//import java.nio.file.{Files, Paths}

//import akka.util.ByteString
import scala.collection.JavaConversions._

object FileUtils {
  def readFile(file: String): String =
    scala.io.Source.fromFile(file).mkString

  def writeToFile(file: File, content: String): Unit = {
    val out = new java.io.PrintWriter(file)
    try out.write(content) finally out.close()
  }

  def writeToFile(file: String, content: String) {
    writeToFile(new File(file), content)
  }

  def writeToFile(path: Path, content: String): Unit = {
    writeToFile(path.toFile, content)
  }

//  def writeToFile(file: String, content: ByteString) {
//    val out = Files.newByteChannel(Paths.get(file), CREATE, WRITE)
//    try out.write(content.toByteBuffer) finally out.close()
//  }

//  def readFileAsByteArray(file: String): Array[Byte] =
//    Files.readAllBytes(Paths.get(file))

  def writeToFile(file: String, content: Array[Byte]) {
    Files.write(Paths.get(file), content)
  }

  def listFiles(path: Path): List[Path] =
    Files.list(path).toArray.map(_.asInstanceOf[Path]).toList

  def isPDF(content: Array[Byte]): Boolean =
    content.startsWith("%PDF")

  def path(path: String): Path =
    Paths.get(path)

  def dir(path: Path): Iterator[Path] =
    Files.newDirectoryStream(path).iterator.toIterator

  implicit class PathStringInterpolation(val sc: StringContext) extends AnyVal {
    def p(args: Any*): Path =
      FileUtils.path(sc.raw(args:_*))

    def d(args: Any*): Iterator[Path] =
      FileUtils.dir(FileUtils.path(sc.raw(args:_*)))
  }
}
