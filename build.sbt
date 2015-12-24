name := """play-elasticsearch-sample"""

version := "1.0-SNAPSHOT"

lazy val module = (project in file("module")).enablePlugins(PlayScala)

lazy val root = (project in file(".")).enablePlugins(PlayScala).aggregate(module).dependsOn(module)


scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  ws,
  jdbc,
  "org.webjars" %% "webjars-play" % "2.4.0-1",
  "org.webjars" % "bootswatch-superhero" % "3.3.5", // Bootstrap and jquery come with it
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24", // Bootstrap and jquery included
  "org.scalikejdbc" %% "scalikejdbc" % "2.2.7",
  "org.scalikejdbc" %% "scalikejdbc-config" % "2.2.7",
  "org.scalikejdbc" %% "scalikejdbc-syntax-support-macro" % "2.2.7",
  "org.scalikejdbc" %% "scalikejdbc-test" % "2.2.7" % "test",
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.4.0",
  "org.scalikejdbc" %% "scalikejdbc-play-dbapi-adapter" % "2.4.0",
  "org.scalikejdbc" %% "scalikejdbc-play-fixture" % "2.4.0",
  "org.flywaydb" %% "flyway-play" % "2.0.1"
)

scalacOptions in Test ++= Seq("-Yrangepos")

libraryDependencies += specs2

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"


// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
