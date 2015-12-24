package services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

object ElasticSearchService extends ElasticSearch {
  override lazy val AWSACCESSKEYID = play.api.Play.current.configuration.getString("elasticsearch.AwsAccessKeyId").getOrElse("")
  override lazy val AWSSECRETKEY = play.api.Play.current.configuration.getString("elasticsearch.AwsSecretKey").getOrElse("")
  override lazy val REGION = play.api.Play.current.configuration.getString("elasticsearch.AwsRegion").getOrElse("us-east-1")
  lazy val clientUrl = play.api.Play.current.configuration.getString("elasticsearch.client").get
}

trait ElasticSearch {

  def AWSACCESSKEYID = ""

  def AWSSECRETKEY = ""

  def REGION = "us-east-1"

  def clientUrl: String

  lazy val signer: AmazonSignerScala = new AmazonSignerScala(AWSACCESSKEYID, AWSSECRETKEY, REGION, clientUrl.replace("http://", "").replace("https://", ""))

  def headers(method: String, uri: String, queryString: String, data: String): Seq[(String, String)] = {
    if (AWSACCESSKEYID.nonEmpty && AWSSECRETKEY.nonEmpty) {
      signer.getSignedHeaders(method, uri, queryString, data).toSeq
    } else Seq()
  }

  def awsSigning(method: String, uri: String, queryString: Option[String] = None, data: Option[String] = None)(implicit ws: WSClient): Future[WSResponse] = {
    val initial = ws.url(s"$clientUrl$uri?${queryString.getOrElse("")}").withHeaders(headers(method, uri, queryString.getOrElse(""), data.getOrElse("")): _*)
    method match {
      case "POST" =>
        data match {
          case Some(d) => initial.post(d.getBytes(StandardCharsets.UTF_8))
          case None => initial.post("")
        }
      case "PUT" =>
        data match {
          case Some(d) => initial.put(d.getBytes(StandardCharsets.UTF_8))
          case None => initial.put("")
        }
      case "GET" => data match {
        case Some(d) => initial.withBody(d.getBytes(StandardCharsets.UTF_8)).get()
        case None => initial.get()
      }
      case "DELETE" => initial.delete()
      case "HEAD" => initial.head()
    }
  }

  /**
    * Close the index (necessary to set certain settings on index)
    *
    * @param index
    * @param ws
    */
  def close(index: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("POST", s"/$index/_close")
  }

  /**
    * Open the index
    *
    * @param index index to open
    * @param ws
    */
  def open(index: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("POST", s"/$index/_open")
  }

  /**
    * Use the bulk API to perform many index/delete operations in a single call.
    *
    * @param index The optional index name.
    * @param type  The optional type.
    * @param data  The operations to perform as described by the [[http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/docs-bulk.html ElasticSearch Bulk API]].
    */
  def bulk(index: Option[String] = None, `type`: Option[String] = None, data: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("POST", s"/${index.map(i => i + "/").getOrElse("")}${`type`.map(i => i + "/").getOrElse("")}_bulk", data = Some(data))
  }

  /**
    * Request a count of the documents matching a query.
    *
    * @param indices A sequence of index names for which mappings will be fetched.
    * @param types   A sequence of types for which mappings will be fetched.
    * @param query   The query to count documents from.
    */
  def count(indices: Seq[String], types: Seq[String], query: Option[String] = None)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("GET", s"/${indices.mkString(",")}/${types.mkString(",")}/_count", data = query)
  }

  /**
    * Create aliases.
    *
    * @param actions A String of JSON containing the actions to be performed. This string will be placed within the actions array passed
    *
    *                As defined in the [[http://www.elasticsearch.org/guide/reference/api/admin-indices-aliases/ ElasticSearch Admin Indices API]] this
    *                method takes a string representing a list of operations to be performed. Remember to
    *                val actions = """{ "add": { "index": "index1", "alias": "alias1" } }, { "add": { "index": "index2", "alias": "alias2" } }"""
    */
  def createAlias(actions: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("POST", s"/_aliases/", data = Some("""{ "actions": [ """ + actions + """ ] }"""))
  }

  /**
    * Create an index, optionally using the supplied settings.
    *
    * @param name     The name of the index.
    * @param settings Optional settings
    */
  def createIndex(name: String, settings: Option[String] = None)(implicit ws: WSClient) = {
    awsSigning("PUT", s"/$name/", data = settings)
  }

  /**
    * Delete a document from the index.
    *
    * @param index The name of the index.
    * @param type  The type of document to delete.
    * @param id    The ID of the document.
    */
  def delete(index: String, `type`: String, id: String)(implicit ws: WSClient): Future[WSResponse] = {
    // XXX Need to add parameters: version, routing, parent, replication,
    // consistency, refresh
    awsSigning("DELETE", s"/$index/${`type`}/$id/")
  }

  /**
    * Delete an index alias.
    *
    * @param index The name of the index.
    * @param alias The name of the alias.
    */
  def deleteAlias(index: String, alias: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("DELETE", s"/$index/_alias/$alias")
  }

  /**
    * Delete documents that match a query.
    * Not supported in > 2 anymore
    *
    * @param index An index names for which mappings will be fetched.
    * @param type  A type for which mappings will be fetched.
    * @param query The query to count documents from.
    */
  /*
  {"took":1,"timed_out":false,"_shards":{"total":5,"successful":5,"failed":0},"hits":{"total":1,"max_score":1.0,
  "hits":[{"_index":"foo","_type":"foo","_id":"foo2","_score":1.0,"_source":{"foo":"bar"}}]}}
   */
  def deleteByQuery(index: String, `type`: String, query: String)(implicit ws: WSClient): Future[WSResponse] = {
    // XXX Need to add parameters: df, analyzer, default_operator
    //ws.url(s"$clientUrl/${indices.mkString(",")}/${types.mkString(",")}/_query").withBody(query.getBytes(StandardCharsets.UTF_8)).delete()
    import scala.concurrent.ExecutionContext.Implicits.global
    for {
      responseOne <- search(index, query, Some(`type`))
      responseTwo <- {
        val ids = Json.parse(responseOne.body) \\ "_id"
        val data = ids.map { id => s"""{ "delete" : { "_index" : "$index", "_type" : "${`type`}", "_id" : $id } }\n""" }.mkString("")
        bulk(Some(index), Some(`type`), data + "\n")
      }
    } yield responseTwo

  }


  /**
    * Delete an index
    *
    * @param name The name of the index to delete.
    */
  def deleteIndex(name: String)(implicit ws: WSClient) = {
    awsSigning("DELETE", s"/$name/")
  }

  /**
    * Delete a warmer.
    *
    * @param index The index of the warmer.
    * @param name  The name of the warmer.
    */
  def deleteWarmer(index: String, name: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("DELETE", s"/$index/_warmer/$name")
  }

  /**
    * Explain a query and document.
    *
    * @param index The name of the index.
    * @param type  The optional type document to explain.
    * @param id    The ID of the document.
    * @param query The query.
    */
  def explain(index: String, `type`: String, id: String, query: String)(implicit ws: WSClient): Future[WSResponse] = {
    // XXX Lots of params to add
    awsSigning("POST", s"/$index/${`type`}/$id/_explain", data = Some(query))
  }

  /**
    * Get a document by ID.
    *
    * @param index The name of the index.
    * @param type  The type of the document.
    * @param id    The id of the document.
    */
  def get(index: String, `type`: String, id: String)(implicit ws: WSClient) = {
    awsSigning("GET", s"/$index/${`type`}/$id")
  }

  /**
    * Get multiple documents by ID.
    *
    * @param index         The optional name of the index.
    * @param type          The optional type of the document.
    * @param query         The query to execute.
    * @param uriParameters The query uri parameters.
    */
  def mget(index: Option[String], `type`: Option[String], query: String,
           uriParameters: MGetUriParameters = MGetUriParameters.withDefaults)(implicit ws: WSClient): Future[WSResponse] = {

    val myParams: String = Seq("_source" -> Option(uriParameters.sourceFields.map(_.trim).filter(_.nonEmpty).mkString(",")))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    awsSigning("POST", s"/${index.map(i => i + "/").getOrElse("")}${`type`.map(i => i + "/").getOrElse("")}_mget", queryString = Some(myParams), data = Some(query))

  }

  /**
    * Get aliases for indices.
    *
    * @param index Optional name of an index. If no index is supplied, then the query will check all indices.
    * @param query The name of alias to return in the response. Like the index option, this option supports wildcards and the option the specify multiple alias names separated by a comma.
    */
  def getAliases(index: Option[String], query: String = "*")(implicit ws: WSClient): Future[WSResponse] = {
    index match {
      case Some(i) => awsSigning("GET", s"/$i/_alias/$query")
      case None => awsSigning("GET", s"/_alias/$query")
    }
  }

  /**
    * Get the mappings for a list of indices.
    *
    * @param indices A sequence of index names for which mappings will be fetched.
    * @param types   A sequence of types for which mappings will be fetched.
    */
  def getMapping(indices: Seq[String], types: Seq[String])(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("GET", s"/${indices.mkString(",")}/_mapping/${types.mkString(",")}")
  }

  /**
    * Get the settings for a list of indices.
    *
    * @param indices A sequence of index names for which settings will be fetched.
    */
  def getSettings(indices: Seq[String])(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("GET", s"/${indices.mkString(",")}/_settings")
  }

  /**
    * Get matching warmers.
    *
    * @param index Name of index to check.
    * @param name  Expression to match warmer.
    */
  def getWarmers(index: String, name: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("GET", s"/$index/_warmer/$name")
  }

  /**
    * Query ElasticSearch for it's health.
    *
    * @param indices                 Optional list of index names. Defaults to empty.
    * @param level                   Can be one of cluster, indices or shards. Controls the details level of the health information returned.
    * @param waitForStatus           One of green, yellow or red. Will wait until the status of the cluster changes to the one provided, or until the timeout expires.
    * @param waitForRelocatingShards A number controlling to how many relocating shards to wait for.
    * @param waitForNodes            The request waits until the specified number N of nodes is available. Is a string because >N and ge(N) type notations are allowed.
    * @param timeout                 A time based parameter controlling how long to wait if one of the waitForXXX are provided.
    */
  def health(indices: Seq[String] = Seq.empty[String], level: Option[String] = None,
             waitForStatus: Option[String] = None, waitForRelocatingShards: Option[String] = None,
             waitForNodes: Option[String] = None, timeout: Option[String] = None)(implicit ws: WSClient) = {

    val params = Seq("level" -> level, "wait_for_status" -> waitForStatus, "wait_for_relocation_shards" -> waitForRelocatingShards, "wait_for_nodes" -> waitForNodes, "timeout" -> timeout)
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    awsSigning("GET", s"/_cluster/health/${indices.mkString(",")}", queryString = Some(params))

  }


  /**
    * Query ElasticSearch Stats. Parameters to enable non-default stats as desired.
    *
    * @param indices Optional list of index names. Defaults to empty.
    * @param clear   Clears all the flags (first).
    * @param refresh refresh stats.
    * @param flush   flush stats.
    * @param merge   merge stats.
    * @param warmer  Warmer statistics.
    */
  def stats(indices: Seq[String] = Seq(), clear: Boolean = false, refresh: Boolean = false, flush: Boolean = false, merge: Boolean = false, warmer: Boolean = false)(implicit ws: WSClient): Future[WSResponse] = {

    val params = Seq("clear" -> Some(clear.toString), "refresh" -> Some(refresh.toString), "flush" -> Some(flush.toString), "merge" -> Some(merge.toString), "warmer" -> Some(warmer.toString))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    indices match {
      case Nil => awsSigning("GET", s"/_stats")
      case _ => awsSigning("GET", s"/${indices.mkString(",")}/_stats")
    }

  }

  /**
    * Index a document.
    *
    * Adds or updates a JSON documented of the specified type in the specified
    * index.
    *
    * @param index   The index in which to place the document
    * @param type    The type of document to be indexed
    * @param id      The id of the document. Specifying None will trigger automatic ID generation by ElasticSearch
    * @param data    The document to index, which should be a JSON string
    * @param refresh If true then ElasticSearch will refresh the index so that the indexed document is immediately searchable.
    */
  def index(index: String, `type`: String, id: Option[String] = None, data: String,
            refresh: Boolean = false)(implicit ws: WSClient): Future[WSResponse] = {

    // XXX Need to add parameters: version, op_type, routing, parents & children,
    // timestamp, ttl, percolate, timeout, replication, consistency
    val idReq: String = id.map(x => s"/$x").getOrElse("")
    val params = Seq("refresh" -> Some(refresh.toString))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    id.map({ i => awsSigning("PUT", s"/$index/${`type`}$idReq", queryString = Some(params), data = Some(data)) })
      .getOrElse(awsSigning("POST", s"/$index/${`type`}$idReq", queryString = Some(params), data = Some(data)))

  }

  /**
    * Put a mapping for a list of indices.
    *
    * @param indices        A sequence of index names for which mappings will be added.
    * @param type           The type name to which the mappings will be applied.
    * @param body           The mapping.
    * @param updateAllTypes When merge has conflicts overwrite mapping anyway, default false.
    */
  def putMapping(indices: Seq[String], `type`: String, body: String, updateAllTypes: Boolean = false, ignoreConflicts: Boolean = false)(implicit ws: WSClient): Future[WSResponse] = {
    val myParams = if (updateAllTypes) "update_all_types=true"
    else if (ignoreConflicts) "ignore_conflicts=true"
    else ""

    awsSigning("PUT", s"/${indices.mkString(",")}/_mapping/${`type`}", queryString = Some(myParams), data = Some(body))

  }

  /**
    * Put settings for a list of indices.
    *
    * @param indices A sequence of index names for which settings will be updated.
    * @param body    The settings.
    */
  def putSettings(indices: Seq[String], body: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("PUT", s"/${indices.mkString(",")}/_settings", data = Some(body))
  }

  /**
    * Add a warmer.
    *
    * @param index The index to add the warmer.
    * @param name  The name of the warmer.
    * @param body  The warmer content.
    */
  def putWarmer(index: String, name: String, body: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("PUT", s"/$index/_warmer/$name", data = Some(body))
  }

  /**
    * Refresh an index.
    *
    * Makes all operations performed since the last refresh available for search.
    *
    * @param index Name of the index to refresh
    */
  def refresh(index: String)(implicit ws: WSClient) = {
    awsSigning("POST", s"/$index/_refresh")
  }

  /**
    * Search for documents.
    *
    * @param index         The index to search
    * @param type          The optional type of document to search
    * @param query         The query to execute.
    * @param uriParameters The query uri parameters.
    */
  def search(index: String, query: String = "{\"query\": { \"match_all\": {} }}", `type`: Option[String] = None,
             uriParameters: SearchUriParameters = SearchUriParameters.withDefaults)(implicit ws: WSClient): Future[WSResponse] = {

    // TODO: Figure out how to do optional parameters right
    val params = Seq("scroll" -> uriParameters.scroll, "search_type" -> uriParameters.searchType.map(_.parameterValue))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    val uri = s"/$index/${`type`.map(t => t + "/").getOrElse("")}_search"
    awsSigning("POST", uri, queryString = Some(params), data = Some(query))

  }

  /**
    * Scrolls for more documents.
    *
    * @param scroll   The scroll parameter which tells Elasticsearch how long it should keep the “search context” alive
    * @param scrollId The _scroll_id value returned in the response to the previous search or scroll request
    */
  def scroll(scroll: String, scrollId: String)(implicit ws: WSClient): Future[WSResponse] = {

    val params = Seq("scroll" -> Some(scroll), "scroll_id" -> Some(scrollId))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    awsSigning("GET", s"/_search/scroll", queryString = Some(params))

  }

  /**
    * Suggest completions based on analyzed documents.
    *
    * @param index The index to search
    * @param query The query to execute.
    */
  def suggest(index: String, query: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("POST", s"/$index/_suggest", data = Some(query))
  }

  /**
    * Get multiple documents by ID.
    *
    * @param index The optional name of the index.
    * @param type  The optional type of the document.
    * @param query The query to execute.
    */
  def msearch(index: Option[String] = None, `type`: Option[String] = None, query: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("GET", s"/${index.map(i => i + "/").getOrElse("")}${`type`.map(i => i + "/").getOrElse("")}_msearch", data = Some(query))
  }

  /**
    * Validate a query.
    *
    * @param index   The name of the index.
    * @param type    The optional type of document to validate against.
    * @param query   The query.
    * @param explain If true, then the response will contain more detailed information about the query.
    */
  def validate(index: String, `type`: Option[String] = None, query: String, explain: Boolean = false)(implicit ws: WSClient): Future[WSResponse] = {

    val params = Seq("explain" -> Some(explain.toString))
      .map(x => (x._1, x._2.getOrElse(""))).filter(_._2.nonEmpty).map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")

    awsSigning("POST", s"/$index/${`type`.map(i => i + "/").getOrElse("")}_validate/query", queryString = Some(params), data = Some(query))

  }

  /**
    * Verify that an index exists.
    *
    * @param name The name of the index to verify.
    */
  def verifyIndex(name: String)(implicit ws: WSClient) = {
    awsSigning("HEAD", s"/$name")
  }


  /**
    * Verify that a type exists.
    *
    * @param index The name of the index to verify.
    * @param type  The name of the document type to verify.
    */
  def verifyType(index: String, `type`: String)(implicit ws: WSClient): Future[WSResponse] = {
    awsSigning("HEAD", s"/$index/${`type`}")
  }


}


sealed trait SearchType {
  def parameterValue: String
}

case object DfsQueryThenFetch extends SearchType {
  val parameterValue = "dfs_query_then_fetch"
}

case object DfsQueryAndFetch extends SearchType {
  val parameterValue = "dfs_query_and_fetch"
}

case object QueryThenFetch extends SearchType {
  val parameterValue = "query_then_fetch"
}

case object QueryAndFetch extends SearchType {
  val parameterValue = "query_and_fetch"
}

case object Count extends SearchType {
  val parameterValue = "count"
}

case object Scan extends SearchType {
  val parameterValue = "scan"
}

case class SearchUriParameters(searchType: Option[SearchType] = None,
                               scroll: Option[String] = None)

object SearchUriParameters {
  val withDefaults = SearchUriParameters(None, None)
}


case class MGetUriParameters(sourceFields: Seq[String] = Seq.empty)

object MGetUriParameters {
  val withDefaults = MGetUriParameters(Nil)
}