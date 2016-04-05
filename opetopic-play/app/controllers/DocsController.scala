/**
  * DocsController.scala - Documentation Controller
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package controllers

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import play.api.i18n.{ MessagesApi, Messages }
import play.api.libs.concurrent.Execution.Implicits._

import models.User
import models.services.UserService

class DocsController @Inject() (
  val messagesApi: MessagesApi,
  val env: Environment[User, CookieAuthenticator],
  userService: UserService
) extends Silhouette[User, CookieAuthenticator] {

  def showDoc(page: String) = SecuredAction.async {
    page match {
      case "intro" => Future.successful(Ok(views.html.docs.intro()))
      case "typetheory" => Future.successful(Ok(views.html.docs.typetheory()))
      case "hdts" => Future.successful(Ok(views.html.docs.hdts()))
      case "opetopes" => Future.successful(Ok(views.html.docs.opetopes()))
      case _ => Future.successful(Ok("No doc found"))
    }
  }

}
