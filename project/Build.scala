import sbt._
import Keys._
import com.github.siasia.SonatypePlugin._

object SonatypeBuild extends Build {
	def sharedSettings: Seq[Setting[_]] = Seq(
		organization := "com.github.siasia",
		version <<= sbtVersion(_ + "-0.1"),
		sbtPlugin := true,
		projectID <<= (organization,moduleName,version,artifacts,crossPaths){ (org,module,version,as,crossEnabled) =>
			ModuleID(org, module, version).cross(crossEnabled).artifacts(as : _*)
    },
		pomUrl := "http://github.com/siasia/oss-sonatype-plugin",
		licenses := Seq(
			"BSD 3-Clause" -> new URL("https://github.com/siasia/xsbt-web-plugin/blob/master/LICENSE")
		),
		scm <<= name { name => (
			"scm:git:git@github.com:siasia/oss-sonatype-plugin.git",
			"scm:git:git@github.com:siasia/oss-sonatype-plugin.git",
			"git@github.com:siasia/oss-sonatype-plugin.git"
		)},
		developers := Seq((
			"siasia",
			"Artyom Olshevskiy",
			"siasiamail@gmail.com"
		))
	) ++ sonatypeSettings
	
	def rootSettings = Seq(
		name := "oss-sonatype-plugin"
	)
	def mavenSettings = Seq(
		name := "maven-plugin",
		libraryDependencies += "org.apache.maven" % "apache-maven" % "3.0.3"
	)

	def nexusSettings = Seq(
		name := "nexus-plugin"
	)

	lazy val maven = Project("maven", file("maven")) settings(
		sharedSettings ++ mavenSettings :_*)
	lazy val nexus = Project("nexus", file("nexus")) settings(
		sharedSettings ++ nexusSettings :_*) dependsOn(maven)
	lazy val root = Project("root", file(".")) settings(
		sharedSettings ++ rootSettings :_*) dependsOn(nexus)
}
