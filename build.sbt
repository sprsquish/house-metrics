name := "home-metrics"

version := "1.0"

scalaVersion := "2.12.4"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "twitter-repo" at "https://maven.twttr.com"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.twitter" %% "twitter-server" % "17.12.0",
  "com.twitter" %% "twitter-server-logback-classic" % "17.12.0",
  "com.twitter" %% "finagle-stats" % "17.12.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
)

assemblyJarName in assembly := "smickhome-metrics.jar"
test in assembly := None
mainClass in assembly := Some("smick.Main")
assemblyMergeStrategy in assembly := {
  case "BUILD"                                        => MergeStrategy.discard
  case x if x endsWith "io.netty.versions.properties" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
