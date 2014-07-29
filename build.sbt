name := """play-scala"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
//  jdbc,
//  anorm,
//  cache,
  ws,
  "com.typesafe.akka" %% "akka-actor" % "2.3.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.3",
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" % "flot" % "0.8.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test"
)
