package pl.touk.esp.engine.standalone.http

import java.io.File
import java.net.URLClassLoader

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.standalone.management.{DeploymentService, FileProcessRepository}
import pl.touk.esp.engine.util.ThreadUtils

import scala.util.Try

object StandaloneHttpApp extends Directives with Argonaut62Support with LazyLogging {

  implicit val system = ActorSystem("esp-standalone-http")

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val processesClassLoader = loadProcessesClassloader(config)
  val creator = loadCreator(config)


  val deploymentService = DeploymentService(creator, config)

  def main(args: Array[String]): Unit = {
    val ports = for {
      mgmPort <- Try(args(0).toInt).toOption
      processesPort <- Try(args(1).toInt).toOption
    } yield (mgmPort, processesPort)
    ports match {
      case Some((mgmPort, procPort)) => initHttp(mgmPort, procPort)
      case None => initHttp()
    }
  }


  val managementRoute = new ManagementRoute(processesClassLoader, deploymentService)

  val processRoute = new ProcessRoute(processesClassLoader, deploymentService)


  def initHttp(managementPort: Int = 8070, processesPort: Int = 8080) = {
    Http().bindAndHandle(
      managementRoute.route,
      interface = "0.0.0.0",
      port = managementPort
    )

    Http().bindAndHandle(
      processRoute.route,
      interface = "0.0.0.0",
      port = processesPort
    )

  }

  def loadCreator(config: Config): ProcessConfigCreator = {
    ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
      ThreadUtils.loadUsingContextLoader(config.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]
    }
  }

  def loadProcessesClassloader(config: Config): ClassLoader = {
    if (!config.hasPath("jarPath")) { //to troche slabe, ale na razie chcemy jakos testowac bez jara...
      getClass.getClassLoader
    } else {
      val jarFile = new File(config.getString("jarPath"))
      val classLoader = new URLClassLoader(Array(jarFile.toURI.toURL), getClass.getClassLoader)
      classLoader
    }
  }

}