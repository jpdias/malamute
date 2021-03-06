package eu.shiftforward.deploylogger.api

import java.net.MalformedURLException
import javax.ws.rs.Path

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.wordnik.swagger.annotations._
import eu.shiftforward.deploylogger.entities._
import eu.shiftforward.deploylogger.persistence._
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._
import spray.routing.{ Route, ExceptionHandler, HttpService }
import spray.util.LoggingContext

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object DeployLoggerService {
  final val json = "application/json; charset=UTF-8"
}

@Api(value = "/api", description = "Deploy Logger Service")
trait DeployLoggerService extends HttpService {

  import DeployLoggerService._

  def actorPersistence: ActorRef

  implicit def ec: ExecutionContext

  implicit val timeout = Timeout(5.seconds)

  implicit def exceptionHandler(implicit log: LoggingContext) = {
    ExceptionHandler {
      case e: DuplicatedEntry =>
        complete(UnprocessableEntity, s"An error occurred: ${e.error}")
      case e =>
        complete(InternalServerError, s"An error occurred: ${e.getMessage}")
    }
  }

  @Path("/ping")
  @ApiOperation(httpMethod = "GET", response = classOf[String], value = "Returns a pong", produces = "text/plain")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK")
  ))
  def pingRoute = path("api" / "ping") {
    get {
      complete("pong")
    }
  }

  @Path("/projects")
  @ApiOperation(httpMethod = "GET", response = classOf[List[ResponseProject]], value = "Returns an array of Projects", produces = json)
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK")
  ))
  def projectsGetRoute = path("api" / "projects") {
    get {
      complete((actorPersistence ? GetProjects).mapTo[List[ResponseProject]])
    }
  }

  @Path("/project")
  @ApiOperation(httpMethod = "POST", response = classOf[ResponseProject], value = "Returns a Project", consumes = json, produces = "*/*")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "Deploy Object", dataType = "eu.shiftforward.deploylogger.entities.RequestProject", required = true, paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 422, message = "UnprocessableEntity"),
    new ApiResponse(code = 400, message = "BadRequest"),
    new ApiResponse(code = 500, message = "InternalServerError")
  ))
  def projectPostRoute = path("api" / "project") {
    post {
      entity(as[RequestProject]) { proj =>
        complete((actorPersistence ? SaveProject(proj)).mapTo[ResponseProject])
      }
    }
  }

  @Path("/project/{projName}/deploy")
  @ApiOperation(httpMethod = "POST", response = classOf[ResponseDeploy], value = "Returns a Deploy", consumes = json, produces = "*/*")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched"),
    new ApiImplicitParam(name = "body", value = "Deploy Object", dataType = "eu.shiftforward.deploylogger.entities.RequestDeploy", required = true, paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 400, message = "BadRequest"),
    new ApiResponse(code = 404, message = "NotFound"),
    new ApiResponse(code = 500, message = "InternalServerError")
  ))
  def projectDeployPostRoute = path("api" / "project" / Segment / "deploy") { name =>
    post {
      entity(as[RequestDeploy]) { deploy =>
        complete((actorPersistence ? AddDeploy(name, deploy)).mapTo[Option[ResponseDeploy]])
      }
    }
  }

  @Path("/project/{projName}/deploys")
  @ApiOperation(httpMethod = "GET", response = classOf[List[ResponseDeploy]], value = "Returns a List of Deploy", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectDeploysGetRoute = path("api" / "project" / Segment / "deploys") { projName =>
    parameters("max".?[Int](10)) { max: Int =>
      get {
        complete((actorPersistence ? GetDeploys(projName, max)).mapTo[List[ResponseDeploy]])
      }
    }
  }

  @Path("project/{projName}/clients")
  @ApiOperation(httpMethod = "GET", response = classOf[List[String]], value = "Returns a List of Clients", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectGetClients = path("api" / "project" / Segment / "clients") { projName =>
    get {
      import spray.json.DefaultJsonProtocol._
      complete((actorPersistence ? GetClients(projName)).mapTo[Option[List[String]]])
    }
  }

  @Path("/project/{projName}/deploy/{deployId}/event")
  @ApiOperation(httpMethod = "POST", response = classOf[ResponseEvent], value = "Add a event to a deploy", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched"),
    new ApiImplicitParam(name = "deployId", required = true, dataType = "string", paramType = "path", value = "Id of deploy that needs to be fetched"),
    new ApiImplicitParam(name = "body", value = "Event Object", dataType = "eu.shiftforward.deploylogger.entities.RequestEvent", required = true, paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectDeployEventPostRoute = path("api" / "project" / Segment / "deploy" / Segment / "event") { (projName, deployId) =>
    post {
      entity(as[RequestEvent]) { ev =>
        complete((actorPersistence ? AddEvent(projName, deployId, ev)).mapTo[Option[ResponseEvent]])
      }
    }
  }

  @Path("/project/{projName}/deploy/{deployId}")
  @ApiOperation(httpMethod = "GET", response = classOf[ResponseDeploy], value = "Returns a Deploy", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched"),
    new ApiImplicitParam(name = "deployId", required = true, dataType = "string", paramType = "path", value = "Id of deploy that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectDeployGetRoute = path("api" / "project" / Segment / "deploy" / Rest) { (projName, deployId) =>
    get {
      complete((actorPersistence ? GetDeploy(projName, deployId)).mapTo[Option[ResponseDeploy]])
    }
  }

  @Path("/project/{projName}/client/{clientName}")
  @ApiOperation(httpMethod = "GET", response = classOf[List[ResponseModule]], value = "Returns a Module List", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched"),
    new ApiImplicitParam(name = "clientName", required = true, dataType = "string", paramType = "path", value = "Client that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectGetModules = path("api" / "project" / Segment / "client" / Rest) { (projName, clientName) =>
    get {
      complete((actorPersistence ? GetModules(projName, clientName)).mapTo[Option[List[ResponseModule]]])
    }
  }

  @Path("/project/{projName}")
  @ApiOperation(httpMethod = "GET", response = classOf[ResponseProject], value = "Returns a Project", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectGetRoute = path("api" / "project" / Rest) { name =>
    get {
      complete((actorPersistence ? GetProject(name)).mapTo[Option[ResponseProject]])
    }
  }

  @Path("/project/{projName}")
  @ApiOperation(httpMethod = "DELETE", response = classOf[ResponseProject], value = "Returns a Project", produces = json)
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "projName", required = true, dataType = "string", paramType = "path", value = "Name of project that needs to be fetched")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "OK"),
    new ApiResponse(code = 404, message = "NotFound")
  ))
  def projectDeleteRoute = path("api" / "project" / Rest) { name =>
    delete {
      complete((actorPersistence ? DeleteProject(name)).mapTo[Option[ResponseProject]])
    }
  }
}
