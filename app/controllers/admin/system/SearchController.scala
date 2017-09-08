package controllers.admin.system

import java.util.UUID

import controllers.BaseController
import models.Application
import play.twirl.api.Html
import services.ServiceRegistry
import util.tracing.TraceData

import scala.concurrent.Future

@javax.inject.Singleton
class SearchController @javax.inject.Inject() (override val app: Application, services: ServiceRegistry) extends BaseController("search") {
  import app.contexts.webContext

  def search(q: String) = withSession("admin.search", admin = true) { implicit request => implicit td =>
    val resultF = try {
      searchInt(q, q.toInt)
    } catch {
      case _: NumberFormatException => try {
        searchUuid(q, UUID.fromString(q))
      } catch {
        case _: IllegalArgumentException => searchString(q)
      }
    }

    resultF.map { results =>
      Ok(views.html.admin.explore.searchResults(q, results))
    }
  }

  private[this] def searchInt(q: String, id: Int)(implicit timing: TraceData) = {
    // Start int searches
    val intSearches = Seq.empty[Future[Seq[Html]]]
    // End int searches

    Future.sequence(intSearches).map(_.flatten)
  }

  private[this] def searchUuid(q: String, id: UUID)(implicit timing: TraceData) = {
    // Start uuid searches
    val uuidSearches = Seq.empty[Future[Seq[Html]]]
    // End uuid searches

    Future.sequence(uuidSearches).map(_.flatten)
  }

  private[this] def searchString(q: String)(implicit timing: TraceData) = {
    // Start string searches
    val stringSearches = Seq.empty[Future[Seq[Html]]]
    // End string searches

    Future.sequence(stringSearches).map(_.flatten)
  }
}
