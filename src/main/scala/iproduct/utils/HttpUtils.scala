package iproduct.utils

import java.util.regex.Pattern

import org.apache.commons.lang3.StringUtils

import scalaj.http._

object HttpUtils {
  def download(url: String): Array[Byte] = {
    val response = Http(url).option(HttpOptions.followRedirects(true)).asBytes

    if (response.code != 200)
      throw new Exception(response.statusLine)

    response.body
  }

  def ext(isPDF: Boolean): String = if (isPDF) "pdf" else "html"

  def urlToFilename(url: String, isPDF: Boolean): String =
    url.replaceAll("§", "§§").replaceAll("\\\\", "§\\\\").replaceAll("/", "\\\\") + "." + ext(isPDF)

  val extPattern: Pattern = Pattern.compile("(\\.pdf|\\.html)$")

  def filenameToUrl(filename: String): String = {
    if (!extPattern.matcher(filename).find) throw new IllegalArgumentException("expected a .html or .pdf filename")
    val f = extPattern.matcher(filename).replaceAll("")
    StringUtils.replaceEach(f, Array("§§", "§\\", "\\"), Array("§", "\\", "/"))
  }
}
