package controllers

import brave.Span
import models.Application
import models.result.data.DataField
import play.api.mvc._
import util.metrics.Instrumented
import util.web.TracingFilter
import util.Logging
import util.tracing.TraceData
import zipkin.TraceKeys

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseController(val name: String) extends InjectedController with Instrumented with Logging {
  protected def app: Application

  protected def withSession(action: String, admin: Boolean = false)(block: Request[AnyContent] => TraceData => Future[Result])(implicit ec: ExecutionContext) = {
    Action.async { implicit request =>
      metrics.timer(name + "." + action).timeFuture {
        app.tracing.trace(name + ".controller." + action) { td =>
          enhanceRequest(request, td.span)
          block(request)(td)
        }(getTraceData)
      }
    }
  }

  protected def getTraceData(implicit requestHeader: RequestHeader) = requestHeader.attrs(TracingFilter.traceKey)

  protected def modelForm(rawForm: Option[Map[String, Seq[String]]]) = {
    val form = rawForm.getOrElse(Map.empty).mapValues(_.head)
    val fields = form.toSeq.filter(x => x._1.endsWith(".include") && x._2 == "true").map(_._1.stripSuffix(".include"))
    fields.map(f => DataField(f, Some(form.getOrElse(f, throw new IllegalStateException(s"Cannot find value for included field [$f].")))))
  }

  private[this] def enhanceRequest(request: Request[AnyContent], trace: Span) = {
    trace.tag(TraceKeys.HTTP_REQUEST_SIZE, request.body.asText.map(_.length).orElse(request.body.asRaw.map(_.size)).getOrElse(0).toString)
  }
}
