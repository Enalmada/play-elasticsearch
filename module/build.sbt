name := """play-elasticsearch"""

version := "0.1.5"

lazy val module = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  ws,
  specs2 % Test,
  "org.specs2" %% "specs2-matcher-extra" % "3.6.5" % "test",
  "org.specs2" %% "specs2-junit" % "3.6.5" % "test",
  "org.elasticsearch" % "elasticsearch" % "1.5.2" % "test",
  "io.argonaut" %% "argonaut" % "6.1" % "test"
)

scalacOptions in Test ++= Seq("-Yrangepos")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"


// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// These tests depend on the order.  Should fix that so they are independant.
parallelExecution in Test := false


//*******************************
// Maven settings
//*******************************

publishMavenStyle := true

organization := "com.github.enalmada"

description := "Playframework/Scala ElasticSearch HTTP api."

startYear := Some(2015)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra in Global := {
  <url>https://github.com/Enalmada/play-elasticsearch</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:git@github.com:enalmada/play-elasticsearch.git</connection>
      <developerConnection>scm:git:git@github.com:enalmada/play-elasticsearch.git</developerConnection>
      <url>https://github.com/enalmada</url>
    </scm>
    <developers>
      <developer>
        <id>enalmada</id>
        <name>Adam Lane</name>
        <url>https://github.com/enalmada</url>
      </developer>
    </developers>
}

credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")
