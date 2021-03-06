package pl.touk.nussknacker.engine.standalone.http


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Json
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter.GenericResultType
import pl.touk.nussknacker.engine.standalone.management.DeploymentService
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext

class ProcessRoute(processesClassLoader: ClassLoader, deploymentService: DeploymentService)  extends Directives with Argonaut62Support with LazyLogging {

  import argonaut.ArgonautShapeless._

  def route(implicit ec: ExecutionContext): Route = ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
    path(Segment) { processPath =>
      post {
        entity(as[Array[Byte]]) { bytes =>
          val interpreter = deploymentService.getInterpreterByPath(processPath)
          interpreter match {
            case None =>
              complete {
                HttpResponse(status = StatusCodes.NotFound)
              }
            case Some(processInterpreter) =>
              val input = processInterpreter.source.toObject(bytes)
              complete {
                //TODO: handle multiple outputs?
                processInterpreter.invoke(input).map(toResponse)
              }
          }
        }
      }
    }
  }

  //TODO: is it ok?
  def toResponse(either: GenericResultType[Any, EspExceptionInfo[_<:Throwable]]) : ToResponseMarshallable = {
    val withErrorsMapped : GenericResultType[Any, EspError] = either.left
      .map(_.map(exception => EspError(exception.nodeId, exception.throwable.getMessage)))

    val withJsonsConverted  : GenericResultType[GenericResultType[Json, EspError], EspError] = withErrorsMapped.right.map(toJsonOrErrors)

    withJsonsConverted.right.flatMap[NonEmptyList[EspError], List[Json]](StandaloneProcessInterpreter.foldResults)
  }

  private def toJsonOrErrors(values: List[Any]) : List[GenericResultType[Json, EspError]] = values.map(toJsonOrError)

  private def toJsonOrError(value: Any) : GenericResultType[Json, EspError] = value match {
    case a:Displayable => Right(List(a.display))
    case a:String => Right(List(Json.jString(a)))
    case a => Left(NonEmptyList.of(EspError(None, s"Invalid result type: ${a.getClass}")))
  }

  case class EspError(nodeId: Option[String], message: String)


}
