package iproduct.utils

import java.util.regex.Pattern

import com.google.common.net.InternetDomainName

import scala.util.Try

object URLUtils {
  def getDomainFromUrl(url: String): Try[String] =
    Try {
      new java.net.URL(url).getHost
    }

  def getTopDomainFromUrl(url: String): Try[String] =
    Try {
      new java.net.URL(url).getHost
    }
      .flatMap(getTopDomainFromDomain)

  def getTopDomainFromDomain(domain: String): Try[String] = Try {
    InternetDomainName.from(domain).topPrivateDomain.name
  }

  def normalizeUrl(url: String): Try[String] =
    Try { new java.net.URL(url) } map { u => u.getProtocol + "://" + u.getAuthority + u.getFile }


  def discardedDomains(discardedDomainsFile: Option[String]): Set[String] = {
    val discardedDomains: Set[String] =
      discardedDomainsFile.map { f => val lines = scala.io.Source.fromFile(f).getLines.toSet; lines.map(_.trim) }.getOrElse(Set.empty)
    println(s"+++ discardedDomains: $discardedDomains")
    discardedDomains
  }

  def urlIsInDomains(domains: Set[Pattern])(url: String): Boolean =
    getDomainFromUrl(url).map(d => domains.exists(dd => dd.matcher(d).matches)).getOrElse(false)
}
