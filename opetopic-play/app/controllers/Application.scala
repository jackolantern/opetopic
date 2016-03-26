package controllers

import play.api._
import play.api.mvc._

import libs.json._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def sketchpad = Action {
    Ok(views.html.sketchpad())
  }

  def prover = Action {
    Ok(views.html.prover())
  }

  def showDoc(page: String) = Action {
    page match {
      case "intro" => Ok(views.html.docs.intro())
      case "typetheory" => Ok(views.html.docs.typetheory())
      case "hdts" => Ok(views.html.docs.hdts())
      case "opetopes" => Ok(views.html.docs.opetopes())
      case _ => Ok("Not found: " ++ page)
    }
  }

  def showTutorial(page: String) = Action {
    page match {
      case "intro" => Ok(views.html.tutorial.intro())
      case "liftings" => Ok(views.html.tutorial.liftings())
      case _ => Ok("Not found: " ++ page)
    }
  }


}
