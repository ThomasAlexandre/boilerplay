package services.user

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.queries.auth._
import models.result.data.DataField
import models.result.filter.Filter
import models.result.orderBy.OrderBy
import models.user.{Role, User}
import services.ModelServiceHelper
import util.FutureUtils.databaseContext
import services.database.Database
import services.cache.UserCache
import util.tracing.{TraceData, TracingService}

import scala.concurrent.Future

@javax.inject.Singleton
class UserService @javax.inject.Inject() (override val tracing: TracingService, hasher: PasswordHasher) extends ModelServiceHelper[User] {
  def getByPrimaryKey(id: UUID)(implicit trace: TraceData) = traceF("get.by.primary.key")(_ => Database.query(UserQueries.getByPrimaryKey(Seq(id))))
  def getByPrimaryKeySeq(idSeq: Seq[UUID])(implicit trace: TraceData) = traceF("get.by.primary.key.sequence") { _ =>
    Database.query(UserQueries.getByPrimaryKeySeq(idSeq))
  }

  def getByRoleSeq(roleSeq: Seq[Role])(implicit trace: TraceData) = traceF("get.by.role.sequence")(_ => Database.query(UserQueries.getByRoleSeq(roleSeq)))

  override def countAll(filters: Seq[Filter] = Nil)(implicit trace: TraceData) = traceF("count.all")(_ => Database.query(UserQueries.countAll(filters)))
  override def getAll(filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int] = None, offset: Option[Int] = None)(implicit trace: TraceData) = {
    traceF("get.all")(_ => Database.query(UserQueries.getAll(filters, orderBys, limit, offset)))
  }

  override def searchCount(q: String, filters: Seq[Filter])(implicit trace: TraceData) = {
    traceF("search.count")(_ => Database.query(UserQueries.searchCount(q, filters)))
  }
  override def search(q: String, filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int], offset: Option[Int])(implicit trace: TraceData) = {
    traceF("search")(_ => Database.query(UserQueries.search(q, filters, orderBys, limit, offset)))
  }
  def searchExact(q: String, orderBys: Seq[OrderBy], limit: Option[Int], offset: Option[Int])(implicit trace: TraceData) = {
    traceF("search.exact")(_ => Database.query(UserQueries.searchExact(q, orderBys, limit, offset)))
  }

  def isUsernameInUse(name: String)(implicit trace: TraceData) = traceF("username.in.use")(_ => Database.query(UserSearchQueries.IsUsernameInUse(name)))

  def insert(user: User)(implicit trace: TraceData) = traceF("insert")(_ => Database.execute(UserQueries.insert(user)).map { _ =>
    log.info(s"Inserted user [$user].")
    UserCache.cacheUser(user)
    user
  })

  def update(user: User)(implicit trace: TraceData) = traceF("update")(_ => Database.execute(UserQueries.UpdateUser(user)).map { _ =>
    log.info(s"Updated user [$user].")
    UserCache.cacheUser(user)
    user
  })

  def remove(userId: UUID)(implicit trace: TraceData) = traceF("remove")(_ => Database.transaction { conn =>
    val startTime = System.nanoTime
    val f = getByPrimaryKey(userId).flatMap {
      case Some(user) => Database.execute(PasswordInfoQueries.removeByPrimaryKey(Seq(user.profile.providerID, user.profile.providerKey)), Some(conn))
      case None => throw new IllegalStateException("Invalid User")
    }
    f.flatMap { _ =>
      Database.execute(UserQueries.removeByPrimaryKey(Seq(userId)), Some(conn)).map { users =>
        UserCache.removeUser(userId)
        val timing = ((System.nanoTime - startTime) / 1000000).toInt
        Map("users" -> users, "timing" -> timing)
      }
    }
  })

  def updateFields(id: UUID, username: String, email: String, password: Option[String], role: Role, originalEmail: String)(implicit trace: TraceData) = {
    traceF("update.fields") { _ =>
      val fields = Seq(
        DataField("username", Some(username)),
        DataField("email", Some(email)),
        DataField("role", Some(role.toString))
      )
      Database.execute(UserQueries.update(id, fields)).flatMap { _ =>
        val emailUpdated = if (email != originalEmail) {
          Database.execute(PasswordInfoQueries.UpdateEmail(originalEmail, email))
        } else {
          Future.successful(0)
        }
        emailUpdated.flatMap { _ =>
          password match {
            case Some(p) =>
              val loginInfo = LoginInfo(CredentialsProvider.ID, email)
              val authInfo = hasher.hash(p)
              Database.execute(PasswordInfoQueries.UpdatePasswordInfo(loginInfo, authInfo))
            case _ => Future.successful(id)
          }
        }.map { _ =>
          UserCache.removeUser(id)
          id
        }
      }
    }
  }
}
