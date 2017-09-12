package iproduct.classifier1

import java.sql.{Connection, ResultSet}

import iproduct.utils.{DatabaseUtils, SparkUtils}
import iproduct.utils.SparkUtils._
import iproduct.utils.Features._
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.{DenseVector, Vector}
import org.apache.spark.mllib.regression.{LabeledPoint, LinearRegressionModel, LinearRegressionWithSGD, RegressionModel}
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.model.DecisionTreeModel

import scala.collection.immutable.Seq
import scala.util.Try

object TrainClassifierAndPredict {
  def main(args: Array[String]): Unit = {
    val dbUrl = args(0)
    val predictOutputFile = args(1)

    SparkUtils.printFeatures(features)

    implicit val conn: Connection = DatabaseUtils.getDbConnection(dbUrl)
    val sc = SparkUtils.initSpark()

    val trainingData: Seq[LabeledPoint] = buildTrainingCorpus()
    trainingData.foreach(println)
    val data: Seq[LabeledPoint] = balancedDataset(trainingData)

    val modelR: LinearRegressionModel = buildRegressionModel(data, sc)
    explainRegressionModel(modelR, features)
    predictR(modelR, predictOutputFile + "_1")

    val modelD: DecisionTreeModel = buildDecissionTreeModel(data, sc)
    explainDecisionTreeModel(modelD, features)
    predictD(modelD, predictOutputFile + "_2")

    sc.stop()
    conn.close()
  }

  def buildDecissionTreeModel(trainingData: Seq[LabeledPoint], sc: SparkContext): DecisionTreeModel = {
    val impurity = "gini"
    val maxDepth = 5
    val maxBins = 32
    val categoricalFeaturesInfo: Map[Int, Int] = Map[Int, Int]()
    DecisionTree.trainClassifier(sc.parallelize(trainingData), 2, categoricalFeaturesInfo, impurity, maxDepth, maxBins)
  }

  def buildRegressionModel(trainingData: Seq[LabeledPoint], sc: SparkContext): LinearRegressionModel = {
    LinearRegressionWithSGD.train(sc.parallelize(trainingData), numIterations = 100, stepSize = 0.00000001)
  }

  def predictD(model: DecisionTreeModel, predictOutputFile: String)(implicit conn: Connection) {
    predict((features: Vector) => model.predict(features), predictOutputFile)
  }

  def predictR(model: RegressionModel, predictOutputFile: String)(implicit conn: Connection) {
    predict((features: Vector) => model.predict(features), predictOutputFile)
  }

  def predict(predict: Function1[Vector, Double], predictOutputFile: String)(implicit conn: Connection) {
    println(s"+++ predict and save to $predictOutputFile")

    val out = new java.io.PrintWriter(new java.io.File(predictOutputFile))

    val sqlQuery =
      """
        |select * from
        |pages t1
        |left join domains t2 on (t1.domain = t2.domain)
        |left join domainStats t3 on (t1.domain = t3.domain)
        |where hasPatentKeyword = true
      """.stripMargin

    val stmt = conn.createStatement()
    stmt.setFetchSize(Integer.MIN_VALUE)

    val entries = iproduct.utils.Utils.resultSetItr(stmt.executeQuery(sqlQuery))
    entries.foreach { rs =>
      val features = exportFeatures(rs)
      val prediction = predict(features)
      out.println(prediction + "\t" + rs.getString("domain") + "\t" + rs.getString("url"))
    }

    out.close()
    stmt.close()
  }

  def buildTrainingCorpus()(implicit conn: Connection): Seq[LabeledPoint] = {
    println("+++ buildTrainingCorpus")

    val sqlQuery =
      """
        |select * from
        |UrlRawLabels t0
        |inner join pages t1 on (t0.url = t1.url)
        |inner join domains t2 on (t1.domain = t2.domain)
        |inner join domainStats t3 on (t1.domain = t3.domain)
      """.stripMargin

    val stmt = conn.createStatement()
    stmt.setFetchSize(Integer.MIN_VALUE)

    val entries = iproduct.utils.Utils.resultSetItr(stmt.executeQuery(sqlQuery))
    val data = entries.map { rs =>
      val a = LabeledPoint(getLabel(rs), exportFeatures(rs))
      println(a)
      a
    }.toList

    stmt.close()
    println("+++ num features: " + data(0).features.size)
    data
  }

  def getLabel(rs: ResultSet): Double = {
    val pageType = rs.getString("pageType")
    val svpmValues = Set("svpm", "cvpm", "ovpm", "hvpm")
    if (svpmValues.contains(pageType)) 1.0 else 0.0
  }

  val words = List("patent", "patents", "patented", "product", "products", "license", "licenses", "licensed", "register", "registered", "process", "processes", "protect", "protected", "pending", "application", "applications", "cover", "covered", "activity", "activities", "issue", "issues", "issued", "method", "methods", "published", "legal", "blog", "blogs", "tag", "tags", "news", "thread", "threads", "archive", "archives", "press", "publication", "publications", "transfer", "resume", "curriculum", "lawsuit", "lawsuits", "dispute", "pledge", "infringement", "search", "google", "job", "jobs")

  val features: List[Feature] = {
    (1 to 4).map(n => s"text_regex_$n:${BuildPages.additionalTextRegexs(n-1)}").toList.map(PositiveIntFeature) :::
      (1 to 4).map(n => s"url_regex_$n:${BuildPages.additionalUrlRegexs(n-1)}").toList.map(PositiveIntFeature) :::
      List("numWords", "numDistinctWords", "longestPatentSequenceSize").map(PositiveIntFeature) :::
      List("numPages", "numDistinctMatchedPatents", "numDistinctHanNames").map(PositiveIntFeature) :::
      (1 to 5).map(n => s"shareTop$n").toList.map(RatioFeature) :::
      List("hanNamesHerfindhalIndex").map(RatioFeature) :::
      words.map(w => WordFeature("page", w)) :::
      words.map(w => WordFeature("url", w))
  }

  def exportFeatures(r: ResultSet): DenseVector = {
    val values =
      List(r.getInt("text_vpm_r1"), r.getInt("text_vpm_r2"), r.getInt("text_vpm_r3"), r.getInt("text_vpm_r4")).map(_.toDouble) :::
        List(r.getInt("url_vpm_r1"), r.getInt("url_vpm_r2"), r.getInt("url_vpm_r3"), r.getInt("url_vpm_r4")).map(_.toDouble) :::
        List(r.getInt("numWords").toDouble, r.getInt("numDistinctWords").toDouble, r.getInt("longestPatentSequenceSize").toDouble) :::
        List(r.getInt("numPages").toDouble, r.getInt("numDistinctMatchedPatents").toDouble, r.getInt("numDistinctHanNames").toDouble) :::
        List(r.getDouble("shareTop1"), r.getDouble("shareTop2"), r.getDouble("shareTop3"), r.getDouble("shareTop4"), r.getDouble("shareTop5")) :::
        List(r.getDouble("hanNamesHerfindhalIndex")) :::
        wordsFound(normalizeDistinctWordsText(r.getString("distinctWordsText"))) :::
        wordsFound(wordsFromUrlPath(r.getString("url")).getOrElse(List.empty))

    new DenseVector(values.toArray)
  }

  def wordsFromUrlPath(url: String): Try[List[String]] = pathFromUrl(url).map { path =>
    path.replaceAll("[^a-zA-Z]", " ").replaceAll("(?<=[a-z])(?=[A-Z])", " ").replaceAll("[\\h\\s\\v]+", " ").trim.toLowerCase.split(" ").toList
  }

  def pathFromUrl(url: String): Try[String] = Try {
    val u = new java.net.URL(url)
    if (u.getRef == null) u.getFile else u.getFile + "#" + u.getRef
  }

  def normalizeDistinctWordsText(text: String): List[String] = {
    val trimmed = text.trim
    if (trimmed.isEmpty) List.empty[String] else trimmed.split(" ").toList
  }

  def wordsFound(distinctWords: List[String]): List[Double] =
    words.map(distinctWords.contains).map(toDouble)

  val toDouble = Map(true -> 1.0, false -> 0.0)
}

case class WordFeature(context: String, word: String) extends BooleanFeature {
  val name = s"${context}_word_${word}"

  override def simpleFilterExp(isLessOrEqual: Boolean, value: Double): String =
    name + " = " + isTrue(isLessOrEqual, value)

  override lazy val sqlField: String = "(" + (context match {
    case "page" => "locate(' " + word + " ', distinctWords) != 0"
    case "url" => "locate('" + word + "', lcase(url)) != 0"  // todo: add new column in table with urlDistictWords
    case _ => throw new IllegalArgumentException(s"invalid context: $context")
  }) + ")"
}
