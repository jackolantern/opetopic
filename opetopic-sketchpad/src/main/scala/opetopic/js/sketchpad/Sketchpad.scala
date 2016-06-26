/**
  * Sketchpad.scala - Opetopic Sketchpad Application
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js.sketchpad

import scala.scalajs.{js => sjs}
import sjs.Dynamic.{literal => lit}
import scala.scalajs.js.JSApp
import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.jquery._
import scalatags.JsDom.all._

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import opetopic._
import opetopic.ui._
import opetopic.js._
import mtl._

import JsDomFramework._
import JQuerySemanticUI._

object Sketchpad extends JSApp {

  val editor = new JsStableEditor[SimpleMarker]
  val viewer = new JsStableViewer[Option[SimpleMarker]]
  val frameViewer = new JsStableViewer[Option[SimpleMarker]]

  type EditorCell = editor.StableCell
  type FrameCell = frameViewer.CellType

  editor.onSelectAsRoot = (c: EditorCell) => {
    showRootFace
    showProps(c)
  }

  frameViewer.onSelectAsRoot = (c: FrameCell) => {
    updateDecoration(c)
  }

  def main : Unit = {

    println("Launched Opetopic Sketchpad.")

    jQuery("#editor-div").append(editor.uiElement)
    editor.initialize

    jQuery("#viewer-div").append(viewer.uiElement)
    viewer.initialize

    jQuery("#edge-props").append(frameViewer.uiElement)
    frameViewer.initialize

    jQuery("#fill-color-btn").popup(lit(
      popup = jQuery(".color-select.popup"),
      movePopup = false,
      on = "click",
      onShow = () => { isFill = true }
    ))

    jQuery("#stroke-color-btn").popup(lit(
      popup = jQuery(".color-select.popup"),
      movePopup = false,
      on = "click",
      onShow = () => { isFill = false }
    ))

    jQuery("#label-input").on("input", () => { updateLabel })

    jQuery(".color-select.popup button").on("click", (e: JQueryEventObject) => {

      val color= jQuery(e.target).attr("data-color").toString

      if (isFill) {
        showFill(color)
        updateFillColor
      } else {
        showStroke(color)
        updateStrokeColor
      }

    })

    jQuery("#edge-accordion").accordion()

    jQuery("#saved-sketches .item").each((e: dom.Element) => {
      for {
        suuid <- jQuery(e).attr("data-id").toOption
      } {

        jQuery(e).on("click", () => {

          for { (_, old) <- selectedSketch } { 
            jQuery(old).removeClass("active") 
          }

          jQuery(e).addClass("active")
          selectedSketch = Some(suuid, e)

        })
        
      }
    })

    jQuery("#view-btn").on("click", () => { loadSelectedSketch })
    jQuery("#delete-btn").on("click", () => { deleteSelectedSketch })
    jQuery("#save-btn").on("click", () => { saveSketch })
    jQuery("#svg-btn").on("click", () => { renderSketch })
    jQuery("#export-btn").on("click", () => { exportSketch })

  }

  var isFill: Boolean = true
  var fillColor: String = "white"
  var strokeColor: String = "black"
  var selectedSketch: Option[(String, dom.Element)] = None

  def updateLabel: Unit = {

    import latex.LatexParser
    import fastparse.all._

    val inputVal = jQuery("#label-input").value().toString

    val labelVal = 
      LatexParser.Input.parse(
        inputVal
      ) match {
        case Parsed.Success(value, _) => value
        case Parsed.Failure(_, _, _) => inputVal
      }

    editor.updateLabel({
      case None => Some(SimpleMarker(labelVal))
      case Some(SimpleMarker(l, s, td, sd)) => Some(SimpleMarker(labelVal, s, td, sd))
    })

    showRootFace

  }

  def updateDecoration(c: FrameCell): Unit = {
    println("Updating decoration...")


    for {
      _ <- editor.withRoot(sc => {
        sc.label match {
          case None => {
            sc.label = Some(SimpleMarker("", DefaultColorSpec, Some(TriangleDec("black")), Map()))
            editor.refreshEditor
            viewer.refreshViewer
          }
          case Some(mk) => {
            if (c.isExternal) {
              println("Source ...")
            } else {
              println("Target ...")
              val nmk = mk.copy(targetDec = Some(TriangleDec("black")))
              sc.label = Some(nmk)
              editor.refreshEditor
              viewer.refreshViewer
            }
          }
        }
        Some(())
      })
    } yield ()
  }

  def showRootFace: Unit = 
    for {
      face <- editor.rootFace
    } { viewer.complex = Some(face) }

  def updateFillColor: Unit = {
    
    val (f, fh, fs) = colorTripleGen(fillColor)

    editor.updateLabel({
      case None => Some(SimpleMarker("", DefaultColorSpec.copy(fill = f, fillHovered = fh, fillSelected = fs)))
      case Some(SimpleMarker(l, s, td, sd)) =>
        Some(SimpleMarker(l, s.copy(fill = f, fillHovered = fs, fillSelected = fs), td, sd))
    })

    showRootFace

  }

  def updateStrokeColor: Unit = {

    val (st, sh, ss) = colorTripleGen(strokeColor)

    editor.updateLabel({
      case None => Some(SimpleMarker("", DefaultColorSpec.copy(stroke = st, strokeHovered = sh, strokeSelected = ss)))
      case Some(SimpleMarker(l, s, td, sd)) => 
        Some(SimpleMarker(l, s.copy(stroke = st, strokeHovered = sh, strokeSelected = ss), td, sd))
    })

    showRootFace

  }

  def exportSketch: Unit = 
    for {
      c <- viewer.complex
    } {

      def quotStr(str: String): String = "\"" + str + "\""

      val lc = c.map(om => om match {
        case None => quotStr("empty")
        case Some(mk) => quotStr(mk.lbl)
      })
      
      println(lc.toString)

    }

  def renderSketch: Unit = 
    for {
      c <- viewer.complex
    } {

      import upickle.default._
      import opetopic.net._

      val renderData : String = complexToJson(c)
      val sizingMethod : String = write(Percentage(0.05))

      jQuery("#render-data").value(renderData)
      jQuery("#sizing-mthd").value(sizingMethod)
      jQuery("#render-request-form").submit()

    }

  def deleteSelectedSketch: Unit = 
    for { (id, _) <- selectedSketch } {

      import upickle.default._
      import opetopic.net.DeleteSketchRequest

      val req = DeleteSketchRequest(id)

      dom.ext.Ajax.post(
        url = "/deleteSketch",
        data = write(req),
        headers = Map(
          ("X-Requested-With" -> "*"),
          ("CSRF-Token" -> "nocheck")
        ),
        withCredentials = true
      ).map(_.responseText).foreach(text => {
        println("Response: " + text)
      })
    }

  def loadSelectedSketch: Unit = 
    for { (id, _) <- selectedSketch } {

      import upickle.default._
      import opetopic.net.LoadSketchRequest

      val req = LoadSketchRequest(id)

      dom.ext.Ajax.post(
        url = "/getSketch",
        data = write(req),
        headers = Map(
          ("X-Requested-With" -> "*"),
          ("CSRF-Token" -> "nocheck")
        ),
        withCredentials = true
      ).map(_.responseText).foreach(json => {

        val c : SComplex[Option[SimpleMarker]] =
          complexFromJson[Option[SimpleMarker]](upickle.json.read(json))

        editor.newEditor(c)

      })
    }

  def saveSketch: Unit = 
    for {
      c <- viewer.complex
    } {

      import org.scalajs.dom
      import upickle.default._
      import opetopic.net.SaveSketchRequest

      jQuery("#save-modal").modal(lit(
        onApprove = () => {

          val req =
            SaveSketchRequest(
              jQuery("#sketch-name-input").value.asInstanceOf[String],
              jQuery("#sketch-path-input").value.asInstanceOf[String],
              jQuery("#sketch-desc-input").value.asInstanceOf[String],
              complexToJson(c)
            )

          dom.ext.Ajax.post(
            url = "/saveSketch",
            data = write(req),
            headers = Map(
              ("X-Requested-With" -> "*"),
              ("CSRF-Token" -> "nocheck")
            ),
            withCredentials = true
          ) // .map(_.responseText).foreach(println)

        }
      )).modal("show")

    }

  def colorTripleGen(color: String) : (String, String, String) = 
    color match {
      case "red"    => ("#DB2828", "#DB2828", "#DB2828")
      case "orange" => ("#F2711C", "#F2711C", "#F2711C")
      case "yellow" => ("#FBBD08", "#FBBD08", "#FBBD08")
      case "olive"  => ("#B5CC18", "#B5CC18", "#B5CC18")
      case "green"  => ("#21BA45", "#21BA45", "#21BA45")
      case "teal"   => ("#00B5AD", "#00B5AD", "#00B5AD")
      case "blue"   => ("#2185D0", "#2185D0", "#2185D0")
      case "violet" => ("#6435C9", "#6435C9", "#6435C9")
      case "purple" => ("#A333C8", "#A333C8", "#A333C8")
      case "pink"   => ("#E03997", "#E03997", "#E03997")
      case "brown"  => ("#A5673F", "#A5673F", "#A5673F")
      case "grey"   => ("lightgrey", "darkgrey", "grey")
      case "black"  => ("#1B1C1D", "#1B1C1D", "#1B1C1D")
      case _ => ("#FFFFFF", "#F3F4F5", "#DCDDDE")
    }

  def colorReverseLookup(str: String) : String = 
    str match {
      case "#DB2828" => "red"
      case "#F2711C" => "orange"
      case "#FBBD08" => "yellow"
      case "#B5CC18" => "olive"
      case "#21BA45" => "green"
      case "#00B5AD" => "teal"
      case "#2185D0" => "blue"
      case "#6435C9" => "violet"
      case "#A333C8" => "purple"
      case "#E03997" => "pink"
      case "#A5673F" => "brown"
      case "lightgrey" => "grey"
      case "#1B1C1D" => "black"
      case "#000000" => "black"
      case _ => "white"
    }

  def showProps(c: EditorCell): Unit = {

    for { f <- c.face } {
      if (f.length > 1) {

        val dim = f.length - 1

        if (dim > 0) {
          frameViewer.firstPanel = Some(dim)
          frameViewer.lastPanel = Some(dim)
        }

        frameViewer.complex = Some(f)

      } 
    }

    c.label match {
      case None => {
        showFill("white")
        showStroke("black")
        jQuery("#label-input").value("")
      }
      case Some(mk) => {
        showFill(colorReverseLookup(mk.colorSpec.fill))
        showStroke(colorReverseLookup(mk.colorSpec.stroke))
        jQuery("#label-input").value(mk.lbl)
      }
    }
  }

  def showFill(str: String): Unit = {
    jQuery("#fill-color-btn").removeClass(fillColor).addClass(str).popup("hide")
    fillColor = str
  }

  def showStroke(str: String): Unit = {
    jQuery("#stroke-color-btn").removeClass(strokeColor).addClass(str).popup("hide")
    strokeColor = str
  }

}
