/**
  * Prover.scala - Opetopic Theorem Prover
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js.prover

import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer

import scala.scalajs.js
import scala.scalajs.js.JSApp
import org.scalajs.jquery._
import scalatags.JsDom.all._
import scala.scalajs.js.Dynamic.{literal => lit}

import scalaz.std.string._

import opetopic._
import opetopic.tt._
import OpetopicTypeChecker._
import opetopic.js.JQuerySemanticUI._

import syntax.complex._
import syntax.tree._
import syntax.nesting._
import syntax.suite._

object Prover extends JSApp {

  def main : Unit = {

    println("Launched Opetopic Prover.")

    jQuery("#new-defn-btn").on("click", () => newDefinition)
    jQuery(".menu .item").tab()

  }

  //============================================================================================
  // DEFINITION MANAGEMENT
  //

  var rho: Rho = RNil

  def newDefinition: Unit = {

    val defnWksp = new DefinitionWorkspace(rho)

    jQuery("#defn-tab").empty().append(defnWksp.mainGrid)
    defnWksp.initUI
    defnWksp.newEditor
    defnWksp.extendContext("X", ECat)

  }

  //============================================================================================
  // USER FEEDBACK ROUTINES
  //

  def showErrorMessage(str: String) : Unit = {

    val closeIcon = i(cls := "close icon").render

    val msg = div(cls := "ui negative message")(
      closeIcon,
      div(cls := "header")("Error:"),
      p(str)
    ).render

    jQuery(closeIcon).on("click", () => {
      jQuery(msg).transition(lit(
        animation = "fade",
        onComplete = () => {
          jQuery(msg).remove()
        }
      ))
    })

    jQuery("#msg-box").append(msg)

  }

  def showInfoMessage(str: String) : Unit = {

    val closeIcon = i(cls := "close icon").render

    val msg = div(cls := "ui yellow message")(
      closeIcon,
      div(cls := "header")("Info:"),
      p(str)
    ).render

    jQuery(closeIcon).on("click", () => {
      jQuery(msg).transition(lit(
        animation = "fade",
        onComplete = () => {
          jQuery(msg).remove()
        }
      ))
    })

    jQuery("#msg-box").append(msg)

  }

}
