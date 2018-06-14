name := "home-metrics"

version := "1.0"

scalaVersion := "2.12.6"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "twitter-repo" at "https://maven.twttr.com"

val netty4Version = "4.1.25.Final"
val twitterVersion = "18.5.0"

libraryDependencies ++= Seq(
  "io.netty" % "netty-resolver-dns" % netty4Version,
  "io.netty" % "netty-codec-dns" % netty4Version,

  "com.amazonaws" % "aws-java-sdk" % "1.11.347" excludeAll(ExclusionRule(organization = "io.netty")),

  "com.twitter" %% "twitter-server" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
  "com.twitter" %% "twitter-server-slf4j-jdk14" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),

  "com.twitter" %% "util-logging" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
  "com.twitter" %% "finagle-stats" % twitterVersion excludeAll(ExclusionRule(organization = "io.netty")),
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
