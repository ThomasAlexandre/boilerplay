package models

import java.util.UUID

import akka.actor.ActorRef

sealed trait InternalMessage

case class SocketStarted(socketId: UUID, conn: ActorRef) extends InternalMessage
case class SocketStopped(socketId: UUID) extends InternalMessage

case object GetSystemStatus extends InternalMessage
case class SystemStatus(sockets: Seq[(UUID, String)]) extends InternalMessage

case class SendSocketTrace(id: UUID) extends InternalMessage
case class SocketTraceResponse(id: UUID, username: String) extends InternalMessage

case class SendClientTrace(id: UUID) extends InternalMessage
case class ClientTraceResponse(id: UUID, data: String) extends InternalMessage
