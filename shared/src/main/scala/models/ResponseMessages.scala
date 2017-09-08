package models

import java.time.LocalDateTime
import java.util.UUID

sealed trait ResponseMessage

case class ServerError(reason: String, content: String) extends ResponseMessage
case class VersionResponse(version: String) extends ResponseMessage

case class Pong(timestamp: LocalDateTime) extends ResponseMessage
case class Disconnected(reason: String) extends ResponseMessage
