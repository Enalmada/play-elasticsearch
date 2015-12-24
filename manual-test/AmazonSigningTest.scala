
import java.nio.charset.StandardCharsets

import org.specs2.matcher.JsonMatchers
import play.api.libs.ws.WSClient
import play.api.test.{FakeApplication, PlaySpecification}
import services._

import scala.concurrent.duration._
import scala.concurrent.{Await, Awaitable}

class AmazonSigningTest extends PlaySpecification with JsonMatchers {

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

  "Signing" should {

    object TestSearch extends ElasticSearch {
      override val clientUrl = s""
      override val AWSACCESSKEYID: String = ""
      override val AWSSECRETKEY: String = ""
    }

    "work with aws" in withWs { implicit ws =>
      awaitUtil(TestSearch.search("foo")).body must contain("took")
    }


  }


}

