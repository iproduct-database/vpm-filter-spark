package playground.inferGrammar.utils

import java.io.{File, StringWriter}
import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{OutputKeys, Transformer, TransformerFactory}
import javax.xml.xpath.{XPath, XPathConstants, XPathFactory}

import org.jsoup.Jsoup
import org.jsoup.helper.{W3CDom => JSoupW3CDom}
import org.jsoup.nodes.{Document => JSoupDocument}
import org.w3c.dom.{Document, Element, Node, NodeList}

import scala.xml.parsing.NoBindingFactoryAdapter
import scala.xml.{Node => ScalaNode}

object XMLUtils {
  val factory: DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance()

  val builder: DocumentBuilder =
    factory.newDocumentBuilder()

  val xPathfactory: XPathFactory = XPathFactory.newInstance
  val xpathEv: XPath = xPathfactory.newXPath()

  def parseXml(xml: String): Element = {
    val source = new org.xml.sax.InputSource()
    source.setCharacterStream(new java.io.StringReader(xml))
    builder.parse(source).getDocumentElement
  }

  lazy val w3cDom: JSoupW3CDom = new JSoupW3CDom()

  def xmlFromHtml(content: String): ScalaNode =
    xmlFromJsoupDoc(Jsoup.parse(content))

  def xmlFromHtml(inputFile: File): ScalaNode =
    xmlFromJsoupDoc(Jsoup.parse(inputFile, "UTF-8"))

  def domFromHtml(content: String): Document =
    w3cDom.fromJsoup(Jsoup.parse(content))

  def xmlFromJsoupDoc(doc: JSoupDocument): ScalaNode =
    asXml(w3cDom.fromJsoup(doc))

  def asXml(dom: Node): ScalaNode = {
    val source = new DOMSource(dom)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
    }

  def path(element: Element): String = {
    def sameName(f: Node => Node)(n: Node) =
      Stream.iterate(n)(f).tail.takeWhile(_ != null).filter(
        _.getNodeName == n.getNodeName
      ).toList

    val preceding = sameName(_.getPreviousSibling) _
    val following = sameName(_.getNextSibling) _
    "/" + Stream.iterate[Node](element)(_.getParentNode).map {
      case _: Document => None
      case e: Element => Some {
        (preceding(e), following(e)) match {
          case (Nil, Nil) => e.getTagName
          case (els, _) => e.getTagName + "[" + (els.size + 1) + "]"
        }
      }
    }.takeWhile(_.isDefined).map(_.get).reverse.mkString("/")
  }

  def elementFromXpath(doc: Node, p: String): Element = {
    val expr = xpathEv.compile(p)
    val nl = expr.evaluate(doc, XPathConstants.NODESET).asInstanceOf[NodeList]
    require(nl.getLength == 1)
    try {
      nl.item(0).asInstanceOf[Element]
    } catch {
      case e: Exception => throw new Exception(p, e)
    }
  }

  val prettyTransformer: Transformer =
    TransformerFactory.newInstance.newTransformer
  prettyTransformer.setOutputProperty(OutputKeys.INDENT, "yes")
  prettyTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

  val transformer: Transformer =
    TransformerFactory.newInstance.newTransformer
  transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

  def print(transformer: Transformer, doc: Node): String = {
    val result = new StreamResult(new StringWriter())
    val source = new DOMSource(doc)
    transformer.transform(source, result)
    result.getWriter.toString
  }

  def prettyPrint(doc: Node): String =
    print(prettyTransformer, doc)

  def printInOneLine(doc: Node): String =
    print(transformer, doc).replaceAll("\n", " \\\\n ")

  def printInOneLine(doc: Node, prefix: String): String =
    print(transformer, doc).split("\n").map(l => prefix + l).mkString("\n")

  def print(doc: Node): String =
    print(transformer, doc)

  implicit class RichNodeList[A](nodeList: NodeList) {
    def asScala: Traversable[Node] = {
      new Traversable[Node] {
        def foreach[A](process: (Node) => A) {
          for (index <- 0 until nodeList.getLength) {
            process(nodeList.item(index))
          }
        }
      }
    }
  }

}

object XMLUtilsTest extends App {
  val root: Element = XMLUtils.parseXml("<html><body><p>p111</p><p>p222</p></body></html>")
  val p = root.getFirstChild.getFirstChild.getNextSibling.asInstanceOf[Element]
  println(root.getTagName)
  println(p.getTagName)
  println(XMLUtils.path(p))
  println(XMLUtils.prettyPrint(root))
}
