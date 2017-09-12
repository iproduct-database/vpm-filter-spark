package iproduct.utils

import java.io.Serializable

import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.{LabeledPoint, LinearRegressionModel}
import org.apache.spark.mllib.tree.configuration.FeatureType.{Categorical, Continuous}
import org.apache.spark.mllib.tree.model.{DecisionTreeModel, Node, Split}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.immutable.Seq
import scala.util.Random
import Features._

object SparkUtils {
  def initSpark(app: String = "app", master: Option[String] = None, s3Conf: S3Conf = S3Conf()): SparkContext = {
    val sparkConf = new SparkConf().setAppName(app)
    master.foreach(v => sparkConf.setMaster(v))
    val sc = new SparkContext(sparkConf)
    setupS3(sc, s3Conf)
    sc
  }

  def initLocalSpark(app: String = "app"): SparkContext =
    initSpark(app, Some("local[*]"))

  case class S3Conf(awsAccessKeyId: String = "", awsSecretAccessKey: String = "")

  def setupS3(sc: SparkContext, s3Conf: S3Conf) {
    sc.hadoopConfiguration.set("fs.s3.impl", "org.apache.hadoop.fs.s3.S3FileSystem")
    sc.hadoopConfiguration.set("fs.s3n.impl", "org.apache.hadoop.fs.s3native.NativeS3FileSystem")
    sc.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", s3Conf.awsAccessKeyId)
    sc.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey", s3Conf.awsSecretAccessKey)
  }

  def balancedDataset(dataset: Seq[LabeledPoint]): List[LabeledPoint] = {
    println(s"balancedDataset. dataset.length = ${dataset.length}")
    println("+++ label counts: " + dataset.groupBy(_.label).mapValues(_.size))
    val grouped = dataset.groupBy(_.label)
    val min = grouped.map(_._2.size).min

    val newList = Random.shuffle(grouped.mapValues(list => list.take(min)).values.flatten).toList
    println("+++ label counts: " + newList.groupBy(_.label).mapValues(_.size))
    newList
  }

  def printFeatures(features: List[Feature]) {
    println("+++ features")
    println(features)
    features.zipWithIndex.foreach { case (feature, index) => println(s"$index\t$feature") }
  }

  def explainDecisionTreeModel(model: DecisionTreeModel, features: List[Feature]) {
    println(s"+++ Learned decision tree model:\n" + model.toDebugString)

    def visit(node: Node, parents: List[(Boolean, Node)]): List[List[(Boolean, Node)]] =
      if (node.isLeaf)
        if (node.predict.predict == 1.0) List(parents) else List.empty
      else
        visit(node.leftNode.get, (true, node) :: parents) ::: visit(node.rightNode.get, (false, node) :: parents)

    val positiveBranches: List[List[(Boolean, Node)]] =
      visit(model.topNode, List.empty)

    def explainPositiveBranch(fe: List[Feature.FilterExp])(parents: List[(Boolean, Node)]) = {
      def splitToString(split: Split, left: Boolean): String =
        split.featureType match {
          case Continuous => fe(split.feature).apply(left, split.threshold)
          case Categorical => throw new IllegalArgumentException("only double values supported")
        }

      parents.reverse.map { case (left, node) => splitToString(node.split.get, left) }.mkString(" and ")
    }

    val textFe = features.map(f => f.simpleFilterExp _)
    val textFilter = positiveBranches.map(explainPositiveBranch(textFe)).mkString("\n")
    println("\n+++ filter: \n" + textFilter)

    val sqlFe = features.map(f => f.sqlFilterExp _)
    val sqlFilter = positiveBranches.map(explainPositiveBranch(sqlFe)).map(t => s"($t)").mkString(" or \n")
    val sqlQuery = s"select url from ClassifierDataset where \n$sqlFilter \norder by url;"
    println("\n+++ sql query: \n" + sqlQuery)
  }

  def explainRegressionModel(model: LinearRegressionModel, features: List[Feature]) {
    println(s"+++ Learned regression model:\n" + model.toPMML)

    println("+++ weights:")
    val pairs = model.weights.toArray.zip(features).sortBy(p => -Math.abs(p._1))

    pairs.foreach { case (weight, feature) => println(s"${feature.name}\t$weight") }

    val sqlSVPMValue =
      pairs
        .filter { case (weight, feature) => weight != 0.0 }
        .map { case (weight, feature) => s"${feature.sqlField} * $weight" }
        .mkString(" + ")
    val sqlQuery = s"select (\n  $sqlSVPMValue\n) as sqlSVPMValue, url \nfrom ClassifierDataset \norder by sqlSVPMValue desc;"
    println("\n+++ sql query: \n" + sqlQuery)
  }

  val booleanToDouble: Map[Boolean, Double] = Map(true -> 1.0, false -> 0.0)
  val doubleToBoolean: Map[Double, Boolean] = booleanToDouble.map(_.swap)
  val booleanToSignString = Map(false -> "!", true -> "")
  def toBinaryDouble(value: Double): Double = if (value < 0.5) 0.0 else 1.0

  // this does not work: Map[Int, Int]().withDefaultValue(2)
  class MapWithFixValue[T](length: Int, value: T) extends Map[Int, T] with Serializable {
    override def +[B1 >: T](kv: (Int, B1)): Map[Int, B1] = throw new IllegalArgumentException("call not expected")
    override def -(key: Int): Map[Int, T] = throw new IllegalArgumentException("call not expected")
    override def get(key: Int): Option[T] = Some(value)
    override def iterator: Iterator[(Int, T)] = List.tabulate(length)(i => (i, value)).iterator
  }

  def predict(model: DecisionTreeModel, features: Vector): Node = {
    def predict_(node: Node): Node = {
      if (node.isLeaf) {
        node
      } else {
        if (node.split.get.featureType == Continuous) {
          if (features(node.split.get.feature) <= node.split.get.threshold) {
            predict_(node.leftNode.get)
          } else {
            predict_(node.rightNode.get)
          }
        } else {
          if (node.split.get.categories.contains(features(node.split.get.feature))) {
            predict_(node.leftNode.get)
          } else {
            predict_(node.rightNode.get)
          }
        }
      }
    }
    predict_(model.topNode)
  }

  def nodes(node: Node): List[Node] =
    if (node.isLeaf)
      List(node)
    else
      node :: nodes(node.leftNode.get) ::: nodes(node.rightNode.get)
}

object Features {
  trait Feature {
    def name: String

    def sqlField: String = name

    def simpleFilterExp(isLessOrEqual: Boolean, value: Double): String = sqlFilterExp(isLessOrEqual, value)

    def sqlFilterExp(isLessOrEqual: Boolean, value: Double): String
  }

  object Feature {
    type FilterExp = (Boolean, Double) => String
  }

  case class PositiveIntFeature(name: String) extends Feature {
    override def sqlFilterExp(isLessOrEqual: Boolean, value: Double): String =
      sqlField + " " +
        (if (isLessOrEqual && value == 0.0) "= 0"
        else if (isLessOrEqual) "<= " + value
        else "> " + value)
  }

  case class RatioFeature(name: String) extends Feature {
    override def sqlFilterExp(isLessOrEqual: Boolean, value: Double): String = {
      require(value >= 0.0 && value <= 1.0)
      require(!(!isLessOrEqual && value == 0.0)) // useless filter
      require(!(isLessOrEqual && value == 1.0)) // useless filter
      sqlField + " " +
        (if (isLessOrEqual && value == 0.0) "= 0.0"
        else if (isLessOrEqual) "<= " + value
        else if (!isLessOrEqual && value == 1.0) "= 1.0"
        else "> " + value)
    }
  }

  trait BooleanFeature extends Feature {
    def isTrue(isLessOrEqual: Boolean, value: Double): Boolean =
      if (isLessOrEqual && value == 0.0) false
      else if (!isLessOrEqual && value == 0.0) true
      else throw new Exception(s"unexpected: isLessOrEqual=$isLessOrEqual, value=$value")

    val toInt = Map(true -> "1", false -> "0")

    override def sqlFilterExp(isLessOrEqual: Boolean, value: Double): String =
      sqlField + " = " + toInt(isTrue(isLessOrEqual, value))
  }
}