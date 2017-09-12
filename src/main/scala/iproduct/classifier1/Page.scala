package iproduct.classifier1

case class Match(text: String, context: String)

// todo: add "patent" and "legal" to additionalRegexs, and add Page.distinctWordsInUrl
@SerialVersionUID(393188064L)
case class Page(
                 url: String,
                 domain: String,
                 patentNumbers: List[Match],
                 additionalTextMatches: Map[String, List[Match]],
                 additionalUrlMatches: Map[String, List[Match]],
                 numWords: Int,
                 distinctWords: Set[String],
                 patentSequences: Seq[Sequence],
                 longestPatentSequenceSize: Int
               ) extends Serializable

@SerialVersionUID(393188065L)
case class Sequence(start: Int, end: Int) { val length: Int = end - start + 1 }

@SerialVersionUID(393188066L)
object EmptyPage extends Page(
  url = "",
  domain = "",
  patentNumbers = List(),
  additionalTextMatches = Map.empty,
  additionalUrlMatches = Map.empty,
  numWords = 0,
  distinctWords = Set(),
  patentSequences = Seq.empty,
  longestPatentSequenceSize = 0
)
