package iproduct.utils

import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.JavaConverters._

object EnvUtils {
  def getProperty(name: String): Option[String] =
    Option(System.getProperty(name)).orElse(Option(System.getenv(name)))

  def showJavaAndScalaProps() {
    val javaVersion = System.getProperty("java.version")
    println(s"javaVersion: $javaVersion")

    val scalaVersion = scala.util.Properties.versionString
    println(s"scalaVersion: $scalaVersion")

    println("java properties:")
    val properties = System.getProperties.asScala.toList.sortBy(_._1)
    for ((key, value) <- properties) println(s"  $key: $value")
  }

  def showSparkVersion(sc: SparkContext) {
    println(s"sparkVersion: ${sc.version}")
  }
}

object EnvCheckApp extends App {
  EnvUtils.showJavaAndScalaProps()

  val sparkConf = new SparkConf().setAppName("EnvCheckApp").setMaster("local[1]")
  val sc: SparkContext = new SparkContext(sparkConf)
  EnvUtils.showSparkVersion(sc)
  sc.stop()
}
