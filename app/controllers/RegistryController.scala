package controllers

import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import scala.collection.JavaConverters._

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

import java.nio.file.{Path, Paths, Files, StandardOpenOption}
import java.nio.charset.{StandardCharsets}

object DockerHeaders {
  val REGISTRY_VERSION    = "X-Docker-Registry-Version"
  val REGISTRY_STANDALONE = "X-Docker-Registry-Standalone"
  val TOKEN               = "X-Docker-Token"
  val ENDPOINTS           = "X-Docker-Endpoints"
}
import DockerHeaders._

case class ImgData(id: String, checksum: String)
//implicit val imgDataWrites = new Writes[ImgData]

object RegistryController extends Controller {

  val dataPath = Paths.get("data")
  val imagesPath = dataPath.resolve("images")
  val repoPath = dataPath.resolve("repositories")
  val JSON_SUFFIX = ".json"

  def root() = Action {
    Ok(Json.toJson("Docker Registry"))
  }

  def _ping() = Action {
    Ok(Json.toJson(true)).withHeaders(REGISTRY_VERSION -> "0.6.3")
  }

  def images(repo: String) = Action {
    Files.createDirectories(imagesPath)
    val files = imagesPath.toFile.list().filter(_.endsWith(JSON_SUFFIX))
    Ok(Json.toJson(files.map( (fn) => {Json.obj("id" -> fn.substring(0, fn.length - JSON_SUFFIX.length),  "checksum" -> "foobar")} )))
  }

  def getTags(repo: String) = Action {
    val tagsPath = repoPath.resolve(s"${repo}/tags")
    if (Files.exists(tagsPath)) {
      val files = tagsPath.toFile.list().filter(_.endsWith(JSON_SUFFIX))
      Ok(Json.toJson(files.map( (fn) => {
          val contents = Files.readAllLines(tagsPath.resolve(fn), StandardCharsets.UTF_8).asScala.mkString
          Json.obj(fn.substring(0, fn.length - JSON_SUFFIX.length) -> Json.parse(contents))
        } ).reduce(_ ++ _)))
    } else {
      NotFound(Json.toJson(s"Repository ${repo} does not exist"))
    }
  }

  def putImages(repo: String) = Action {
    // this is the final call from Docker client when pushing an image
    // Docker client expects HTTP status code 204 (No Content) instead of 200 here!
    Status(204)("")
  }

  def putRepo(repo: String) = Action {request =>
    val host: String = request.headers.get("Host").getOrElse("")
      Ok(Json.toJson("PUTPUT"))
      .withHeaders(TOKEN -> "mytok")
      .withHeaders(ENDPOINTS -> host)
  }

  def getImageJson(image: String) = Action {
    val imagePath = imagesPath.resolve(s"${image}.json")
    val layerPath = imagesPath.resolve(s"${image}.layer")
    if (Files.exists(imagePath) && Files.exists(layerPath)) {
      val contents = Files.readAllLines(imagePath, StandardCharsets.UTF_8).asScala.mkString
      Ok(Json.parse(contents))
    } else {
      NotFound(Json.toJson(s"Image JSON (${image}.json) not found"))
    }
  }

  def getAncestry(image: String): Option[List[String]] = {
    var ancestry = List(image)
    var cur = image
    while (true) {
      val imagePath = imagesPath.resolve(s"${cur}.json")
      if (!Files.exists(imagePath)) {
        return None
      }
      val contents = Files.readAllLines(imagePath, StandardCharsets.UTF_8).asScala.mkString
      val data = Json.parse(contents)
      val opt = (data \ "parent").asOpt[String]
      if (opt.isEmpty) {
        return Some(ancestry)
      } else {
        cur = opt.get
        ancestry :+= cur
      }
    }
    return Some(ancestry)
  }

  def getImageAncestry(image: String) = Action {
    val ancestry = getAncestry(image)
    if (ancestry.isEmpty) {
      NotFound(Json.toJson(s"Image JSON (${image}.json) not found"))
    } else {
      Ok(Json.toJson(ancestry))
    }
  }

  def putImageJson(image: String) = Action(BodyParsers.parse.json) { request =>
    Files.createDirectories(imagesPath)
    val contents: String = Json.stringify(request.body)

    Files.write(imagesPath.resolve(s"${image}.json"), contents.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE)
    Ok(Json.toJson("OK"))
  }

  def putImageLayer(image: String) = Action(BodyParsers.parse.temporaryFile) { request =>
    val layerPath = imagesPath.resolve(s"${image}.layer")
    val bodyData = request.body.moveTo(layerPath.toFile)
    Ok(Json.toJson("OK"))
  }

  def getImageLayer(image: String) = Action {
    val layerPath = imagesPath.resolve(s"${image}.layer")
    if (Files.exists(layerPath)) {
      Ok.sendFile(layerPath.toFile)
    } else {
      NotFound("Layer not found")
    }
  }

  def putImageChecksum(image: String) = Action {
    // TODO: do something
    Ok(Json.toJson("OK"))
  }

  def putTag(repo: String, tag:String) = Action(BodyParsers.parse.json) { request =>
    val tagPath = repoPath.resolve(s"${repo}/tags/${tag}.json")
    Files.createDirectories(tagPath.getParent)
    val contents: String = Json.stringify(request.body)
    Files.write(tagPath, contents.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE)
    Ok(Json.toJson("OK"))
  }
}

