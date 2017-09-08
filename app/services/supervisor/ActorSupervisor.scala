package services.supervisor

import java.util.UUID

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ActorRef, OneForOneStrategy, SupervisorStrategy}
import models._
import java.time.LocalDateTime
import util.metrics.{InstrumentedActor, MetricsServletActor}
import util.{DateUtils, Logging}

class ActorSupervisor(val app: Application) extends InstrumentedActor with Logging {
  override def preStart() = {
    context.actorOf(MetricsServletActor.props(app.config.metrics), "metrics-servlet")
    log.debug(s"Actor Supervisor started for [${util.Config.projectId}].")
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  override def receiveRequest = {
    case im: InternalMessage => log.warn(s"Unhandled internal message [${im.getClass.getSimpleName}] received.")
    case x => log.warn(s"ActorSupervisor encountered unknown message: ${x.toString}")
  }
}
