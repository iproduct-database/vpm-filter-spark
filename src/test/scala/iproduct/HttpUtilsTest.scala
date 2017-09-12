package iproduct

import utils.HttpUtils
import org.scalatest.FunSuite


class HttpUtilsTest extends FunSuite {
  test("urlToFilename and filenameToUrl") {
    val input = "http://www.mounts.com/results?match=NEC%20M40&test1=§&test2=\\&test3=\""
    val expected = "http:\\\\www.mounts.com\\results?match=NEC%20M40&test1=§§&test2=§\\&test3=\".pdf"
    val output = HttpUtils.urlToFilename(input, isPDF = true)
    val restored = HttpUtils.filenameToUrl(output)

    println("   input: " + input)
    println("restored: " + restored)
    println("expected: " + expected)
    println("  output: " + output)

    assert(output === expected)
    assert(restored === input)
  }
}
