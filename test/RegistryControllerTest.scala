import org.specs2.mutable._
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.FakeRequest
import controllers.DockerHeaders._
import TestUtils._

class RegistryControllerTest extends Specification {
  "RegistryController" should {

    "return server name" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/")).get

      status(res) must be equalTo OK
    }

    "respond to _ping" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/_ping")).get

      val reportedDockerRegistryVersion = header(REGISTRY_VERSION, res).getOrElse("UNKNOWN")

      reportedDockerRegistryVersion must be equalTo "0.6.3"

      status(res) must be equalTo OK
      contentAsJson(res) match {
        case JsBoolean(value) => value must be equalTo true
        case other => sys.error(s"Unexpected result type: $other")
      }
    }

    "fail on getting tags for non-existing repository" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/repositories/NON/EXISTING/tags")).get
      status(res) must be equalTo NOT_FOUND
    }

    "fail on getting JSON image for non-existing image" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/images/NONEXISTING/json")).get
      status(res) must be equalTo NOT_FOUND
    }

    "get list of images" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/repositories/some/repo/images")).get
      status(res) must be equalTo OK
    }

    "fail on getting ancestry for non-existing image" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/images/NONEXISTING/ancestry")).get
      status(res) must be equalTo NOT_FOUND
    }

    "fail on getting layer for non-existing image" in new WithTestApplication {
      val res = route(FakeRequest(GET, "/v1/images/NONEXISTING/layer")).get
      status(res) must be equalTo NOT_FOUND
    }

    "push and verify layer" in new WithTestApplication {
      var res = route(FakeRequest(PUT, "/v1/images/abc123/layer").withTextBody("some data")).get
      status(res) must be equalTo OK

      res = route(FakeRequest(GET, "/v1/images/abc123/layer")).get
      contentAsString(res) must be equalTo "some data"
    }

    "push and verify tag" in new WithTestApplication {
      var res = route(FakeRequest(PUT, "/v1/repositories/test/repo1/tags/1.0").withJsonBody(JsString("abc123-image-id"))).get
      status(res) must be equalTo OK
      contentAsJson(res) must be equalTo JsString("OK")

      res = route(FakeRequest(GET, "/v1/repositories/test/repo1/tags")).get
      contentAsJson(res) must be equalTo Json.parse("""{"1.0": "abc123-image-id"}""")
    }

    "fail on pushing a repo without Host header" in new WithTestApplication {
      val res = route(FakeRequest(PUT, "/v1/repositories/test/repo/")).get
      status(res) must be equalTo BAD_REQUEST
    }

    "simulate Docker client push behavior" in new WithTestApplication {
      val layers = Seq(
        "511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158" -> """{"id":"511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158","comment":"Imported from -","created":"2013-06-13T14:03:50.821769-07:00","container_config":{"Hostname":"","User":"","Memory":0,"MemorySwap":0,"CpuShares":0,"AttachStdin":false,"AttachStdout":false,"AttachStderr":false,"PortSpecs":null,"Tty":false,"OpenStdin":false,"StdinOnce":false,"Env":null,"Cmd":null,"Dns":null,"Image":"","Volumes":null,"VolumesFrom":""},"docker_version":"0.4.0","architecture":"x86_64"}""",
        "df7546f9f060a2268024c8a230d8639878585defcc1bc6f79d2728a13957871b" ->
        """{"id":"df7546f9f060a2268024c8a230d8639878585defcc1bc6f79d2728a13957871b","parent":"511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158","created":"2014-10-01T20:46:07.263351912Z","container":"2147a17cb1b2d6626ed78e5ef8ba4c71ce82c884bc3b57ab01e6114ff357cea4","container_config":{"Hostname":"2147a17cb1b2","Domainname":"","User":"","Memory":0,"MemorySwap":0,"CpuShares":0,"Cpuset":"","AttachStdin":false,"AttachStdout":false,"AttachStderr":false,"PortSpecs":null,"ExposedPorts":null,"Tty":false,"OpenStdin":false,"StdinOnce":false,"Env":["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],"Cmd":["/bin/sh","-c","#(nop) MAINTAINER J..r..me Petazzoni \u003cjerome@docker.com\u003e"],"Image":"511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158","Volumes":null,"WorkingDir":"","Entrypoint":null,"NetworkDisabled":false,"OnBuild":[]},"docker_version":"1.2.0","author":"J..r..me Petazzoni \u003cjerome@docker.com\u003e","config":{"Hostname":"2147a17cb1b2","Domainname":"","User":"","Memory":0,"MemorySwap":0,"CpuShares":0,"Cpuset":"","AttachStdin":false,"AttachStdout":false,"AttachStderr":false,"PortSpecs":null,"ExposedPorts":null,"Tty":false,"OpenStdin":false,"StdinOnce":false,"Env":["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],"Cmd":null,"Image":"511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158","Volumes":null,"WorkingDir":"","Entrypoint":null,"NetworkDisabled":false,"OnBuild":[]},"architecture":"amd64","os":"linux","Size":0}""",
        "4986bf8c15363d1c5d15512d5266f8777bfba4974ac56e3270e7760f6f0a8125" ->
        """{"id":"4986bf8c15363d1c5d15512d5266f8777bfba4974ac56e3270e7760f6f0a8125","parent":"df7546f9f060a2268024c8a230d8639878585defcc1bc6f79d2728a13957871b","created":"2014-12-31T22:23:56.943403668Z","container":"83dcf36ad1042b90f4ea8b2ebb60e61b2f1a451a883e04b388be299ad382b259","container_config":{"Hostname":"7f674915980d","Domainname":"","User":"","Memory":0,"MemorySwap":0,"CpuShares":0,"Cpuset":"","AttachStdin":false,"AttachStdout":false,"AttachStderr":false,"PortSpecs":null,"ExposedPorts":null,"Tty":false,"OpenStdin":false,"StdinOnce":false,"Env":["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],"Cmd":["/bin/sh","-c","#(nop) CMD [/bin/sh]"],"Image":"ea13149945cb6b1e746bf28032f02e9b5a793523481a0a18645fc77ad53c4ea2","Volumes":null,"WorkingDir":"","Entrypoint":null,"NetworkDisabled":false,"MacAddress":"","OnBuild":[]},"docker_version":"1.4.1","author":"J..r..me Petazzoni \u003cjerome@docker.com\u003e","config":{"Hostname":"7f674915980d","Domainname":"","User":"","Memory":0,"MemorySwap":0,"CpuShares":0,"Cpuset":"","AttachStdin":false,"AttachStdout":false,"AttachStderr":false,"PortSpecs":null,"ExposedPorts":null,"Tty":false,"OpenStdin":false,"StdinOnce":false,"Env":["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"],"Cmd":["/bin/sh"],"Image":"ea13149945cb6b1e746bf28032f02e9b5a793523481a0a18645fc77ad53c4ea2","Volumes":null,"WorkingDir":"","Entrypoint":null,"NetworkDisabled":false,"MacAddress":"","OnBuild":[]},"architecture":"amd64","os":"linux","checksum":"tarsum.dev+sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855","Size":0}""")

      val json =
        Json.arr(
          Json.obj("id" -> "511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158"),
          Json.obj("id" -> "df7546f9f060a2268024c8a230d8639878585defcc1bc6f79d2728a13957871b"),
          Json.obj("id" -> "4986bf8c15363d1c5d15512d5266f8777bfba4974ac56e3270e7760f6f0a8125"),
          Json.obj("id" -> "511136ea3c5a64f264b78b5433614aec563103b4d4702f3ba7d4d2698e22c158", "Tag" -> "1.0")
        )

      var res = route(FakeRequest(PUT, "/v1/repositories/test/repo/")
        .withHeaders("Host" -> "localhost")
        .withJsonBody(json))
        .get
      status(res) must be equalTo 200

      // push every image layer as the Docker client would do it
      layers foreach { case (id, json) =>

        res = route(FakeRequest(GET, s"/v1/images/${id}/json")).get
        status(res) must be equalTo NOT_FOUND

        res = route(FakeRequest(PUT, s"/v1/images/${id}/json").withJsonBody(Json.parse(json))).get
        contentAsJson(res) must be equalTo JsString("OK")

        res = route(FakeRequest(PUT, s"/v1/images/${id}/layer").withTextBody("some data")).get
        status(res) must be equalTo OK

        res = route(FakeRequest(PUT, s"/v1/images/${id}/checksum")).get
        status(res) must be equalTo OK
        contentAsJson(res) must be equalTo JsString("OK")
      }

      res = route(FakeRequest(PUT, "/v1/repositories/test/repo/tags/1.0").withJsonBody(JsString(layers.last._1))).get
      status(res) must be equalTo OK
      contentAsJson(res) must be equalTo JsString("OK")

      res = route(FakeRequest(PUT, "/v1/repositories/test/repo/images").withJsonBody(Json.parse("[]"))).get
      status(res) must be equalTo NO_CONTENT

      // End of Docker push

      // ToDo: verify by simulating complete Docker pull flow
      res = route(FakeRequest(GET, s"/v1/images/${layers.head._1}/ancestry")).get
      status(res) must be equalTo OK
      contentAsJson(res) must be equalTo JsArray(layers.take(1).map{x => JsString(x._1)})

      res = route(FakeRequest(GET, s"/v1/images/${layers.last._1}/ancestry")).get
      status(res) must be equalTo OK
      contentAsJson(res) must be equalTo JsArray(layers.map{x => JsString(x._1)}.reverse )
    }
  }
}
