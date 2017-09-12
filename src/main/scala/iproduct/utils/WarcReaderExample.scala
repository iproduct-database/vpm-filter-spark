package iproduct.utils

import org.archive.io.warc._
import org.archive.wayback.core.CaptureSearchResult

object WarcReaderExample {
  def main(args: Array[String]) {
    testWarcReader()
    testCDXIndex()
  }

  def testWarcReader() {
    println("TEST1. START.")

    def info(r: WarcReader.Response) {
      println(r.statusCode + ": " + r.url)
      println(new String(r.content).replaceAll("[\n\r]", "-"))
    }

    val reader = WARCReaderFactory.get("/david/Desktop/files.warc.gz")
    WarcReader.getResponses(reader).foreach(info)
    reader.close()

    println("TEST1. END.")
  }

  def testCDXIndex() {
    println("TEST2. START.")

    val index = new CDXIndex("/david/Desktop/files.warc.cdx", "/david/Desktop")

    def info(r: CaptureSearchResult): Unit = {
      val response = index.getContent(r)
      println(s"${r.getFile}:${r.getOffset}: ${r.getOriginalUrl}, status: ${response.statusCode}")
      println(new String(response.content).replaceAll("[\n\r]", "-"))
    }

    println("+++ filterByUrl")
    index.filterByUrl("http://www.rmspumptools.com/innovation.php").foreach(info)

    println("+++ filterByPrefix")
    index.filterByPrefix("http://www.rmspumptools.com/").foreach(info)

    println("TEST2. END.")
  }
}
