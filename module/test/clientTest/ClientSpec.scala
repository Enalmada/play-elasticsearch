package clientTest


import argonaut.Parse
import org.specs2.matcher.JsonMatchers
import play.api.libs.ws.WSClient
import play.api.test.{FakeApplication, PlaySpecification}
import services.{ElasticSearch, MGetUriParameters, Scan, SearchUriParameters}

import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable}

class ClientSpec extends PlaySpecification with JsonMatchers {

  // https://www.codatlas.com/github.com/playframework/playframework/HEAD/documentation/manual/working/scalaGuide/main/cache/code/ScalaCache.scala?line=40
  def withWs[T](block: WSClient => T) = {
    val app = FakeApplication()
    running(app)(block(app.injector.instanceOf[WSClient]))
  }

  /**
    * Simple utility for dealing with Futures. This method waits for futures to complete, so you can test everything
    * sequentially.
    *
    * @param awaitable the code returning a future to wait for
    * @tparam T the result type
    * @return the result
    */
  def awaitUtil[T](awaitable: Awaitable[T], awaitTime: Duration = Duration(30, "seconds")): T = Await.result(awaitable, awaitTime)

  sequential

  val server = new ElasticsearchEmbeddedServer

  step {
    server.start()
  }


  "Client" should {

    object TestSearch extends ElasticSearch {
      override val clientUrl = s"http://localhost:${server.httpPort}"
    }

    def createIndex(index: String)(implicit ws: WSClient) = {
      awaitUtil(TestSearch.createIndex(name = index)).body must contain("acknowledged")
      // Note: we cannot wait for green as the index, by default, will have a missing replica.
      // TODO: When defect #25 is fixed, we can specify that the index should have no replicas, and then we can wait for "green".
      awaitUtil(TestSearch.health(List(index), waitForStatus = Some("yellow")))
    }

    def index(index: String, `type`: String, id: String, data: Option[String] = None)(implicit ws: WSClient) = {
      awaitUtil(TestSearch.index(
        id = Some(id),
        index = index, `type` = `type`,
        data = data.getOrElse(s"""{"id":"$id"}"""), refresh = true
      )).body must contain("\"_version\"")
    }

    def deleteIndex(index: String)(implicit ws: WSClient) = {
      awaitUtil(TestSearch.deleteIndex(index)).body must contain("\"acknowledged\"")
    }

    "fail usefully" in withWs { implicit ws =>
      awaitUtil(TestSearch.verifyIndex("foobarbaz")).status must beEqualTo(404)
    }

    "create and delete indexes" in withWs { implicit ws =>
      createIndex("foo")
      awaitUtil(TestSearch.verifyIndex("foo"))
      deleteIndex("foo")
    }


    "create and delete aliases" in withWs { implicit ws =>
      createIndex("foo")
      awaitUtil(TestSearch.createAlias(actions = """{ "add": { "index": "foo", "alias": "foo-write" }}""")).body must contain("acknowledged")
      awaitUtil(TestSearch.getAliases(index = Some("foo"))).body must contain("foo-write")
      awaitUtil(TestSearch.deleteAlias(index = "foo", alias = "foo-write")).body must contain("acknowledged")
      deleteIndex("foo")
    }




    "put, get, and delete warmer" in withWs { implicit ws =>


      createIndex("trogdor")

      Thread.sleep(1000) //ES needs some time to make the index first
      awaitUtil(TestSearch.putWarmer(index = "trogdor", name = "fum", body = """{"query": {"match_all":{}}}""")).body must contain("acknowledged")

      awaitUtil(TestSearch.getWarmers("trogdor", "fum")).body must contain("fum")

      awaitUtil(TestSearch.deleteWarmer("trogdor", "fum")).body must contain("acknowledged")

      awaitUtil(TestSearch.getWarmers("trogdor", "fum")).body must not contain ("fum")

      deleteIndex("trogdor")
    }

    "index, fetch, and delete a document" in withWs { implicit ws =>


      awaitUtil(TestSearch.index(
        id = Some("foo"),
        index = "foo", `type` = "foo",
        data = "{\"foo\":\"bar₡\"}", refresh = true
      )).body must contain("\"_version\"")

      awaitUtil(TestSearch.get("foo", "foo", "foo")).body must contain("\"bar₡\"")
      awaitUtil(TestSearch.delete("foo", "foo", "foo")).body must contain("\"found\"")

      deleteIndex("foo")
    }


    "get multiple documents" in withWs { implicit ws =>

      "with index and type" in withWs { implicit ws =>

        index(index = "foo", `type` = "bar", id = "1")
        index(index = "foo", `type` = "bar", id = "2")
        Thread.sleep(1000) //ES needs some time to make the index first

        awaitUtil(TestSearch.mget(index = Some("foo"), `type` = Some("bar"), query =
          """
            |{
            | "ids" : ["1", "2"]
            |}
          """.stripMargin)).body must /("docs") /# 0 / ("found" -> "true") and /("docs") /# 1 / ("found" -> "true")
        //must contain("\"found\":true")
        deleteIndex("foo")
      }



      "with index, type and some specified fields using source uri param" in withWs { implicit ws =>


        index(index = "foo", `type` = "bar", id = "1",
          data = Some("{\"name\":\"Jon Snow\", \"age\":18, \"address\":\"Winterfell\"}"))
        index(index = "foo", `type` = "bar", id = "2",
          data = Some("{\"name\":\"Arya Stark\", \"age\":14, \"address\":\"Winterfell\"}"))

        val body = awaitUtil(TestSearch.mget(index = Some("foo"), `type` = Some("bar"), query =
          """
            |{
            | "ids" : ["1", "2"]
            |}
          """.stripMargin, MGetUriParameters(Seq("name ", "", " address")))).body

        body must /("docs") /# 0 / "_source" / ("name" -> "Jon Snow") and
          /("docs") /# 0 / "_source" / ("address" -> "Winterfell") and
          /("docs") /# 1 / "_source" / ("name" -> "Arya Stark") and
          /("docs") /# 1 / "_source" / ("address" -> "Winterfell") and
          not contain "age"

        deleteIndex("foo")
      }

      "with index" in withWs { implicit ws =>


        index(index = "foo", `type` = "bar1", id = "1")
        index(index = "foo", `type` = "bar2", id = "2")

        awaitUtil(TestSearch.mget(index = Some("foo"), `type` = None, query =
          """{
            |  "docs" : [
            |    {
            |      "_type" : "bar1",
            |      "_id" : "1"
            |    },
            |    {
            |      "_type" : "bar2",
            |      "_id" : "2"
            |    }
            |  ]
            |}
          """.stripMargin)).body must /("docs") /# 0 / ("found" -> "true") and
          /("docs") /# 1 / ("found" -> "true")

        deleteIndex("foo")
      }

      "without index and type" in withWs { implicit ws =>


        index(index = "foo1", `type` = "bar1", id = "1")
        index(index = "foo2", `type` = "bar2", id = "2")

        awaitUtil(TestSearch.mget(index = None, `type` = None, query =
          """{
            |  "docs" : [
            |    {
            |      "_index" : "foo1",
            |      "_type" : "bar1",
            |      "_id" : "1"
            |    },
            |    {
            |      "_index" : "foo2",
            |      "_type" : "bar2",
            |      "_id" : "2"
            |    }
            |  ]
            |}
          """.stripMargin)).body must /("docs") /# 0 / ("found" -> "true") and
          /("docs") /# 1 / ("found" -> "true")

        deleteIndex("foo1")
        deleteIndex("foo2")
      }

    }

    "search for a document" in withWs { implicit ws =>

      index(index = "foo", `type` = "foo", id = "foo2", data = Some("{\"foo\":\"bar\"}"))

      awaitUtil(TestSearch.search("foo", "{\"query\": { \"match_all\": {} } }")).body must contain("\"foo2\"")

      // No query
      awaitUtil(TestSearch.count(Seq("foo"), Seq("foo"))).body must contain("\"count\"")

      awaitUtil(TestSearch.count(Seq("foo"), Seq("foo"), query = Some("{\"query\": { \"match_all\": {} } }"))).body must contain("\"count\"")

      awaitUtil(TestSearch.delete("foo", "foo", "foo2")).body must contain("\"found\"")

      deleteIndex("foo")
    }

    "search with search_type and scroll parameters" in withWs { implicit ws =>


      index(index = "foo", `type` = "bar", id = "bar1", data = Some("{\"abc\":\"def\"}"))

      awaitUtil(TestSearch.search("foo", "{\"query\": { \"match_all\": {} } }", Some("bar"),
        SearchUriParameters(scroll = Some("1m")))).body must contain("\"bar1\"")

      awaitUtil(TestSearch.search("foo", "{\"query\": { \"match_all\": {} } }", Some("bar"),
        SearchUriParameters(scroll = Some("1m"), searchType = Some(Scan)))).body must contain("\"_scroll_id\"")

      deleteIndex("foo")
    }

    "scroll" in withWs { implicit ws =>

      def extractScrollId(responseBody: String): Option[String] = for {
        parsed <- Parse.parseOption(responseBody)
        scrollId <- parsed.fieldOrEmptyString("_scroll_id").string
      } yield scrollId

      def extractResultIds(responseBody: String): Option[List[String]] = for {
        parsed <- Parse.parseOption(responseBody)
        outerHits <- parsed.field("hits")
        innerHits <- outerHits.fieldOrEmptyArray("hits").array
      } yield innerHits.map(_.fieldOrEmptyString("_id").stringOr(""))



      index(index = "foo", `type` = "bar", id = "bar1", data = Some("{\"abc\":\"def1\"}"))
      index(index = "foo", `type` = "bar", id = "bar2", data = Some("{\"abc\":\"def2\"}"))
      index(index = "foo", `type` = "bar", id = "bar3", data = Some("{\"abc\":\"def3\"}"))

      val firstSearchResponse = awaitUtil(TestSearch.search("foo",
        """{"query": { "match_all": {} }, "size": 2 }""", Some("bar"),
        SearchUriParameters(scroll = Some("1m")))).body

      val fistScrollIdOption = extractScrollId(firstSearchResponse)
      fistScrollIdOption must beSome

      val firstResultIds = extractResultIds(firstSearchResponse)
      firstResultIds must_== Some(List("bar1", "bar2"))

      val secondSearchResponse = awaitUtil(TestSearch.scroll("1m", fistScrollIdOption.get)).body

      val secondScrollIdOption = extractScrollId(secondSearchResponse)
      secondScrollIdOption must beSome

      val secondResultIds = extractResultIds(secondSearchResponse)
      secondResultIds must_== Some(List("bar3"))

      deleteIndex("foo")
    }

    "multi-search" in withWs { implicit ws =>



      "with index and type" in withWs { implicit ws =>
        index(index = "foo", `type` = "bar", id = "1", data = Some( """{"name": "Fred Smith"}"""))
        index(index = "foo", `type` = "bar", id = "2", data = Some( """{"name": "Mary Jones"}"""))

        awaitUtil(TestSearch.msearch(index = Some("foo"), `type` = Some("bar"), query =
          """
            |{}
            |{"query" : {"match" : {"name": "Fred"}}}
            |{}
            |{"query" : {"match" : {"name": "Jones"}}}
          """.stripMargin), Duration(1, "second")).body must
          /("responses") /# 0 / ("hits") / ("total" -> "1.0") and
          /("responses") /# 1 / ("hits") / ("total" -> "1.0")

        deleteIndex("foo")
      }

      "with index" in withWs { implicit ws =>
        index(index = "foo", `type` = "bar1", id = "1", data = Some( """{"name": "Fred Smith"}"""))
        index(index = "foo", `type` = "bar2", id = "2", data = Some( """{"name": "Mary Jones"}"""))

        awaitUtil(TestSearch.msearch(index = Some("foo"), query =
          """
            |{"type": "bar1"}
            |{"query" : {"match" : {"name": "Fred"}}}
            |{"type": "bar2"}
            |{"query" : {"match" : {"name": "Jones"}}}
          """.stripMargin), Duration(1, "second")).body must
          /("responses") /# 0 / ("hits") / ("total" -> "1.0") and
          /("responses") /# 1 / ("hits") / ("total" -> "1.0")

        deleteIndex("foo")
      }

      "without index or type" in withWs { implicit ws =>
        index(index = "foo1", `type` = "bar", id = "1", data = Some( """{"name": "Fred Smith"}"""))
        index(index = "foo2", `type` = "bar", id = "2", data = Some( """{"name": "Mary Jones"}"""))

        awaitUtil(TestSearch.msearch(index = Some("foo"), query =
          """
            |{"index": "foo1"}
            |{"query" : {"match" : {"name": "Fred"}}}
            |{"index": "foo2"}
            |{"query" : {"match" : {"name": "Jones"}}}
          """.stripMargin), Duration(1, "second")).body must
          /("responses") /# 0 / ("hits") / ("total" -> "1.0") and
          /("responses") /# 1 / ("hits") / ("total" -> "1.0")

        deleteIndex("foo1")
        deleteIndex("foo2")
      }
    }


    "delete a document by query" in withWs { implicit ws =>


      index(index = "foo", `type` = "foo", id = "foo2", data = Some("{\"foo\":\"bar\"}"))

      awaitUtil(TestSearch.count(Seq("foo"), Seq("foo"), query = Some("{\"query\": { \"match_all\": {} } }"))).body must contain("\"count\":1")

      // v1.5 '{"took":2,"errors":false,"items":[{"delete":{"_index":"foo","_type":"foo","_id":"foo2","_version":2,"status":200,"found":true}}]}' doesn't contain '"successful"'
      // v2.x '{"took":8,"errors":false,"items":[{"delete":{"_index":"foo","_type":"foo","_id":"foo2","_version":2,"_shards":{"total":2,"successful":1,"failed":0},"status":200,"found":true}}]}'
      awaitUtil(TestSearch.deleteByQuery("foo", "foo", """{ "query": { "match_all" : { } } }""")).body must contain("\"found\":true")
      Thread.sleep(1000) //ES needs some time to make the delete happen

      awaitUtil(TestSearch.count(Seq("foo"), Seq("foo"), Some("{\"query\": { \"match_all\": {} } }"))).body must contain("\"count\":0")

      deleteIndex("foo")
    }


    "get settings" in withWs { implicit ws =>


      awaitUtil(TestSearch.createIndex(name = "replicas3",
        settings = Some( """{"settings": {"number_of_shards" : 1, "number_of_replicas": 3}}""")
      )).body must contain("acknowledged")

      // The tests start a single-node cluster and so the index can never be green.  Hence we only wait for "yellow".
      awaitUtil(TestSearch.health(List("replicas3"), waitForStatus = Some("yellow"), timeout = Some("5s")))

      awaitUtil(TestSearch.getSettings(List("replicas3"))).body must
        /("replicas3") / ("settings") / ("index") / ("number_of_replicas" -> "3")

      awaitUtil(TestSearch.deleteIndex("replicas3")).body must contain("acknowledged")
    }

    "properly manipulate mappings" in withWs { implicit ws =>


      createIndex("foo")

      awaitUtil(TestSearch.putMapping(Seq("foo"), "foo", """{"foo": { "properties": { "message": { "type": "string", "store": true } } } }""")).body must contain("acknowledged")

      awaitUtil(TestSearch.verifyType("foo", "foo"))

      awaitUtil(TestSearch.getMapping(Seq("foo"), Seq("foo"))).body must contain("store")

      // v1.5  '{"error":"MergeMappingException[Merge failed with failures {[mapper [message] of different type, current_type [string], merged_type [integer]]}]","status":400}'
      // v2.1  '{"error":{"root_cause":[{"type":"merge_mapping_exception","reason":"Merge failed with failures {[mapper [message] of different type, current_type [string], merged_type [integer]]}"}],"type":"merge_mapping_exception","reason":"Merge failed with failures {[mapper [message] of different type, current_type [string], merged_type [integer]]}"},"status":400}'
      awaitUtil(TestSearch.putMapping(Seq("foo"), "foo",
        """{"foo": { "properties": { "message": { "type": "integer", "store": true } } } }""",
        updateAllTypes = false)).body must contain("mapper [message] of different type")

      /* Cant change existing mapping
      awaitUtil(TestSearch.putMapping(Seq("foo"), "foo",
        """{"foo": { "properties": { "message": { "type": "integer", "store": true } } } }""",
        updateAllTypes = true)).body must contain("acknowledged")
        */

      deleteIndex("foo")
    }

    "properly update settings" in withWs { implicit ws =>


      createIndex("foo")
      createIndex("bar")

      awaitUtil(TestSearch.putSettings(Seq("foo", "bar"), """{"index.blocks.read": true}""")).body must contain("acknowledged")

      awaitUtil(TestSearch.getSettings(Seq("foo", "bar"))).body must
        /("foo") / "settings" / "index" / "blocks" / ("read" -> true) and
        /("bar") / "settings" / "index" / "blocks" / ("read" -> true)

      deleteIndex("foo")
      deleteIndex("bar")
    }

    "suggest completions" in withWs { implicit ws =>


      createIndex("music")

      awaitUtil(TestSearch.putMapping(Seq("music"), "song",
        """{
          |  "song" : {
          |        "properties" : {
          |            "name" : { "type" : "string" },
          |            "suggest" : { "type" : "completion",
          |                          "analyzer" : "simple",
          |                          "search_analyzer" : "simple",
          |                          "payloads" : true
          |            }
          |        }
          |    }
          |}
        """.stripMargin)).body must contain("acknowledged")

      index("music", "song", "1",
        Some(
          """{
            |    "name" : "Nevermind",
            |    "suggest" : {
            |        "input": [ "Nevermind", "Nirvana" ],
            |        "output": "Nirvana - Nevermind",
            |        "payload" : { "artistId" : 2321 },
            |        "weight" : 34
            |    }
            |}
          """.stripMargin))

      awaitUtil(TestSearch.suggest("music",
        """{
          |    "song-suggest" : {
          |        "text" : "n",
          |        "completion" : {
          |            "field" : "suggest"
          |        }
          |    }
          |}
        """.stripMargin)).body must contain("Nirvana - Nevermind")

      deleteIndex("music")
    }

    "validate and explain queries" in withWs { implicit ws =>


      createIndex("foo")

      index(index = "foo", `type` = "foo", id = "foo2", data = Some("{\"foo\":\"bar\"}"))

      awaitUtil(TestSearch.validate(index = "foo", query = "{\"query\": { \"match_all\": {} }")).body must contain("\"valid\"")

      awaitUtil(TestSearch.explain(index = "foo", `type` = "foo", id = "foo2", query = "{\"query\": { \"term\": { \"foo\":\"bar\"} } }")).body must contain("explanation")

      deleteIndex("foo")
    }

    "handle health checking" in withWs { implicit ws =>


      awaitUtil(TestSearch.health()).body must contain("number_of_nodes")

      awaitUtil(TestSearch.health(level = Some("indices"), timeout = Some("5s"))).body must contain("number_of_nodes")
    }

    "handle stats checking" in withWs { implicit ws =>


      createIndex("foo")
      createIndex("bar")

      val res = awaitUtil(TestSearch.stats()).body
      res must contain("primaries")
      res must contain("_all")
      res must contain("indices")

      val fooRes = awaitUtil(TestSearch.stats(indices = Seq("foo"))).body
      fooRes must contain("_all")
      fooRes must contain("indices")
      fooRes must contain("foo")
      fooRes must not contain ("bar")

      val barRes = awaitUtil(TestSearch.stats(indices = Seq("bar"))).body
      barRes must contain("_all")
      barRes must contain("indices")
      barRes must contain("bar")
      barRes must not contain ("foo")

      deleteIndex("foo")
      deleteIndex("bar")
    }

    "handle refresh" in withWs { implicit ws =>


      createIndex("test")

      val res = awaitUtil(TestSearch.refresh("test")).body
      res must contain("\"successful\"")

      deleteIndex("test")
    }

    "handle bulk requests" in withWs { implicit ws =>


      val res = awaitUtil(TestSearch.bulk(data =
        """{ "index" : { "_index" : "test", "_type" : "type1", "_id" : "1" } }
   { "field1" : "value1" }
   { "delete" : { "_index" : "test", "_type" : "type1", "_id" : "2" } }
   { "create" : { "_index" : "test", "_type" : "type1", "_id" : "3" } }
   { "field1" : "value3" }
   { "update" : {"_id" : "1", "_type" : "type1", "_index" : "index1"} }
   { "doc" : {"field2" : "value2"} }""")).body
      res must contain("\"status\":201")

      deleteIndex("test")
    }


  }

  step {
    server.stop()
  }

}

