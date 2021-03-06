package eu.shiftforward.deploylogger

import java.util.UUID

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import eu.shiftforward.deploylogger.api.DeployLoggerService
import eu.shiftforward.deploylogger.entities._
import eu.shiftforward.deploylogger.persistence.SlickPersistenceActor
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport._
import spray.testkit.Specs2RouteTest
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class DeployLoggerRouteSpec extends Specification with Specs2RouteTest {

  implicit val routeTestTimeout = RouteTestTimeout(Duration(5, SECONDS))

  def config() = ConfigFactory.parseString(
    s"""
      |persistence {
      | connectionPool = disabled
      | driver = "org.sqlite.JDBC"
      | url = "jdbc:sqlite:testdb:test-${UUID.randomUUID}"
      |}
    """.stripMargin
  )

  class MockDeployLoggerService extends DeployLoggerService with Scope {
    override def actorRefFactory = system
    val actorPersistence = system.actorOf(Props(new SlickPersistenceActor(config)))
    //val actorPersistence = system.actorOf(Props[MemoryPersistenceActor])
    def ec: ExecutionContext = system.dispatcher
  }

  "The deployLoggerService" should {
    "handle /api/ping" in {
      "return a 'pong' response for GET requests" in new MockDeployLoggerService {
        Get("/api/ping") ~> pingRoute ~> check {
          status === OK
          responseAs[String] === "pong"
        }
      }
    }
    "handle /api/project" in {
      "return a 'JSON obj Project' response for POST requests " in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
      }

      "return a 422 - UnprocessableEntity response for POST requests with a duplicated name" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj1", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj1")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project",
            RequestProject("TestProj1", "Proj Description Test", "http://bitbucket.com/abc")
          ) ~> projectPostRoute ~> check {
            status === UnprocessableEntity
          }
        }
      }
    }
    "handle /api/projects" in {
      "return a 'JSON Array of Project' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj2", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj2")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        Post("/api/project",
          RequestProject("TestProj20", "Proj Description Test 1", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj20")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test 1")
        }
        eventually {
          Get("/api/projects") ~> projectsGetRoute ~> check {
            status === OK
            responseAs[List[ResponseProject]].length must beEqualTo(2)
          }
        }
      }
    }
    "handle /api/project/:name" in {
      "return a 'JSON of Project' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj4", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj4")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Get("/api/project/TestProj4") ~> projectGetRoute ~> check {
            status === OK
            responseAs[ResponseProject].name must beEqualTo("TestProj4")
            responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
          }
        }
      }
      "return a 404 response for GET requests to /api/project/:name that doesn't exist" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj5", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj5")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        Get("/api/project/blabla") ~> projectGetRoute ~> check {
          status === NotFound
        }
      }
      "return a 'JSON Obj of Project' response for DELETE requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj6", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj6")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        Post("/api/project",
          RequestProject("TestProj7", "Proj Description Test 1", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj7")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test 1")
        }
        eventually {
          Get("/api/projects") ~> projectsGetRoute ~> check {
            status === OK

            responseAs[List[ResponseProject]].length must beEqualTo(2)
          }
        }
        eventually {
          Delete("/api/project/TestProj6") ~> projectDeleteRoute ~> check {
            status === OK
            responseAs[ResponseProject].name must beEqualTo("TestProj6")
            responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
          }
        }
        eventually {
          Get("/api/projects") ~> projectsGetRoute ~> check {
            status === OK
            responseAs[List[ResponseProject]].length must beEqualTo(1)
          }
        }
      }
      "return a 404 response for DELETE requests to a name that doesn't exist" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj8", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj8")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        Delete("/api/project/babla") ~> projectDeleteRoute ~> check {
          status === NotFound
        }
      }
    }
    "handle /api/project/:name/deploy" in {
      "return a 'JSON obj Project' response for POST requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj9", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj9")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj9/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
        }
      }
      "return a 404 response for POST requests to project that doesn't exist" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj10", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj10")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        Post("/api/project/abc/deploy",
          RequestDeploy("testUser",
            Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
            List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
        ) ~> projectDeployPostRoute ~> check {
          status === NotFound
        }
      }
    }
    "handle /api/project/:name/deploy/:id/event" in {
      "return a 'JSON obj Event' response for POST requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj11", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj11")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj11/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
            val deployId = responseAs[ResponseDeploy].id

            Post("/api/project/TestProj11/deploy/" + deployId + "/event",
              RequestEvent(DeployStatus.Success, "done")
            ) ~> projectDeployEventPostRoute ~> check {
              status === OK
              responseAs[ResponseEvent].status === DeployStatus.Success
            }
            //tests if the first and only deploy have two events (inital + success)
            Get("/api/project/TestProj11/deploys") ~> projectDeploysGetRoute ~> check {
              responseAs[List[ResponseDeploy]].head.events.size === 2
            }
          }
        }
      }
    }
    "handle /api/project/:name/deploy/:id" in {
      "return a 'JSON obj Deploy' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
            val deployId = responseAs[ResponseDeploy].id

            Post("/api/project/TestProj/deploy/" + deployId + "/event",
              RequestEvent(DeployStatus.Success, "done")
            ) ~> projectDeployEventPostRoute ~> check {
              status === OK
              responseAs[ResponseEvent].status === DeployStatus.Success
            }
            eventually {
              Get("/api/project/TestProj/deploy/" + deployId) ~> projectDeployGetRoute ~> check {
                status === OK
                responseAs[ResponseDeploy].id === deployId
                responseAs[ResponseDeploy].modules ===  List(ResponseModule("ModuleX","v0.1",ModuleStatus.Add))
              }
            }
          }
        }
      }
    }
    "handle /api/project/:name/deploys" in {
      "return a 'JSON Array obj Deploy' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj13", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj13")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj13/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
          Post("/api/project/TestProj13/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(RequestModule("ModuleX","v0.1",ModuleStatus.Add)), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }

          Get("/api/project/TestProj13/deploys?max=1") ~> projectDeploysGetRoute ~> check {
            status === OK
            responseAs[List[ResponseDeploy]].size === 1
          }
          Get("/api/project/TestProj13/deploys") ~> projectDeploysGetRoute ~> check {
            status === OK
            responseAs[List[ResponseDeploy]].size === 2
          }
        }
      }
    }
    "handle /api/project/:name/client/:clientName" in {
      "return a 'JSON list obj Module' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(
                RequestModule("ModuleX","v0.1",ModuleStatus.Add),
                RequestModule("ModuleX","v0.1",ModuleStatus.Remove),
                RequestModule("ModuleX","v0.2",ModuleStatus.Add)
              ), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
        }
        eventually {
          Get("/api/project/TestProj/client/Cliente") ~> projectGetModules ~> check {
            status === OK
            responseAs[List[ResponseModule]] === List(ResponseModule("ModuleX","v0.2",ModuleStatus.Add))
          }
        }
      }
      "return a 404-NotFound response for GET requests on a nonExistent Client" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(
                RequestModule("ModuleX", "v0.1", ModuleStatus.Add)
              ), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
        }
        eventually {
          Get("/api/project/ProjNonExistent/client/Cliente") ~> projectGetModules ~> check {
            status === NotFound
          }
        }
      }
    }
    "handle /api/project/:name/clients" in {
      "return a 'JSON list string' response for GET requests" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(
                RequestModule("ModuleX","v0.1",ModuleStatus.Add),
                RequestModule("ModuleX","v0.1",ModuleStatus.Remove),
                RequestModule("ModuleX","v0.2",ModuleStatus.Add)
              ), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente1",
              List(
                RequestModule("ModuleX","v0.1",ModuleStatus.Add),
                RequestModule("ModuleX","v0.1",ModuleStatus.Remove),
                RequestModule("ModuleX","v0.2",ModuleStatus.Add)
              ), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }
          Get("/api/project/TestProj/clients") ~> projectGetClients ~> check {
            status === OK
            import spray.json.DefaultJsonProtocol._
            responseAs[List[String]].length === 2
            responseAs[List[String]] == List("Cliente","Cliente1")
          }
        }
      }
      "return a 404 response for GET requests on a non-existing Project" in new MockDeployLoggerService {
        Post("/api/project",
          RequestProject("TestProj", "Proj Description Test", "http://bitbucket.com/abc")
        ) ~> projectPostRoute ~> check {
          status === OK
          responseAs[ResponseProject].name must beEqualTo("TestProj")
          responseAs[ResponseProject].description must beEqualTo("Proj Description Test")
        }
        eventually {
          Post("/api/project/TestProj/deploy",
            RequestDeploy("testUser",
              Commit("abc124ada", "master"), "testestess", "http://google.com/", "1.1.1", false, "Cliente",
              List(
                RequestModule("ModuleX","v0.1",ModuleStatus.Add),
                RequestModule("ModuleX","v0.1",ModuleStatus.Remove),
                RequestModule("ModuleX","v0.2",ModuleStatus.Add)
              ), "This config")
          ) ~> projectDeployPostRoute ~> check {
            status === OK
            responseAs[ResponseDeploy].user must beEqualTo("testUser")
          }

          Get("/api/project/NonExistentProj/clients") ~> projectGetClients ~> check {
            status === NotFound
          }
        }
      }
    }
  }
  step {
    import java.io._
    new File(".").listFiles().filter(_.getName.startsWith("testdb")).foreach(_.delete())
  }
}
