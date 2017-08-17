package services.database

import com.github.mauricio.async.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory
import com.github.mauricio.async.db.{Configuration, Connection, QueryResult}
import models.database.{RawQuery, Statement}
import org.slf4j.LoggerFactory
import util.FutureUtils.databaseContext
import util.metrics.Instrumented

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Database extends Instrumented {
  private[this] val log = LoggerFactory.getLogger(Database.getClass)
  private[this] val poolConfig = new PoolConfiguration(maxObjects = 100, maxIdle = 10, maxQueueSize = 1000)
  private[this] var factory: MySQLConnectionFactory = _
  private[this] var pool: ConnectionPool[MySQLConnection] = _
  private[this] var config: Option[Configuration] = None
  def getConfig = config.getOrElse(throw new IllegalStateException("Database not open."))

  private[this] def prependComment(obj: Object, sql: String) = s"/* ${obj.getClass.getSimpleName.replace("$", "")} */ $sql"

  def open(cfg: play.api.Configuration): Unit = {
    def get(k: String) = cfg.get[Option[String]]("database." + k).getOrElse(throw new IllegalStateException(s"Missing config for [$k]."))
    open(get("username"), get("host"), get("port").toInt, Some(get("password")), Some(get("database")))
  }

  def open(username: String, host: String = "localhost", port: Int = 5432, password: Option[String] = None, database: Option[String] = None): Unit = {
    open(Configuration(username, host, port, password, database))
  }

  def open(configuration: Configuration): Unit = {
    factory = new MySQLConnectionFactory(configuration)
    pool = new ConnectionPool(factory, poolConfig)
    config = Some(configuration)

    val healthCheck = pool.sendQuery("select now()")
    healthCheck.failed.foreach(x => throw new IllegalStateException("Cannot connect to database.", x))
    Await.result(healthCheck.map(r => r.rowsAffected == 1.toLong), 5.seconds)
  }

  def transaction[A](f: (Connection) => Future[A], conn: Connection = pool): Future[A] = conn.inTransaction(c => f(c))

  def execute(statement: Statement, conn: Option[Connection] = None): Future[Int] = {
    val name = statement.getClass.getSimpleName.replaceAllLiterally("$", "")
    log.debug(s"Executing statement [$name] with SQL [${statement.sql}] with values [${statement.values.mkString(", ")}].")
    val ret = metrics.timer(s"execute.$name").timeFuture {
      conn.getOrElse(pool).sendPreparedStatement(prependComment(statement, statement.sql), statement.values).map(_.rowsAffected.toInt)
    }
    ret.failed.foreach(x => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing statement [$name] with SQL [${statement.sql}].", x))
    ret
  }

  def query[A](query: RawQuery[A], conn: Option[Connection] = None): Future[A] = {
    val name = query.getClass.getSimpleName.replaceAllLiterally("$", "")
    log.debug(s"Executing query [$name] with SQL [${query.sql}] with values [${query.values.mkString(", ")}].")
    val ret = metrics.timer(s"query.$name").timeFuture {
      conn.getOrElse(pool).sendPreparedStatement(prependComment(query, query.sql), query.values).map { r =>
        query.handle(r.rows.getOrElse(throw new IllegalStateException()))
      }
    }
    ret.failed.foreach(x => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing query [$name] with SQL [${query.sql}].", x))
    ret
  }

  def raw(name: String, sql: String, conn: Option[Connection] = None): Future[QueryResult] = {
    log.debug(s"Executing raw query [$name] with SQL [$sql].")
    val ret = metrics.timer(s"raw.$name").timeFuture {
      conn.getOrElse(pool).sendQuery(prependComment(name, sql))
    }
    ret.failed.foreach(x => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing raw query [$name] with SQL [$sql].", x))
    ret
  }

  def close() = {
    Await.result(pool.close, 5.seconds)
    true
  }
}
