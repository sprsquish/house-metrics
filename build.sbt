name := "smickhome-metrics"
version := "1.0"
scalaVersion := "2.13.4"

scalacOptions ++= Seq("-unchecked", "-deprecation")

val netty4Version = "4.1.54.Final"
val twitterVersion = "20.10.0"
val awsVersion = "1.11.872"
val scalaXmlVersion = "1.3.0"

libraryDependencies ++= Seq(
  "io.netty" % "netty-all" % netty4Version,

  "com.amazonaws" % "aws-java-sdk-bom" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-route53" % awsVersion excludeAll(ExclusionRule(organization = "io.netty")),

  "com.twitter" %% "twitter-server" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
  "com.twitter" %% "twitter-server-slf4j-jdk14" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),

  "com.twitter" %% "util-logging" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
  "com.twitter" %% "finagle-stats" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
  "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion
)

assemblyJarName in assembly := "smickhome-metrics.jar"
test in assembly := None
mainClass in assembly := Some("smick.Main")
assemblyMergeStrategy in assembly := {
  case "BUILD"                                        => MergeStrategy.discard
  case x if x endsWith "io.netty.versions.properties" => MergeStrategy.discard
  case x if x contains "org/apache/commons/logging"   => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
