package iproduct.utils

import edu.stanford.nlp.ling.CoreAnnotations.{OriginalTextAnnotation, PartOfSpeechAnnotation, TokensAnnotation}
import edu.stanford.nlp.ling.{CoreAnnotations, CoreLabel}
import edu.stanford.nlp.pipeline.{Annotation, StanfordCoreNLP}
import edu.stanford.nlp.util.logging.RedwoodConfiguration
import edu.stanford.nlp.util.{CoreMap, PropertiesUtils}

import scala.collection.JavaConversions._

object NLPUtils {
  def tokenize(text: String): List[CoreLabel] = {
    RedwoodConfiguration.current.clear.apply()
    val pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties(
      "annotators", "tokenize, ssplit, pos",
      "ssplit.isOneSentence", "false",
      "tokenize.language", "en"))

    val document = new Annotation(text)

    pipeline.annotate(document)

    def getTokenFromSentence(sentence: CoreMap): List[CoreLabel] =
      sentence.get(classOf[TokensAnnotation]).toList

    document.get(classOf[CoreAnnotations.SentencesAnnotation]).toList.flatMap(getTokenFromSentence)
  }

  // todo: Other/JJ, chars/NNS, :/:, #/#, $/$, "/'', (/-LRB-, )/-RRB-, ,/,, ./., :/:, '/'', `/``, €/$, !/., ?/., </JJR, >/JJR, +/CC, */SYM, %/NN, //:)
  // https://nlp.stanford.edu/software/tagger.shtml
  // http://www.comp.leeds.ac.uk/amalgam/tagsets/upenn.html
  def isPunctuation(token: CoreLabel): Boolean =
    !token.get(classOf[PartOfSpeechAnnotation]).charAt(0).isLetter
}


object NLPUtilsExample extends App {
  val text = """This page is intended to serve as notice under 35 U.S.C. § 287(a).
               |Retail Products
               |TiVo Roamio U.S. Pat. Nos. D435,561, D445,801, 6,327,418, 6,385,739, 8,457,476, 8,464,309,8,577,205. Other patents pending.
               |""".stripMargin

  def tokenToString(token: CoreLabel): String =
    token.get(classOf[OriginalTextAnnotation]) + "/" + token.get(classOf[PartOfSpeechAnnotation]) + "/" + NLPUtils.isPunctuation(token)

  Utils.printList("list", NLPUtils.tokenize(text).map(tokenToString))

}
