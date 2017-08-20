package controllers.admin.system

import controllers.BaseController
import models.Application

@javax.inject.Singleton
class MetricsController @javax.inject.Inject() (override val app: Application) extends BaseController("metrics") {
  import app.contexts.webContext

  def showMetrics = withSession("admin.metrics", admin = true) { implicit request =>
    val url = "http://localhost:2001/metrics?pretty=true"
    val call = app.ws.url(url).withHttpHeaders("Accept" -> "application/json")
    val f = call.get()

    f.map { json =>
      Ok(views.html.admin.metrics(request.identity, json.body))
    }
  }
}
