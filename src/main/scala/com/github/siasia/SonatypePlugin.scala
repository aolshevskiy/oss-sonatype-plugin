package com.github.siasia

import sbt.{Node => _, _}
import Keys._
import MavenPlugin._
import NexusPlugin._
import scala.xml._

object SonatypePlugin extends Plugin {
	val sonatypePublish = TaskKey[Unit]("sonatype-publish")
	val pomUrl = SettingKey[String]("pom-url")
	val scm = SettingKey[(String, String, String)]("scm")
	val developers = SettingKey[Seq[(String, String, String)]]("developers")
	val isSnapshot = SettingKey[Boolean]("is-snapshot")

	def pomPostProcessTask = (pomPostProcess, pomUrl, scm, licenses, developers) {
		(pomPostProcess, pomUrl, scm, lics, devs) =>
			(node: Node) =>
				node match {
					case xml: Elem =>
						val children = Seq(
							<url>{pomUrl}</url>,
							scm match {
								case (conn, dev, url) =>
								<scm>
									<connection>{conn}</connection>
									<developerConnection>{dev}</developerConnection>
									<url>{url}</url>
								</scm>
							},
							<developers>{devs.map {
								case (id, name, email) =>
								<developer>
									<id>{id}</id>
									<name>{name}</name>
									<email>{email}</email>
								</developer>
							}}</developers>,
							<parent>
							<groupId>org.sonatype.oss</groupId>
							<artifactId>oss-parent</artifactId>
							<version>7</version>
							</parent>
						)
					pomPostProcess(xml.copy(child = xml.child ++ children))
				}
	}

	def readPassword(prompt: String) = synchronized {
		jline.Terminal.getTerminal().disableEcho()
		val pwd = new jline.ConsoleReader().readLine(prompt, '*')
		jline.Terminal.getTerminal().enableEcho()
		pwd
	}

	def sonatypePublishTask = (streams, packagedArtifacts, nexusUrl, authId) map {
		(s, as, nexus, auth) =>
		def gpgSignAndDeploy = mavenInvoke(s.log)("gpg:sign-and-deploy-file") _
		val descriptor = PublishDescriptor(as)
		val passphrase = readPassword("GPG passphrase:")
		def deploy(artifact: File, classifier: Option[String]) = { 
			val properties = Seq(
				"url" -> (nexus + "service/local/staging/deploy/maven2"),
				"repositoryId" -> auth,
				"pomFile" -> descriptor.pom.getPath,
				"file" -> artifact.getPath,
				"gpg.passphrase" -> passphrase
			) ++ classifier.map("classifier" -> _).toList
			gpgSignAndDeploy(properties :_*)
		}
		descriptor.artifacts.foreach {
			case (artifact, classifier) => deploy(artifact, classifier)
		}
	}

	def sonatypeSettings: Seq[Setting[_]] = Seq(
		publishMavenStyle := true,
		pomIncludeRepository := ((_) => false),
		pomPostProcess <<= pomPostProcessTask,
		nexusUrl := url("https://oss.sonatype.org/"),
		authId := "sonatype-nexus-staging",
		isSnapshot <<= version(_.trim.endsWith("SNAPSHOT")),
		publishTo <<= (isSnapshot, nexusUrl) {
			(snapshot, nexus) =>
			if (snapshot)
				Some("snapshots" at nexus + "content/repositories/snapshots") 
			else Some("staging" at nexus + "service/local/staging/deploy/maven2/")
		},
		sonatypePublish <<= sonatypePublishTask,
		publish <<= (publish.task, sonatypePublish.task, isSnapshot) flatMap {
			(pub, sonatype, snapshot) =>
			if (snapshot)
				pub
			else sonatype
		}		
	) ++ nexusSettings
}
