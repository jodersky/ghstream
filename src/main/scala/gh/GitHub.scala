package gh

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Keep, Sink }
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal


object GitHub extends Issues with AkkaHttpClient {

  implicit val system = ActorSystem("gh")
  implicit val materializer = ActorMaterializer()

  def client = new AkkaHttpClient("api.github.com")

}

object Main extends App {
  import GitHub._

  val done = issues.get("jodersky", "sbt-jni").toMat(Sink.foreach(println))(Keep.right).run()

  try {
    println(Await.result(done, 10.seconds))
  } finally {
    GitHub.system.shutdown()
  }

}
