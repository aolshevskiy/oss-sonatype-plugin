package com.github.siasia

import sbt._

object PublishDescriptor {
	def apply(artifacts: Map[Artifact, File]) = {
		val (pom, rawArtifacts) =
			artifacts.partition {
				case (artifact, _) =>
				artifact.`type` == "pom"
			}
		val as = rawArtifacts.toSeq.map {
			case (artifact, file) => (file, artifact.classifier)
		}
		new PublishDescriptor(pom.toSeq(0)._2, as)
	}
}

class PublishDescriptor(val pom: File, val artifacts: Seq[(File, Option[String])])
