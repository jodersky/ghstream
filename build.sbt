name := "akka-github"

scalaVersion := "2.11.8"

val AkkaVersion = "2.4.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % AkkaVersion,
  "io.spray" %%  "spray-json" % "1.3.2"
)
