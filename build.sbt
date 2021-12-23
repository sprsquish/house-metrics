name := "smickhome-metrics"
version := "1.10"
scalaVersion := "2.13.6"

scalacOptions ++= Seq("-unchecked", "-deprecation")

val netty4Version = "4.1.65.Final"
val twitterVersion = "21.4.0"
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

enablePlugins(JavaServerAppPackaging, RpmPlugin, SystemdPlugin)
Compile / mainClass := Some("smick.Main")

packageName := "house-metrics"
rpmVendor := "smickhome"
rpmLicense := Some("MIT")
rpmRequirements := Seq("jre-11-headless")
Rpm / serviceAutostart := true
