package gh

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ MediaTypes, ResponseEntity }
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ ContentType, RequestEntity, Uri }
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.headers.{ Link, LinkValue }
import akka.http.scaladsl.model.headers.LinkParams
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, HttpHeader}
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.http.scaladsl.unmarshalling.FromResponseUnmarshaller
import akka.stream.{ BidiShape, Materializer }
import akka.stream.scaladsl.{ BidiFlow, Broadcast, Flow, GraphDSL, Merge, Sink, Source }
import akka.http.scaladsl.{HttpExt, Http}
import scala.concurrent.ExecutionContext
import spray.json.{ JsonParser, JsonReader }
import spray.json.JsonReader

trait HttpClient {

  def client: HttpClient

  trait HttpClient {
    def get[A: JsonReader](path: String): Source[A, NotUsed]
  }

}

trait AkkaHttpClient extends HttpClient {

  class AkkaHttpClient(host: String)(implicit system: ActorSystem, materializer: Materializer) extends HttpClient {
    
    val http = Http(system)

    def get[A: JsonReader](path: String): Source[A, NotUsed] = {
      implicit val ec = materializer.executionContext

      val uri = Uri() withScheme("https") withHost(host) withPath(Path(path))
      val request = HttpRequest(uri = uri) withHeaders(Accept(MediaTypes.`application/json`))

      println(request)
      val um = implicitly[FromResponseUnmarshaller[String]].map(text => {
        val v = JsonParser(text)
        println(v.prettyPrint)
        v.convertTo[A]
      })

      Source.single(request)
        .via(paginate(100).join(http.outgoingConnectionHttps(host)))
        .mapAsync(10)(um.apply)
    }


    def paginate(itemsPerPage: Int):
        BidiFlow[HttpRequest, HttpRequest, HttpResponse, HttpResponse, NotUsed] = {
      BidiFlow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val initialRequest = builder.add(Flow[HttpRequest].take(1))

        // set number of items per page
        val perPage = builder.add(Flow[HttpRequest].map{ req =>
          val uri = req.uri withQuery Query("per_page" -> itemsPerPage.toString)
          req withUri uri
        })

        // get next request from a paginated response
        val nextPage = builder.add(Flow[HttpResponse].takeWhile{
          case NextPage(_) => true
          case _ => false
        }.map{
          case NextPage(next) => next
        })

        val merge = builder.add(Merge[HttpRequest](2))
        val split = builder.add(Broadcast[HttpResponse](2))

        initialRequest ~> perPage ~> merge
        split.out(0) ~> nextPage ~> merge

        BidiShape(initialRequest.in, merge.out, split.in, split.out(1))
      })
    }

    def unmarshal[A](implicit u: Unmarshaller[ResponseEntity, A], ec: ExecutionContext): Flow[HttpResponse, A, NotUsed] = {
      Flow[HttpResponse].mapAsync(10){ response =>
        if (response.status.isSuccess()) {
          Unmarshal(response.entity).to[A]
        } else {
          throw new RuntimeException("Unsuccessful response status code: " + response.status.value)
        }
      }
    }

    // Extractor for responses containing a 'Link' header to a next page, i.e.
    // Link: <https://api.github.com/user/repos?page=3&per_page=100>; rel="next"
    private object NextPage {
      def unapply(response: HttpResponse): Option[(HttpRequest)] = {
        val next = for (
          Link(values) <- response.header[Link].toSeq;
          LinkValue(nextUri, Seq(LinkParams.rel("next"))) <- values
        ) yield {
          HttpRequest(uri = nextUri)
        }
        next.headOption
      }
    }

  }

}
