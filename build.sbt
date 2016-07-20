import com.github.retronym.SbtOneJar._

oneJarSettings

name := "home-metrics"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += "twitter-repo" at "https://maven.twttr.com"

libraryDependencies ++= Seq(
  "com.twitter" %% "twitter-server" % "1.21.0",
  "com.twitter" %% "finagle-stats" % "6.36.0",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0"
  //"org.apache.commons" % "commons-io" % "1.3.2"
)
