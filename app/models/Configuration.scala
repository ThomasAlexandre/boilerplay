package models

import better.files._
import play.api.{Environment, Mode}
import services.notification.SlackConfig
import util.metrics.MetricsConfig

@javax.inject.Singleton
class Configuration @javax.inject.Inject() (val cnf: play.api.Configuration, val metrics: MetricsConfig, env: Environment) {
  val debug = env.mode == Mode.Dev
  val dataDir = cnf.get[String]("data.directory").toFile

  val slackConfig = SlackConfig(
    enabled = cnf.get[Boolean]("notification.slack.enabled"),
    url = cnf.get[String]("notification.slack.url"),
    channel = cnf.get[String]("notification.slack.channel"),
    username = cnf.get[String]("notification.slack.username"),
    iconUrl = cnf.get[String]("notification.slack.iconUrl")
  )
}
