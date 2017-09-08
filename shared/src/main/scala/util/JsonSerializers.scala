package util

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.java8.time._

import models.{RequestMessage, ResponseMessage}

object JsonSerializers {
  private[this] implicit val config = Configuration.default.withDefaults

  def readRequestMessage(s: String) = decode[RequestMessage](s) match {
    case Right(x) => x
    case Left(err) => throw err
  }
  def writeRequestMessage(sm: RequestMessage, debug: Boolean = false) = if (debug) { sm.asJson.spaces2 } else { sm.asJson.noSpaces }

  def readResponseMessage(s: String) = decode[ResponseMessage](s) match {
    case Right(x) => x
    case Left(err) => throw err
  }
  def writeResponseMessage(sm: ResponseMessage, debug: Boolean = false) = if (debug) { sm.asJson.spaces2 } else { sm.asJson.noSpaces }
}
