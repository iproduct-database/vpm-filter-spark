package iproduct.utils

import java.io._
import java.util
import java.util.concurrent.atomic.AtomicInteger

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.archive.format.warc.WARCConstants.WARCRecordType
import org.archive.io.warc.{WARCRecordInfo, WARCWriter, WARCWriterPoolSettings}
import org.archive.uid.UUIDGenerator
import org.archive.util.DateUtils
import org.warcbase.spark.archive.io.ArchiveRecord
import org.warcbase.spark.matchbox.RecordLoader

object FilterArchive {
  def run(inputWarcFile: String, outDir: String, filter: ArchiveRecord => Boolean, sc: SparkContext) {
    val outDir_ = new File(outDir)
    outDir_.mkdirs()

    def safeFilter(ar: ArchiveRecord): Boolean =
      try filter(ar)
      catch {
        case t: Throwable =>
          println(s"+++ cannot process ${ar.getUrl} " + t.toString)
          t.printStackTrace()
          false
      }

    val webPages: RDD[ArchiveRecord] = RecordLoader.loadArchives(inputWarcFile, sc)

    webPages.foreachPartition(it => {
      val file = java.io.File.createTempFile("files_", ".warc.gz", outDir_)
      val (writer, uuidGenerator) = initWriter(file)
      it.filter(safeFilter).foreach { archiveRecord =>
//        println(s"$file\t${archiveRecord.getUrl}")
        val warcResponse = buildWARCResponse(archiveRecord.getUrl, archiveRecord.getContentBytes, uuidGenerator)
        writer.writeRecord(warcResponse)
      }
      writer.close()
    })

    sc.stop()
    println("END")
  }

  private def initWriter(file: File): (WARCWriter, UUIDGenerator) = {
    val bos = new BufferedOutputStream(new FileOutputStream(file))
    val settings = getSettings(isCompressed = true, prefix = null, arcDirs = null, metadata = null)
    val writer = new WARCWriter(new AtomicInteger(), bos, file, settings)
    (writer, settings.getRecordIDGenerator)
  }

  def getSettings(isCompressed: Boolean, prefix: String, arcDirs: util.List[File], metadata: util.List[String]) =
    new WARCWriterPoolSettings() {
      def calcOutputDirs: util.List[File] = arcDirs

      def getMetadata: util.List[String] = metadata

      def getPrefix: String = prefix

      def getCompress: Boolean = isCompressed

      def getMaxFileSizeBytes = org.archive.format.arc.ARCConstants.DEFAULT_MAX_ARC_FILE_SIZE

      def getTemplate = "${prefix}-${timestamp17}-${serialno}"

      def getFrequentFlushes = false

      def getWriteBufferSize = 4096

      def getRecordIDGenerator = new UUIDGenerator()
    }

  def buildWARCResponse(url: String, content: Array[Byte], uuidGenerator: UUIDGenerator): WARCRecordInfo = {
    val r = new WARCRecordInfo()
    r.setType(WARCRecordType.response)
    r.setUrl(url)
    r.setMimetype("application/http; msgtype=response")
    r.setRecordId(uuidGenerator.getRecordID)
    r.setContentStream(new ByteArrayInputStream(content))
    r.setContentLength(content.length)
    // NB: extract data from ArchiveRecord if needed
    r.setCreate14DigitDate(DateUtils.getLog14Date())  // NB: create14DigitDate must be in ISOZ format (name "14DigitDate" is confusing)
    r
  }
}
