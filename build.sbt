name := "home-metrics"

version := "1.0"

scalaVersion := "2.12.4"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "twitter-repo" at "https://maven.twttr.com"

val netty4Version = "4.1.16.Final"

libraryDependencies ++= Seq(
  "io.netty" % "netty-resolver-dns" % netty4Version,
  "io.netty" % "netty-codec-dns" % netty4Version,

  "com.amazonaws" % "aws-java-sdk" % "1.11.258",

  "com.twitter" %% "twitter-server" % "17.12.0",
  "com.twitter" %% "twitter-server-slf4j-jdk14" % "17.12.0",

  "com.twitter" %% "util-logging" % "17.12.0",
  "com.twitter" %% "finagle-stats" % "17.12.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
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
