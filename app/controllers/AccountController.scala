package controllers

import javax.inject.Inject

import models.Account
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import services.ElasticSearchService
import util.{Page, PageFilter}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// See https://github.com/t2v/play2-auth for a full authentication/authorization example
class AccountController @Inject()(implicit val messagesApi: MessagesApi, val ws: WSClient) extends Controller with I18nSupport {

  def stringOpt(value: String) = Option(value).filter(_.trim.nonEmpty)

  def list(page: Int, sortBy: String, sortOrder: String, query: String, status: String) = Action.async { implicit request =>
    val form: Form[(String, String)] = Form(tuple("query" -> text, "status" -> text)).fill(query, status)
    val pageFilter = PageFilter(page, 20)
    val queryOpt = stringOpt(query)
    val statusOpt = stringOpt(status)
    queryOpt match {
      case Some(q) =>
        // For sort to work you need to explicitly map field types
        // To sort by _id you need to say _uid

        val filter = statusOpt match {
          case Some(f) =>
            s"""
               |         "filter" : {
               |            "term" : {
               |               "status" : "$status"
               |            }
               |         },
             """.stripMargin
          case None => ""
        }

        val eq =
          s"""
             |{
             |   "query" : {
             |      "filtered" : {
             |         $filter
             |         "query" : {
             |            "match" : { "name": "${q.toLowerCase}" }
             |         }
             |      }
             |   },
             |   "from" : ${pageFilter.offset},
             |   "size" : ${pageFilter.pageSize}
             |}
           """.stripMargin.stripMargin.replace("\"id\"", "\"_uid\"")


        Logger.debug(s"Query: $eq")
        ElasticSearchService.search(index = "play-elasticsearch", `type` = Some("account"), query = eq) map { searchResponse =>
          Logger.debug(s"Resp: ${searchResponse.body}")
          val ids: Seq[Int] = (Json.parse(searchResponse.body) \\ "_id").map(_.as[String]).map(_.toInt)
          Logger.debug(s"IDS: $ids")
          val accounts: Seq[Account] = ids.map { id => Account.findById(id).get }
          val currentPage = Page(accounts, pageFilter, hasPrev = false, hasNext = false)
          Ok(views.html.account(currentPage, form, sortBy, sortOrder))
        }
      case None =>
        val currentPage = Page(Account.findAll(), pageFilter, hasPrev = false, hasNext = false)
        Future(Ok(views.html.account(currentPage, form, sortBy, sortOrder)))
    }
  }

}