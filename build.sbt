name := "smickhome-metrics"
version := "1.10"
scalaVersion := "2.13.8"

scalacOptions ++= Seq("-unchecked", "-deprecation")

val netty4Version = "4.1.76.Final"
val twitterVersion = "22.4.0"
val awsVersion = "1.11.1024"
val scalaXmlVersion = "2.0.0"

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

enablePlugins(JavaServerAppPackaging, DockerPlugin)
Compile / mainClass := Some("smick.Main")

dockerRepository := Some("registry.squishtech.com")
dockerBaseImage := "openjdk:11"
dockerUpdateLatest := true
dockerExposedPorts ++= Seq(9990, 7777)
