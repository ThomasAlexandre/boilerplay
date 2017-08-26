package services.notification

import models.Configuration
import models.notification.{Notification, NotificationField}
import models.result.data.DataField
import util.tracing.TraceData
import util.web.TracingWSClient
import util.{FutureUtils, Logging, NullUtils}

object NotificationService extends Logging {
  private var inst: Option[NotificationService] = None

  private[this] def onNotification(notification: Notification)(implicit trace: TraceData) = {
    inst.foreach(_.callback(notification))
    notification
  }

  def onInsert(t: String, fields: Seq[DataField])(implicit trace: TraceData) = {
    val msg = s"Inserted new [$t] with [${fields.size}] fields:"
    onNotification(Notification("insert", t, Nil, msg, fields.map(f => NotificationField(f.k, None, f.v))))
  }

  def onUpdate(t: String, ids: Seq[DataField], originalFields: Seq[DataField], newFields: Seq[DataField])(implicit trace: TraceData) = {
    def changeFor(f: DataField) = originalFields.find(_.k == f.k).map { o =>
      NotificationField(f.k, o.v, f.v)
    }.getOrElse(throw new IllegalStateException(s"Missing original field [${f.k}]."))
    val changes = newFields.map(changeFor)
    val msg = s"Updated [${changes.size}] fields of $t[${ids.map(id => id.k + ": " + id.v.getOrElse(NullUtils.char)).mkString(", ")}]:\n"
    onNotification(Notification("update", t, ids, msg, changes))
  }
}

@javax.inject.Singleton
class NotificationService @javax.inject.Inject() (config: Configuration, ws: TracingWSClient, fu: FutureUtils) extends Logging {
  import fu.webContext
  NotificationService.inst.foreach(_ => throw new IllegalStateException("Double initialization."))
  NotificationService.inst = Some(this)

  def callback(n: Notification)(implicit trace: TraceData) = {
    val logs = n.changes.map(c => s"  ${c.key}: ${c.originalValue.getOrElse(NullUtils.char)} -> ${c.newValue.getOrElse(NullUtils.char)}").mkString("\n")
    val msg = n.msg + logs

    if (config.slackConfig.enabled) {
      import io.circe.generic.extras.auto._
      import io.circe.syntax._

      val body = Map(
        "channel" -> config.slackConfig.channel,
        "username" -> config.slackConfig.username,
        "icon_url" -> config.slackConfig.iconUrl,
        "text" -> msg
      )

      val call = ws.url("slack.post", config.slackConfig.url).withHttpHeaders("Accept" -> "application/json").post(body.asJson.spaces2)
      val ret = call.map { x =>
        if (x.status == 200) {
          "OK"
        } else {
          log.warn("Unable to post to Slack (" + x.status + "): [" + x.body + "].")
          "ERROR"
        }
      }
      ret.failed.foreach(x => log.warn("Unable to post to Slack.", x))
    }
    log.info(msg)
  }
}
