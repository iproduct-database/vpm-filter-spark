package iproduct

import scala.util.matching.Regex

object CrawlerFilterConfig {
  // EN, FR, DE, ES, CAT, IT, PT, NL, CN simplified, CN traditonal, KR, JP
  val patentKeywordPattern = "(?i)(patent|brevet|patent|patente|patent|brevetto|patente|octrooi|专利|專利|특허|特許)"

  val us = "U\\.?S\\.?"
  val kind = "[A-Z]\\d?"
  val patentNoText = "((?i)(Patents?|Pat.?) )?((?i)(Numbers?|Nos?\\.?) ?)?"
  val n1 = s"(($us|EP|GB|JP) ?)?$patentNoText((D ?(# ?)?)|((# ?)?\\d[,.']?))\\d\\d\\d[,.']?\\d\\d\\d([,.' ]?$kind)?"
  val n2 = s"($us ?)?${patentNoText}RE ?\\d\\d[,.']?\\d\\d\\d([,.' ]?$kind)?"
  val n3 = s"(JP ?)?$patentNoText([HS] ?)?(\\d{7,8}|\\d{10})( ?$kind)?"
  val patentNumberRegex = s"(?<![^ :])(($n1)|($n2)|($n3))(?=[,.';:()]?(?![^ ]))"

  val cleanSpacesRegex: Regex = "[\\h\\s\\v]+".r
  def cleanSpaces(text: String): String =
    cleanSpacesRegex.replaceAllIn(text, " ")

  val discardedDomainsFile = "./discardedDomains.txt"

  def normalizePatentNumber(patent: String): String =
    patent.replaceAll("[ .,'#]", "").replaceAll("(?i)(Patents?|Pat)?", "").replaceAll("(?i)(Numbers?|Nos?)", "")
}
