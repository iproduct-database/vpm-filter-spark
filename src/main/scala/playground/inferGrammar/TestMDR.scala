package playground.inferGrammar

// testing https://github.com/scrapinghub/mdr

import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory}

import iproduct.CrawlerFilterConfig
import iproduct.utils.WarcReader.Response
import iproduct.utils.{CDXIndex, Utils}
import jep.Jep
import org.w3c.dom.{Element, Node, Text}
import playground.inferGrammar.utils.XMLUtils
import playground.inferGrammar.utils.XMLUtils._

import scala.collection.JavaConverters._

object MDR {
  case class SeedRecord(element: Element) {
    override def toString: String = "SeedRecord[" + printInOneLine(element) + "]"
  }
  case class Record(nodes: List[Element]) {
    override def toString: String = "Record[" + nodes.map(printInOneLine).mkString("\n") + "]"
  }
  case class RecordMapping(record: Record, mapping: Map[Element, Element]) {
    override def toString: String = s"RecordMapping($record,Map(" + mapping.map { case (s,t) => printInOneLine(s) + " -> " + printInOneLine(t)}.mkString(", ") + "))"
  }
  case class Result(root: Element, seedRecord: SeedRecord, recordMappings: List[RecordMapping])

  def someXmlTextInResultSatisfies(r: Result, fn: (String) => Boolean): Boolean = {
    //r.recordMappings.exists(recordMapping => recordMapping.mapping.values.exists(e => fn(e.getTextContent)))
    fn(r.root.getTextContent)
  }

  def hasOnlyTextNodes(e: Element): Boolean =
    e.getChildNodes.asScala.forall(_.getNodeType == Node.TEXT_NODE)

  // it assumes hasOnlyTextNodes(e) is true
  def getTextFromElementWithOnlyTextNodes(e: Element): String = {
    e.getChildNodes.asScala.map(_.getTextContent).mkString(" ").trim
  }

  def extractDataFromRecordMapping(recordMapping: RecordMapping): Map[String, String] =
    recordMapping.mapping.flatMap { case (s, t) =>
      if (!hasOnlyTextNodes(s) || !hasOnlyTextNodes(t)) None
      else {
        val text = getTextFromElementWithOnlyTextNodes(t)
        if (text.isEmpty) None
        else Some(path(s) -> text)
      }
    }

  def extractDataFromResult(r: Result): List[Map[String, String]] =
    r.recordMappings.map(extractDataFromRecordMapping)

  def cleanText(text: String): String =
    CrawlerFilterConfig.cleanSpaces(text)

  def cleanData(data: List[Map[String, String]]): List[Map[String, String]] =
    data.map(_.mapValues(cleanText))

  def printData(data: Map[String, String]): Unit = {
    println("+++ record")
    data.toList.sortBy(_._1).foreach { case (key, value) =>
        println(s"$key\t${cleanText(value)}")
    }
    println()
  }

  def printData(data: List[Map[String, String]]) {
    // Utils.printList("data", MDR.cleanData(data))
    println("+++ records: " + data.length)
    data.foreach(printData)
  }

  def findStructure(e: Element): List[String] = {
    if (hasOnlyTextNodes(e)) List(path(e))
    else
      e.getChildNodes.asScala.toList.collect{case e: Element => e}.flatMap(findStructure)
  }

  def findStructure(seedRecord: SeedRecord): List[String] =
    findStructure(seedRecord.element)

  def findStructure(data: List[Map[String, String]]): List[String] =
    data.flatMap(_.keys).distinct.sorted


  case class MData(var value: Option[String], children: collection.mutable.Map[String, MData]) {
    override def toString: String = toString("")
    def toString(indent: String): String = {
      children.toList.sortBy(_._1).map { case (path, data) =>
          indent + path + data.value.map(v => ": " + cleanText(v)).getOrElse("") + "\n" + data.toString(indent + "  ")
      }.mkString("")
    }
  }

  def buildNestData(data: Map[String, String]): MData = {
    val rootData = MData(None, collection.mutable.Map.empty)

    def getLocalPaths(path: String) = {
      require(path(0) == '/')
      val pathI = path.drop(1).replaceAll("\\[(\\d)\\]", "/[00$1]").replaceAll("\\[(\\d\\d)\\]", "/[0$1]").replaceAll("\\[(\\d+)\\]", "/[$1]")
      // assert(!pathI.matches(".*[\\[\\]].*"))
      pathI.split("/").toList
    }

    def add(path: String, value: String) {
      def add(nestedData: MData, localPaths: List[String]) {
        localPaths match {
          case Nil =>
            require(nestedData.value.isEmpty)
            nestedData.value = Some(value)
          case head :: tail =>
            if (!nestedData.children.isDefinedAt(head))
              nestedData.children.put(head, MData(None, collection.mutable.Map.empty))
            add(nestedData.children(head), tail)
        }
      }

      add(rootData, getLocalPaths(path))
    }

    data.foreach { case (path, value) => add(path, value) }

    rootData
  }

  def compactSegment(nestedPath: String, data: MData): (String, MData) = {
    if (data.value.isDefined || data.children.size != 1)
      (nestedPath, data)
    else
      compactSegment(nestedPath + "/" + data.children.head._1, data.children.head._2)
  }

  def compactNestData(data: MData): MData = {
    MData(data.value, data.children.map { case (path, nestedData) =>
      val (newPath, newNestedData) = compactSegment(path, nestedData)
      (newPath, compactNestData(newNestedData))
    })
  }

  // case class Structure(path: String, children: List[Structure])
  // def findStructure2(data: List[Map[String, String]]): List[String] = ???

  val dbf: DocumentBuilderFactory = DocumentBuilderFactory.newInstance
  val builder: DocumentBuilder = dbf.newDocumentBuilder

  def embedTextNodes(html: String): String =
    print(embedTextNodes(XMLUtils.domFromHtml(html).getDocumentElement))

  def embedTextNodes(n: Element): Element = {
    val doc = builder.newDocument

    def embed(src: Node, dst: Node): Node = src match {
      case e: Element =>
        val ne = doc.createElement(e.getTagName)
        dst.appendChild(ne)
        e.getChildNodes.asScala.foreach { c =>
          ne.appendChild(embed(c, ne))
        }
        ne
      case t: Node =>
        val ne = doc.createElement("text")
        dst.appendChild(ne)
        ne.appendChild(dst.getOwnerDocument.createTextNode(t.getTextContent))
        ne
    }

    embed(n, doc)
    doc.getDocumentElement
  }

  def listCandidates(doc: Element, fn: (String) => Boolean = (_) => true): List[Element] = {
    val nodes = traverseNonEmptyTextNodesRecursively(doc).filter(n => fn(n.getTextContent)).toList
    // simplified xpath -> list of xpaths
    val d: Map[String, List[String]] = nodes.map(n => path(n.getParentNode.asInstanceOf[Element])).groupBy(simplifyXPath)

    val ancestorXPaths: List[String] = d.values.toList.map(ancestorXPath)
    Utils.count(ancestorXPaths).toList.sortBy(- _._2).map{case (xpath, count) => elementFromXpath(doc, xpath)}.filter(_.getChildNodes.getLength >= 2)
  }

  def traverseNodesRecursively(node: Node): Stream[Node] =
    Stream(node).append(node.getChildNodes.asScala.toStream.flatMap(traverseNodesRecursively))

  def traverseNonEmptyTextNodesRecursively(node: Node): Stream[Node] =
    traverseNodesRecursively(node).filter(n => n.isInstanceOf[Text] && cleanText(n.getTextContent).nonEmpty)

  def simplifyXPath(xpath: String): String =
    xpath.replaceAll("\\[\\d+\\]", "")

  def commonPrefixFromPair[A](s: List[A], t: List[A]): List[A] =
    s.zip(t).takeWhile(Function.tupled(_ == _)).map(_._1)

  def commonPrefixFromList[A](list: List[List[A]]): List[A] =
    list.tail.foldLeft(list.head)(commonPrefixFromPair)

  def ancestorXPath(xpaths: List[String]): String =
    commonPrefixFromList(xpaths.map(_.split("/").toList)).mkString("/")

  val pythonPath = "src/main/python"
  val jep = new Jep(false, pythonPath)
  //List("from mdr import MDR", "from lxml import etree", "mdr = MDR()").foreach(jep.eval)
  jep.runScript(s"$pythonPath/utils.py")
}

class MDR(text: String, doEmbedTextNodes: Boolean = true) {
  import MDR._
  jep.set("text", if (doEmbedTextNodes) embedTextNodes(text) else text)
  jep.eval("doc = mdr.parseHtml(text)")
  jep.eval("docText = etree.tostring(doc, pretty_print=False, encoding=\"unicode\", doctype=\"\")")
  val doc: Element = parseXml(jep.getValue("docText").asInstanceOf[String])

  //  def listCandidates(): List[Element] = {
  //    jep.eval("candidates = mdr.list_candidates(doc)")
  //    val paths = jep.getValue("[doc.getpath(c) for c in candidates]").asInstanceOf[java.util.ArrayList[String]].asScala.toList
  //    paths.map(p => elementFromXpath(doc, p))
  //  }

  def extractResultsFromRoot(root: Element): Option[Result] = {
    jep.set("rootPath", path(root))
    jep.eval("seedRecord, recordMappingDict = mdrExtract(doc, rootPath)")

    val seedRecordString = jep.getValue("seedRecord").asInstanceOf[String]
    if (seedRecordString == null) return None

    val seedRecord = SeedRecord(parseXml(seedRecordString))

    val recordMappingDictWithXPaths: List[(List[String], Map[String, String])] =
      jep.getValue("recordMappingDict").asInstanceOf[java.util.ArrayList[java.util.Collection[_]]].asScala.toList
        .map { recordMapping => val m = recordMapping.asScala.toList
          (m(0).asInstanceOf[java.util.ArrayList[String]].asScala.toList,
            m(1).asInstanceOf[java.util.Map[String, String]].asScala.toMap)

        }

    val recordMappings: List[RecordMapping] = recordMappingDictWithXPaths.map(recordMapping =>
      RecordMapping(
        record = Record(recordMapping._1.map(path => elementFromXpath(doc, path))),
        mapping = recordMapping._2.map { case (s, t) =>
          elementFromXpath(seedRecord.element, s) -> elementFromXpath(doc, t)
        }
      )
    )

    Some(Result(root, seedRecord, recordMappings))
  }
}

/*
test:
http://www.yelp.co.uk/biz/the-ledbury-london
http://www.muellerwaterproducts.com/patents
http://www.ignitepatents.com/
*/
object TestMDR extends App {
  val url = args(0)
  // val text = utils.FileUtils.readFile("./data/the-ledbury-london.html")
  // val text = utils.FileUtils.readFile("./data/data.html")
  val text = Utils.loadPageFromArchive(url)

  val mdr = new MDR(text, doEmbedTextNodes = true)

  lazy val r = CrawlerFilterConfig.patentNumberRegex.r
  def textContainsPatent(text: String): Boolean = r.findFirstIn(text).isDefined

  val candidates: List[Element] = MDR.listCandidates(mdr.doc, textContainsPatent)
  // val candidates: List[Element] = MDR.listCandidates(mdr.doc)

  println("candidates: " + candidates.map(path))

  def test(root: Element) {
    println("+++ test root: " + path(root))

    try {
      val optResult = mdr.extractResultsFromRoot(root)
      // println(optResult)
      optResult.foreach { result =>
        val interesting = MDR.someXmlTextInResultSatisfies(result, textContainsPatent)
        println(s"interesting: $interesting")

        // Utils.printList("structure seedRecord", MDR.findStructure(result.seedRecord))

        val data: List[Map[String, String]] = MDR.extractDataFromResult(result)
        // MDR.printData(data)

        // Utils.printList("structure data", MDR.findStructure(data))

        val nestedData = data.map(MDR.buildNestData)
        // Utils.printList("nestedData", nestedData)

        val compactNestedData = nestedData.map(MDR.compactNestData)
        Utils.printList("compactNestedData", compactNestedData)
      }
    } catch {
      case e: Exception => e.printStackTrace()
    }
    println()
  }

  candidates.foreach(test)

  println("+++ END")
}

object TestMDR2 extends App {
  val r = CrawlerFilterConfig.patentNumberRegex.r
  def textContainsPatent(text: String): Boolean = r.findFirstIn(text).isDefined

  case class Result(html: String, root: String, numRecords: Int, numRecordsWithPatents: Int, exampleRecord: MDR.MData, exception: Option[Exception]) {
    override def toString: String =
      s"Result:\t$numRecords\t$numRecordsWithPatents\t$html\t$root\t${exception.getOrElse("")}\t${MDR.cleanText(String.valueOf(exampleRecord))}"
  }

  def test(url:String, html: String, filterFn: (String) => Boolean, doEmbedTextNodes: Boolean): List[Result] = {
    val mdr = new MDR(html, doEmbedTextNodes)
    val roots: List[Element] = MDR.listCandidates(mdr.doc, filterFn)

    def test(root: Element): Result = {
      println("+++ test root: " + path(root))
      try {
        val optResult = mdr.extractResultsFromRoot(root)
        optResult.map { result =>
          //val interesting = MDR.someXmlTextInResultSatisfies(result, filterFn)
          val data: List[Map[String, String]] = MDR.extractDataFromResult(result)
          val nestedData: List[MDR.MData] = data.map(MDR.buildNestData)
          val compactNestedData: List[MDR.MData] = nestedData.map(MDR.compactNestData)
          Utils.printList("compactNestedData", compactNestedData)
          println()

          val numRecordsWithPatents = data.map(_.values.exists(filterFn)).count(_ == true)
          val example = compactNestedData.take(3).last
          Result(url, path(root), result.recordMappings.length, numRecordsWithPatents, example, None)
        }.getOrElse(Result(url, path(root), 0, 0, MDR.MData(None, collection.mutable.Map.empty), None))
      } catch {
        case e: Exception => e.printStackTrace(); Result(url, null, 0, 0, null, Some(e))
      }
    }

    roots.take(3).map(test)
  }

  def test(r: Response): List[Result] = {
    println("+++ URL: " + r.statusCode + ": " + r.url )
    try {
      test(r.url, new String(r.content), textContainsPatent, doEmbedTextNodes = true)
      // for some reason, after throwing a java.util.concurrent.TimeoutException, all following JEP calls get a jep.JepException: Invalid thread access.
      // Utils.runWithTimeout(180){ test(r.url, r.contentString, textContainsPatent, doEmbedTextNodes = true) }
    } catch {
      case e: Exception => e.printStackTrace(); List(Result(r.url, null, 0, 0, null, Some(e)))
    }
  }

  val index = new CDXIndex("/david/Desktop/files.warc.cdx", "/david/Desktop")
  index.filterByUrl2("https://www.tivo.com/legal/patents").flatMap(test).foreach(println)
  index.filterByUrl2("http://www.rmspumptools.com/innovation.php").flatMap(test).foreach(println)
  val ignoreUrls = List("-", "http://www.musictrades.com/database.html?azColSort=databaseId&az_numPerPage=&az_descendingSort=1", "http://www.davisnet.com/legal/")
  index.all.filter(r => !ignoreUrls.contains(r.url)).flatMap(test).foreach(println)

  // val reader = WARCReaderFactory.get("/david/Desktop/files.warc.gz")
  // WarcReader.getResponses(reader).foreach(test)
  // reader.close()
}


/*
candidates: List(/html/body/table[2]/tbody)
+++ test root: /html/body/table[2]/tbody
interesting: true
compactNestedData, count: 3
seed/tr/td/
  [001]/text: p1
  [002]/text: patents: 7,351,506

seed/tr/td/
  [001]/text: p2
  [002]/text: patents: 7,351,507; 7,351,509

seed/tr/td/
  [001]/text: p3
  [002]/text: patents: 7,351,501


+++ END
*/
