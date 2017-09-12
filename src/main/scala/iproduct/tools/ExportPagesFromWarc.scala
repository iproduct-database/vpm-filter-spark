package iproduct.tools

import java.nio.file.{Files, Path, Paths}
import java.util

import iproduct.utils.{FileUtils, HttpUtils, Utils, WarcReader}
import org.archive.io.warc.{WARCReader, WARCReaderFactory}

import scala.collection.JavaConversions._

object ExportPagesFromWarc extends App {
  val appName = "ExportPagesFromWarc"

  case class Config(
                     warc: String = "",
                     sampleRate: Int = 1,
                     outDir: String = "")

  val parser = new scopt.OptionParser[Config](appName) {
    override def showUsageOnError = true

    head("Export a file for each page in the Web Archive (WARC).")

    opt[String]("warc").required.action((x, c) =>
      c.copy(warc = x)).text("input warc.gz (file or folder)")

    opt[Int]("sampleRate").action((x, c) =>
      c.copy(sampleRate = x))
      .text("takes a page only every x pages. If sampleRate = 1, it takes all files. If sampleRate = 100, it takes only one page every 100 pages.")

    opt[String]("outDir").required.action((x, c) =>
      c.copy(outDir = x)).text("output dir")

    help("help").text("prints this usage text")
  }

  parser.parse(args, Config()) match {
    case Some(config) =>
      run(
        warc = Paths.get(config.warc),
        sampleRate = config.sampleRate,
        outDir = Paths.get(config.outDir)
      )

    case None => sys.exit(1)
  }

  def run(warc: Path, sampleRate: Int, outDir: Path) {
    Files.createDirectories(outDir)

    var i = 0

    def runSample(op: => Unit) { if (i <= 0) { i = sampleRate - 1; op } else { i = i - 1 } }

    val warcFiles: util.Iterator[Path] =
      if (Files.isDirectory(warc))
        Files.newDirectoryStream(warc).iterator
      else
        List(warc).iterator

    def export(r: WarcReader.Response) {
      print("+++ exporting: " + r.url)
      val f = outDir.resolve(HttpUtils.urlToFilename(r.url, FileUtils.isPDF(r.content)))
      println(" to " + f)
      Files.write(f, r.content)
    }

    warcFiles.foreach { warcFile =>
      println(s"+++ warcfile: $warcFile")
      val reader: WARCReader = WARCReaderFactory.get(warcFile.toFile)
      WarcReader.getResponses(reader).foreach(r => Utils.tryAndContinue(runSample(export(r))))
    }

    println("+++ END.")
  }
}