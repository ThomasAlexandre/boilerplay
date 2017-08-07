package services.auth

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import models.queries.auth._
import models.user.{RichUser, Role, User}
import services.database.Database
import services.user.UserService
import util.FutureUtils.defaultContext
import util.Logging
import util.cache.UserCache

import scala.concurrent.Future

@javax.inject.Singleton
class AuthService @javax.inject.Inject() (hasher: PasswordHasher) extends Logging {
  def getById(id: UUID) = UserService.getById(id).map(_.map(RichUser.apply))
  def userCount = Database.query(AuthQueries.count)
  def isUsernameInUse(name: String) = Database.query(AuthQueries.IsUsernameInUse(name))
  def usernameLookup(id: UUID) = Database.query(AuthQueries.GetUsername(id))

  def save(user: RichUser, update: Boolean = false) = {
    log.info(s"${if (update) { "Updating" } else { "Creating" }} user [$user].")
    val statement = if (update) {
      AuthQueries.UpdateUser(user)
    } else {
      AuthQueries.insert(user)
    }
    Database.execute(statement).map { _ =>
      UserCache.cacheUser(user.toUser)
      user
    }
  }

  def usernameLookupMulti(ids: Set[UUID]) = if (ids.isEmpty) {
    Future.successful(Map.empty[UUID, String])
  } else {
    Database.query(AuthQueries.GetUsernames(ids))
  }

  def remove(userId: UUID) = Database.transaction { conn =>
    val startTime = System.nanoTime
    val f = getById(userId).flatMap {
      case Some(user) => Database.execute(PasswordInfoQueries.removeById(Seq(user.profile.providerID, user.profile.providerKey)), Some(conn))
      case None => throw new IllegalStateException("Invalid User")
    }
    f.flatMap { _ =>
      Database.execute(AuthQueries.removeById(Seq(userId)), Some(conn)).map { users =>
        UserCache.removeUser(userId)
        val timing = ((System.nanoTime - startTime) / 1000000).toInt
        Map("users" -> users, "timing" -> timing)
      }
    }
  }

  def enableAdmin(user: User) = Database.query(AuthQueries.CountAdmins).flatMap { adminCount =>
    if (adminCount == 0) {
      Database.execute(AuthQueries.SetRole(user.id, Role.Admin)).map(_ => UserCache.removeUser(user.id))
    } else {
      throw new IllegalStateException("An admin already exists.")
    }
  }

  def getAll(limit: Option[Int] = None, offset: Option[Int] = None) = UserService.getAll(Some("\"username\""), limit, offset).map(_.map(RichUser.apply))
  def search(q: String, limit: Option[Int], offset: Option[Int]) = try {
    getById(UUID.fromString(q)).map(_.toSeq)
  } catch {
    case _: IllegalArgumentException => Database.query(AuthQueries.search(q, Some("id desc"), limit, offset))
  }

  def update(id: UUID, username: String, email: String, password: Option[String], role: Role, originalEmail: String) = {
    Database.execute(AuthQueries.UpdateFields(id, username, email, role)).flatMap { _ =>
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
