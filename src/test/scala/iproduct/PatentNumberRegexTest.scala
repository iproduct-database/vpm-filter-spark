package iproduct

import org.scalatest.FunSuite
import org.warcbase.spark.matchbox.RemoveHTML

import scala.util.matching.Regex


class PatentNumberRegexTest extends FunSuite {
  case class Case(input: String, expected: List[String])

  val cases = List(
    // normal
    Case("1'234'567", List("1'234'567")),
    Case("1.234.567", List("1.234.567")),
    Case("1,234,567", List("1,234,567")),
    Case("1;234;567", List()),
    Case("1234567", List("1234567")),
    Case("US1.234.567", List("US1.234.567")),
    Case("US1234567", List("US1234567")),
    Case("US 1234567", List("US 1234567")),              // we ignore register office if separated by space
    Case("D234'567", List("D234'567")),
    Case("D234567", List("D234567")),
    Case("USD234567", List("USD234567")),
    Case("1'234.567", List("1'234.567")),    // we accept a mix of separators within the same number. maybe we should restrict this.
    Case("us1234567", List()),

    // RE
    Case("RE44701", List("RE44701")),
    Case("RE44,701", List("RE44,701")),
    Case("US RE44701", List("US RE44701")),
    Case("RE44701E", List("RE44701E")),
    Case("RE44701E1", List("RE44701E1")),
    Case("RE44701A", List("RE44701A")),     // at the moment we accept this case, as we use the same suffix for all cases

    // take the US prefix in this cases: "U.S. Patent 9,027,681", "U.S. Patent Nos. 8,569,210", "U.S. Patent # 8,569,210".
    Case("U.S. 1234567B8", List("U.S. 1234567B8")),
    Case("U.S. Patent 1234567B8", List("U.S. Patent 1234567B8")),
    Case("U.S. patent 1234567B8", List("U.S. patent 1234567B8")),
    Case("U.S. Patent Nos. 1234567B8", List("U.S. Patent Nos. 1234567B8")),
    Case("U.S. Patent # 1234567B8", List("U.S. Patent # 1234567B8")),
    Case("U.S. Patent RE 44,247", List("U.S. Patent RE 44,247")),

    // EP and GB cases
    Case("EP1234567", List("EP1234567")),
    Case("GB1234567", List("GB1234567")),
    Case("1234567A4", List("1234567A4")),
    Case("1234567B8", List("1234567B8")),

    // We also accept this invalid patent number (US prefix + GB suffix)
    Case("US1234567B8", List("US1234567B8")),

    // Japan: 10, 8, 7 digits (6 not accepted for the CrawlerFilter). no digit separators ,.'. prefix: S, H.
    Case("JP2009278698A", List("JP2009278698A")),
    Case("JP60041878B2", List("JP60041878B2")),
    Case("JP4363537B1", List("JP4363537B1")),
    Case("JP106792C2", List()),              // 6 digits, valid JP, but don't accepted for the CrawlerFilter
    Case("2009278698", List("2009278698")),  // 10 digits
    Case("999999999", List()),               // 9 digits not valid JP
    Case("60041878", List("60041878")),      // 8 digits
    Case("4363537", List("4363537")),        // 7 digits, as US patents
    Case("106792", List()),                  // 6 digits, valid JP, but don't accepted for the CrawlerFilter
    Case("S60041878", List("S60041878")),
    Case("JPS60041878", List("JPS60041878")),
    Case("2.009.278.698", List()),           // no digit separators for the JP cases (except for the 7 digits, as US patents)

    // unexpected sizes
    Case("11'234'567", List()),
    Case("234'567", List()),
    Case("34'567", List()),
    Case("4'567", List()),
    Case("567", List()),
    Case("67", List()),
    Case("7", List()),
    Case("11234567", List("11234567")),  // JP
    Case("234567", List()),
    Case("34567", List()),
    Case("4567", List()),
    Case("US11234567", List()),
    Case("US234567", List()),
    Case("US34567", List()),
    Case("D11234567", List()),
    Case("D1234567", List()),
    Case("D34567", List()),

    // with letters
    Case("1.234.567A", List("1.234.567A")),
    Case("1.234.567B1", List("1.234.567B1")),
    Case("1.234.567B2", List("1.234.567B2")),
    Case("1.234.567.B1", List("1.234.567.B1")),
    Case("JP2009278698.A", List()),                // no . accepted for the JP 10 digits patent
    Case("1.234.567B3", List("1.234.567B3")),
    Case("1.234.567C", List("1.234.567C")),
    Case("1.234.567 A", List("1.234.567 A")),        // we ignore letters if separated by space
    Case("D701,125S", List("D701,125S")),          // https://www.dropps.com/pages/patents
    Case("US 7931615B2\n", List("US 7931615B2")),

    // dash prefix
    Case("#1.234.567", List("#1.234.567")),        // Patent #7,095,113  https://www.diodes.com/assets/Datasheets/ds30602.pdf

    // in text
    Case("patent number 1'234'567.", List("patent number 1'234'567")),
    Case("patent numbers 1'234'567, 2'222'333 and 3'444'555.", List("patent numbers 1'234'567", "2'222'333", "3'444'555")),
    Case("patent numbers 1'234'567; 2'222'333 and 3'444'555.", List("patent numbers 1'234'567", "2'222'333", "3'444'555")),
    Case("patents 6,233,389, 6,327,418. Other patents pending.", List("patents 6,233,389", "6,327,418")),

    // boundaries
    Case("$1'234'567'123", List()),
    Case("1'234'567'123", List()),
    Case("1'234'567.123", List()),
    Case("1'234'567. 123", List("1'234'567")),
    Case("1'234'567\n", List("1'234'567")),
    Case("\n1'234'567", List("1'234'567")),
    Case("8028357;295166;8321970", List()),         // do we want to handle this case? http://watcomfg.com/watco/patents/Default.html
    Case("US Patent No.9480555", List("US Patent No.9480555")),           // https://www.alcon.com/content/alcon-patent-list
    Case("\t1'234'567", List("1'234'567")),

    // html
    Case("<p>\n\tU.S. 7,658,040</p>", List("U.S. 7,658,040")),              // tags
    Case("9,004,524,&nbsp;9,050,987", List("9,004,524", "9,050,987")), // space encoded

    // real examples
    // https://www.tivo.com/legal/patents
    Case("TiVo BOLT and BOLT+ U.S. Pat. Nos. D435,561, D445,801, 6,233,389, 6,327,418, 9,113,219. Other patents pending.", List("U.S. Pat. Nos. D435,561", "D445,801", "6,233,389", "6,327,418", "9,113,219")),
    // http://www.ipc.com/patents
 //   Case("U.S. Patent Nos.: 7,904,056; 8,767,942\n", List("7,904,056", "8,767,942")),
    Case("U.S. Patent Nos.: 7,904,056; 8,767,942", List("7,904,056", "8,767,942")),
    // http://www.abaxis.com/about_us/patents/vetscan-profiles
    Case("This product is covered under issued US Patent Nos. 7,998,411 and all related non-US patents", List("US Patent Nos. 7,998,411")),
    // https://www.actifio.com/patents/
    Case("US Patent Numbers: 8,299,944; 8,396,905; 9,251,198;9,501,548;9,495,435. Other patents pending.", List("8,299,944", "8,396,905")),  // we do not accept this case
    // https://cnd.com/patents
    Case("Protected by U.S. Patents 6,391,938, 6,599,958 and 6,803,394. Additional patents...", List("U.S. Patents 6,391,938", "6,599,958", "6,803,394")),
    // http://www.novonordisk-us.com/patients/products/product-patents.html
    Case("Radiopharmaceutical containers\n7,165,672\n7,495,246\n7,692,173", List("7,165,672", "7,495,246", "7,692,173")),
    // http://www.rapiscansystems.com/en/company/virtual_patent_marking
    Case("U.S. Patent Numbers:6,839,403; 7,505,557; 8,213,570", List("6,839,403", "7,505,557", "8,213,570")),
    // https://www.smarttech.com/patents
    Case("patents:\nUS6320597, US6326954 and USD636183.\nOther patents pending.", List("US6320597", "US6326954", "USD636183")),
    // http://www.rmspumptools.com/innovation.php
    Case("Shuttle Switch (MWR) - US Patent No: US 7,650,942,B2", List("US 7,650,942,B2"))
  )

  val r: Regex = CrawlerFilterConfig.patentNumberRegex.r

  cases.foreach { c =>
    test(s"Test $c") {
      val output = r.findAllIn(CrawlerFilterConfig.cleanSpaces(RemoveHTML(c.input))).toList
//      val output = r.findAllIn(BuildPages.cleanSpaces(c.input)).toList
      assert(output === c.expected)
    }
  }
}
