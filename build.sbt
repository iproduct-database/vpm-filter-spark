//val sparkVersion = "2.1.0"
val sparkVersion = "2.0.2"
val hadoopVersion = "2.7.3"

name := "vpm-filter-spark"

version := "0.4-SNAPSHOT"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8")
    sys.error("Java 8 is required for this project.")
}

scalaVersion := "2.11.11"

val logbackVersion = "1.2.3"

val slf4jVersion = "1.7.25"

// warcbase is not yet published anywhere, and it has dependencies on cloudera and internetarchive
resolvers += "dportabella-3rd-party-mvn-repo-releases" at "https://github.com/dportabella/3rd-party-mvn-repo/raw/master/releases/"
resolvers += "dportabella-3rd-party-mvn-repo-snapshots" at "https://github.com/dportabella/3rd-party-mvn-repo/raw/master/snapshots/"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  "org.warcbase" % "warcbase-core" % "0.1.0-SNAPSHOT"
    excludeAll(
    ExclusionRule(organization = "org.apache.spark"),
    ExclusionRule(organization = "org.apache.hadoop")),
  "xml-apis" % "xml-apis" % "1.4.01", // dependency problem with warcbase
  "com.github.scopt" %% "scopt" % "3.5.0",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.3",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3",
  "org.apache.commons" % "commons-lang3" % "3.4",
  "mysql" % "mysql-connector-java" % "5.1.40",
  "com.google.guava" % "guava" % "14.0.1",  // the version used by spark
  "com.lihaoyi" %% "pprint" % "0.5.2",
  "com.typesafe.play" %% "anorm" % "2.5.2",
  //"com.typesafe.play" %% "play-ws" % "2.5.15",
  "com.typesafe.play" %% "play-json" % "2.5.15"
    excludeAll(
    ExclusionRule(organization = "com.fasterxml.jackson.core"),
    ExclusionRule(organization = "com.fasterxml.jackson.datatype")
  ),
  "org.netpreserve.commons" % "webarchive-commons" % "1.1.7"
    excludeAll ExclusionRule(organization = "org.apache.hadoop"),
  "org.apache.commons" % "commons-io" % "1.3.2",
  "org.jsoup" % "jsoup" % "1.10.2",
  "com.syncthemall" % "boilerpipe" % "1.2.2",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5",
  "org.apache.pdfbox" % "pdfbox" % "2.0.2",
  "org.apache.pdfbox" % "pdfbox-tools" % "2.0.2",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0",
  //  "edu.stanford.nlp" % "stanford-parser" % "3.6.0",
  //  "edu.stanford.nlp" % "stanford-kbp" % "1.0.0"
  "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0" classifier "models",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
  "ch.qos.logback" %  "logback-classic" % "1.1.7",
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "commons-logging" % "commons-logging" % "1.2"
)

// dependencyOverrides ++= Set("com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7")

/*
  "org.warcbase" % "warcbase-core" % "0.1.0-SNAPSHOT"
     exclude("org.apache.hadoop", "hadoop-client")
     exclude("org.apache.spark", "spark-core")
     exclude("org.apache.spark", "spark-graphx")
     ,
*/

// taken from https://github.com/databricks/learning-spark/blob/master/build.sbt
mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case m if m.toLowerCase.endsWith("manifest.mf") => MergeStrategy.discard
    case m if m.startsWith("META-INF") => MergeStrategy.discard
    case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", xs @ _*) => MergeStrategy.first
    case PathList("org", "jboss", xs @ _*) => MergeStrategy.first
    case "about.html"  => MergeStrategy.rename
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }
}

runMain in Compile <<= Defaults.runMainTask(fullClasspath in Compile, runner in(Compile, run))

javaOptions in Test += "-Djava.library.path=./lib"
javaOptions in runMain += "-Djava.library.path=./lib"
