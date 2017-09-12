package iproduct.utils

import java.io._
import java.nio.file.Path

import org.apache.commons.io.IOUtils
import org.archive.io.ArchiveRecord
import org.archive.io.warc.{WARCReader, WARCReaderFactory, WARCRecord}
import org.archive.wayback.core.CaptureSearchResult
import org.archive.wayback.resourceindex.cdx.CDXFormatIndex
import org.archive.wayback.resourcestore.resourcefile.WarcResource
import org.archive.wayback.util.url.AggressiveUrlCanonicalizer

import scala.collection.JavaConverters._

/*
  val reader: WARCReader = WARCReaderFactory.get("/david/Desktop/files.warc.gz")
  WarcReader.getResponses(reader)
*/
object WarcReader {
  def getResponsesFromWarcFiles(warcFiles: Iterator[Path], printWarc: Boolean = true, printUrl: Boolean = true): Iterator[Response] = {
    warcFiles.flatMap { warcFile =>
      if (printWarc)
        println(s"+++ warcfile: $warcFile")
      val reader: WARCReader = WARCReaderFactory.get(warcFile.toFile)
      WarcReader.getResponses(reader, printUrl)
    }
  }

  def getResponses(reader: WARCReader, printUrl: Boolean = true): Iterator[Response] = {
    reader.iterator.asScala
      .filter(_.getHeader.getHeaderValue("WARC-Type") == "response")
      .map { r: ArchiveRecord =>
        val res = buildResponse(r.asInstanceOf[WARCRecord], reader)
        if (printUrl) println(s"+++ url: ${res.url}")
        res
      }
  }

  case class Response(url: String, statusCode: Int, content: Array[Byte])

  def buildResponse(warcRecord: WARCRecord, reader: WARCReader): Response = {
    val warcResource = new WarcResource(warcRecord, reader)
    warcResource.parseHeaders()
    val url = warcResource.getWarcHeaders.getUrl
    val statusCode = warcResource.getStatusCode
    val content = IOUtils.toByteArray(warcResource)
    Response(url, statusCode, content)
  }

  def getContent(r: CaptureSearchResult): Response =
    getContent(r.getFile, r.getOffset)

  def getContent(file: String, offset: Long): Response =
    getContent(new File(file), offset)

  def getContent(file: File, offset: Long): Response = {
    val reader = WARCReaderFactory.get(file)
    try {
      val warcRecord = reader.get(offset).asInstanceOf[WARCRecord]
      buildResponse(warcRecord, reader)
    } finally reader.close()
  }
}

class CDXIndex(val cdxFile: String, basePath: String) {
  def this(cdxFile: String) = this(cdxFile, new File(cdxFile).getParent)

  val basePath_ = new File(basePath)

  val canonicalizer = new AggressiveUrlCanonicalizer()
  def canonicalize(url: String): String =
    canonicalizer.urlStringToKey(url)

  private def getIndex = {
    val index: CDXFormatIndex = new CDXFormatIndex()
    index.setPath(cdxFile)
    index
  }

  def all: Iterator[WarcReader.Response] =
    getIndex.getPrefixIterator("").asScala.filter(_.isHttpSuccess).map(getContent)
  // filter(_.getUrlKey != "-")  // todo: fix this - entry in the index

  def filterByPrefix(url: String): Iterator[CaptureSearchResult] = {
    val key = canonicalize(url)
    getIndex.getPrefixIterator(key).asScala.filter(_.isHttpSuccess).takeWhile(_.getUrlKey.startsWith(key))
  }

  def filterByUrl(url: String): Iterator[CaptureSearchResult] = {
    val key = canonicalize(url)
    getIndex.getUrlIterator(key).asScala.filter(_.isHttpSuccess).takeWhile(key == _.getUrlKey)
  }

  def filterByUrl2(url: String): Iterator[WarcReader.Response] = {
    val key = canonicalize(url)
    getIndex.getUrlIterator(key).asScala.filter(_.isHttpSuccess).takeWhile(key == _.getUrlKey).map(getContent)
  }

  def isHttpSuccess(statusCode: Int): Boolean =
    statusCode >= 200 && statusCode < 300

  def firstByUrl(url: String): Array[Byte] =
    filterByUrl2(url).filter(r => isHttpSuccess(r.statusCode)).next.content

  def getContent(r: CaptureSearchResult): WarcReader.Response =
    WarcReader.getContent(new File(basePath_, r.getFile), r.getOffset)
}
