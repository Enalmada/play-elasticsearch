package controllers

import javax.inject.Inject

import models.Account
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import services.ElasticSearchService

import scala.concurrent.ExecutionContext.Implicits.global

// See https://github.com/t2v/play2-auth for a full authentication/authorization example
class Application @Inject()(implicit val ws: WSClient, val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def home = Action { implicit request =>
    Ok(views.html.index())
  }

  val indexName = "play-elasticsearch"
  val `type` = "account"

  def bulkAccount(command: String, index: String, `type`: String, account: Account) = {
    s"""{ "$command" : { "_index" : "$index", "_type" : "${`type`}", "_id" : "${account.id}" } }\n{ "email" : "${account.email}" , "name" : "${account.name}", "status" : "${account.status}" }\n""".stripMargin
  }


  def fullindex = Action.async { implicit request =>

    // Initial index synonyms
    // AWS ES only lets you do settings synonyms (not file)
    // there are lots of filters that stem at different levels (this example has names no not stemming)
    val settings =
      """
        |{
        |  "settings": {
        |    "index": {
        |      "analysis" : {
        |        "analyzer" : {
        |          "my_search_analyzer" : {
        |            "tokenizer" : "whitespace",
        |            "filter" : ["standard", "asciifolding", "lowercase", "search_synonym"]
        |          }
        |        },
        |        "filter" : {
        |          "search_synonym" : {
        |            "type" : "synonym",
        |            "synonyms" : ["male => chris, bob"]
        |          }
        |        }
        |      }
        |    }
        |  },
        |  "mappings": {
        |    "account": {
        |      "properties": {
        |        "name": {
        |          "type": "string",
        |          "index_analyzer": "standard",
        |          "search_analyzer": "my_search_analyzer"
        |        },
        |        "email": {
        |          "type": "string",
        |          "index_analyzer": "standard",
        |          "search_analyzer": "my_search_analyzer"
        |        },
        |        "status": {
        |          "type": "string",
        |          "index" : "not_analyzed"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin

    val bulkRes = for {
      d <- ElasticSearchService.deleteIndex(indexName)
      c <- ElasticSearchService.createIndex(name = indexName, settings = Some(settings))
      a <- {
        val data = Account.findAll().map { account => bulkAccount("create", indexName, `type`, account) }.mkString("")
        Logger.debug(s"Data: $data")
        ElasticSearchService.bulk(Some(indexName), Some(`type`), data + "\n")
      }
    } yield {
      c
    }

    bulkRes.map { res =>
      Logger.debug(res.body)
      Redirect(routes.Application.home()).flashing("success" -> s"${res.body}")
    }

  }

  def deltaindex = Action.async { implicit request =>

    // Do index which does create or update
    // Change data function to return records that need changing

    val bulkRes = for {
      c <- {
        val data = Account.findAll().map { account => bulkAccount("index", indexName, `type`, account) }.mkString("")
        Logger.debug(s"Data: $data")
        ElasticSearchService.bulk(Some(indexName), Some(`type`), data + "\n")
      }
    } yield {
      c
    }

    bulkRes.map { res =>
      Logger.debug(res.body)
      Redirect(routes.Application.home()).flashing("success" -> s"${res.body}")
    }

  }

  def health = Action.async {

    ElasticSearchService.health() map { serviceResult =>
      Ok(serviceResult.body)
    }

  }

  def createIndex(name: String) = Action.async {
    ElasticSearchService.createIndex(name) map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def deleteIndex(name: String) = Action.async {
    ElasticSearchService.deleteIndex(name) map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def verifyIndex(name: String) = Action.async {
    ElasticSearchService.verifyIndex(name = name) map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def getMapping(index: String, typ: String) = Action.async {
    ElasticSearchService.getMapping(indices = Seq(index), types = Seq(typ)) map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def putMapping(index: String, typ: String) = Action.async {
    ElasticSearchService.putMapping(indices = Seq(index), `type` = typ, body = "{\"bar\": {\"properties\": {\"baz\": {\"type\": \"string\"} } } }") map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def add = Action.async {
    ElasticSearchService.index(index = "foo", `type` = "foo", id = Some("foo"), data = "{\"foo\":\"bar\"}") map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def get = Action.async {
    ElasticSearchService.get("foo", "foo", "foo") map { serviceResult =>
      Ok(serviceResult.body)
    }
  }

  def search = Action.async {
    ElasticSearchService.search(index = "foo") map { serviceResult =>
      Ok(serviceResult.body)
    }
  }


  def index = Action.async {

    ElasticSearchService.get("foo", "foo", "foo") map { serviceResult =>
      Ok(serviceResult.body)
    }

  }


}