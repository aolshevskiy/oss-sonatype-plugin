package com.github.siasia

import sbt._
import Keys._
import MavenPlugin._
import Cache.seqFormat
import complete._
import sbinary.DefaultProtocol._

object NexusPlugin extends Plugin {
	val nexusUrl = SettingKey[URL]("nexus-url")
	val authId = SettingKey[String]("auth-id")
	val discoveredStagingRepos = TaskKey[Seq[String]]("discovered-staging-repos")
	val stagingClose = InputKey[Unit]("staging-close")
	val stagingRelease = InputKey[Unit]("staging-release")
	val stagingDrop = InputKey[Unit]("staging-drop")

	lazy private val Reg = """^-  (\S+) \(profile: """.r
	
	def discoverStagingRepos = (streams, nexusUrl, authId, organization) map {
		(s, nexus, auth, group) =>
		val result = mavenOutput(s.log)("nexus:staging-list")(
			"nexus.groupId" -> group,
			"nexus.url" -> nexus.toString,
			"nexus.automaticDiscovery" -> "true",
			"nexus.serverAuthId" -> auth
		)
		val rs = augmentString(result).lines.flatMap {
			line =>
			Reg.findFirstMatchIn(line).map(_.group(1))
		}
		rs.toSeq
	}

	def stagingParser: (State, Seq[String]) => Parser[String] =
	{
		import DefaultParsers._
		(state, staging) => Space ~> token(NotSpace examples staging.toSet)
	}

	def stagingTask(action: String)(extra: (String, String)*) = 
		InputTask(TaskData(discoveredStagingRepos)(stagingParser)(Nil)) {
			result =>
				(streams, nexusUrl, authId, organization, result) map { 
					(s, nexus, auth, group, repo) =>
						val props = Seq(
							"nexus.groupId" -> group,
							"nexus.repositoryId" -> repo,
							"nexus.url" -> nexus.toString,
							"nexus.automaticDiscovery" -> "true",
							"nexus.serverAuthId" -> auth
						) ++ extra
						mavenInvoke(s.log)("nexus:staging-"+action)(props :_*)
				}
		}
	
	def nexusSettings: Seq[Setting[_]] = Seq(
		discoveredStagingRepos <<= TaskData.write(discoverStagingRepos) triggeredBy(publish),
		stagingClose <<= stagingTask("close")(),
		stagingRelease <<= stagingTask("release")("targetRepositoryId" -> "releases"),
		stagingDrop <<= stagingTask("drop")()
	)
}
