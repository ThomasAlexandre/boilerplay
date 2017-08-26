import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin
import webscalajs.ScalaJSWeb
import sbt.Keys._
import sbt._

import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.{toScalaJSGroupID => _, _}

object Client {
  private[this] val clientSettings = Shared.commonSettings ++ Seq(
    libraryDependencies ++= Seq(
      "be.doeraene" %%% "scalajs-jquery" % Dependencies.ScalaJS.jQueryVersion,
      "com.lihaoyi" %%% "scalatags" % Dependencies.ScalaJS.scalaTagsVersion,
      "com.outr" %%% "scribe" % Dependencies.Utils.scribeVersion
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    scapegoatIgnoredFiles := Seq(".*/JsonUtils.scala", ".*/JsonSerializers.scala")
  )

  lazy val client = (project in file("client"))
    .settings(clientSettings: _*)
    .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
    .dependsOn(Shared.sharedJs)
}
