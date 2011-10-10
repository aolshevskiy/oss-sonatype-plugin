package com.github.siasia

import sbt._
import org.apache.maven.cli.MavenCli
import org.apache.maven.eventspy.{EventSpy => MavenEventSpy, AbstractEventSpy}
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.logging.{Logger => PlexusLogger, AbstractLogger, BaseLoggerManager}
import scala.collection.JavaConversions._
import java.io.{PrintWriter, StringWriter}

class LoggerAdapter(descriptor: LoggerDescriptor) extends AbstractLogger(PlexusLogger.LEVEL_DEBUG, "MavenLogger") {
	private val delegate = descriptor.logger
	def getChildLogger(name: String) = null
	def fatalError(message: String, th: Throwable) { error("[fatal] " + message, th)	}
	def error(message: String, th: Throwable) {
		delegate.error(message)
		if(th != null)
			delegate.trace(th)
	}
	def warn(message: String, th: Throwable) {
		delegate.warn(message)
		if(th != null)
			delegate.trace(th)
	}
	def info(message: String, th: Throwable) {
		delegate.info(message)
		descriptor.writer.foreach(_.println(message))
		if(th != null)
			delegate.trace(th)
	}
	def debug(message: String, th: Throwable) {
		delegate.debug(message)
		if(th != null)
			delegate.trace(th)
	}
}

class LoggerManager extends BaseLoggerManager {
	private val logger = new LoggerAdapter(MavenPlugin.descriptor.get)
	override def createLogger(key: String) = logger
}

class EventSpy extends AbstractEventSpy {
	override def init(context: MavenEventSpy.Context) {
		super.init(context)
		val container = context.getData()("plexus").asInstanceOf[DefaultPlexusContainer]
		container.setLoggerManager(new LoggerManager)
	}
}

case class LoggerDescriptor(logger: Logger, writer: Option[PrintWriter])
	
object MavenPlugin extends Plugin {
	private[siasia] var descriptor = new ThreadLocal[LoggerDescriptor]
	def withLoader[T](t: => T): T = {
		val thread = Thread.currentThread
		val loader = thread.getContextClassLoader
		thread.setContextClassLoader(getClass.getClassLoader)
		try {
			t
		} finally {
			thread.setContextClassLoader(loader)
		}
	}
	def mavenOutput(logger: Logger)(args: String*)(properties: (String, String)*): String = {
		val string = new StringWriter()
		val writer = new PrintWriter(string)
		descriptor.set(LoggerDescriptor(logger, Some(writer)))
		mavenInvoke0(args :_*)(properties :_*)
		string.toString
	}
	def mavenInvoke(logger: Logger)(args: String*)(properties: (String, String)*) = {
		descriptor.set(LoggerDescriptor(logger, None))
		mavenInvoke0(args :_*)(properties :_*)
	}
	private def mavenInvoke0(args: String*)(properties: (String, String)*) = withLoader {
		try {
			val finalArgs = args ++ properties.map {
				case(key, value) => "-D"+key+"="+value
			}
			val result = MavenCli.main(finalArgs.toArray, null)
			if(result != 0)
				sys.error("Maven execution failed. Exit code: " + result)
		} finally {
			descriptor.remove()
		}
	}
}
