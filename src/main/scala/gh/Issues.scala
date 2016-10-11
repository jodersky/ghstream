package gh

import akka.NotUsed
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import spray.json.{ DefaultJsonProtocol, JsonFormat }

trait Issues { self: HttpClient =>
  import self._
  case class Issue(
    id: Int,
    number: Int,
    state: String,
    title: String,
    body: String,
    user: User,
    labels: Seq[Label],
    assignee: Option[User],
    milestone: Option[Milestone],
    locked: Boolean,
    comments: Int,
    closedAt: Option[String],
    createdAt: String,
    updatedAt: String
  )

  case class User(
    login: String,
    id: Int,
    avatarUrl: String,
    gravatarId: String,
    `type`: String,
    siteAdmin: Boolean
  )

  case class Label(
    name: String,
    color: String
  )

  case class Milestone(
    id: Int,
    state: String,
    title: String,
    description: String,
    creator: User,
    openIssues: Int,
    closedIssues: Int,
    createdAt: String,
    updatedAt: String,
    closedAt: String,
    dueOn: String
  )

  case class Comment(
    id: Int,
    body: String,
    user: User,
    createdAt: String,
    updatedAt: String
  )

  private object JsonSupport extends DefaultJsonProtocol with SnakifiedSprayJsonSupport {
    implicit val userFormat = jsonFormat6(User)
    implicit val commentFormat = jsonFormat5(Comment)
    implicit val milestoneFormat = jsonFormat11(Milestone)
    implicit val labelFormat = jsonFormat2(Label)
    implicit val itemFormat = jsonFormat14(Issue)
  }
  import JsonSupport._

  object issues {
    def get(
      owner: String,
      repo: String,
      milestone: String = "*",
      state: String = "all",
      assignee: String = "*",
      creator: String = "*",
      mentioned: String = "*",
      labels: String = "*",
      sort: String = "created",
      direction: String = "desc",
      since: String = "1970-01-01T00:00:00"
    ): Source[Issue, NotUsed] = {
      client.get[collection.immutable.Seq[Issue]]("/repos/" + owner + "/" + repo + "/issues").mapConcat(x => x)
    }

  }

}
