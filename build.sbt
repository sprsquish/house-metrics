name := "smickhome-metrics"
version := "1.5"
scalaVersion := "2.13.4"

scalacOptions ++= Seq("-unchecked", "-deprecation")

val netty4Version = "4.1.56.Final"
val twitterVersion = "21.1.0"
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

enablePlugins(JavaServerAppPackaging, RpmPlugin, SystemdPlugin)
mainClass in Compile := Some("smick.Main")

packageName := "house-metrics"
rpmVendor := "smickhome"
rpmLicense := Some("MIT")
rpmRequirements := Seq("jre-11-headless")
serviceAutostart in Rpm := true
