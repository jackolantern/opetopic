/**
  * Sketchpad.scala - Opetopic Sketchpad Application
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js.sketchpad

import scala.scalajs.{js => sjs}
import scala.scalajs.js.JSApp
import org.scalajs.dom._
import org.scalajs.jquery._
import scalatags.JsDom.all._

import opetopic._
import opetopic.ui._
import opetopic.js._
import syntax.complex._
import JsDomFramework._

// import org.denigma.codemirror.extensions.EditorConfig
// import org.denigma.codemirror.{CodeMirror, EditorConfiguration}
// import org.scalajs.dom.raw.HTMLTextAreaElement

import opetopic.js.JQuerySemanticUI._

object Sketchpad extends JSApp {

  // Setup CodeMirror

  // val params : EditorConfiguration = 
  //   EditorConfig.lineNumbers(true) 

  // val codeArea = 
  //   document.getElementById("code-area").
  //     asInstanceOf[HTMLTextAreaElement]

  // val editor = CodeMirror.fromTextArea(codeArea, params)

  def main : Unit = {

    println("Launched Opetopic Sketchpad.")

    jQuery("#new-tab").click((e : JQueryEventObject) => { addEditorTab })
    jQuery("#sketch-prop-tab-menu .item").tab()
    jQuery("#cell-props-menu .item").tab()

    jQuery("#fill-color-btn").popup(sjs.Dynamic.literal(
      popup = jQuery(".color-select.popup"),
      movePopup = false,
      on = "click",
      onShow = () => { isFill = true }
    ))

    jQuery("#stroke-color-btn").popup(sjs.Dynamic.literal(
      popup = jQuery(".color-select.popup"),
      movePopup = false,
      on = "click",
      onShow = () => { isFill = false }
    ))

    jQuery("#label-input").on("input", () => {
      updateLabel
    })

    jQuery(".color-select.popup button").on("click", (e: JQueryEventObject) => {

      val color= jQuery(e.target).attr("data-color").toString

      if (isFill) {

        jQuery("#fill-color-btn").removeClass(fillColor).addClass(color).popup("hide")
        fillColor = color
        updateFillColor

      } else {
        jQuery("#stroke-color-btn").removeClass(strokeColor).addClass(color).popup("hide")
        strokeColor = color
      }

    })

    jQuery("#snapshot-btn").on("click", () => { takeSnapshot })

    addEditorTab

  }

  var tabCount: Int = 0

  var isFill: Boolean = true
  var fillColor: String = "white"
  var strokeColor: String = "black"

  def addEditorTab: Unit = {

    val editorTab = new EditorTab

    tabCount += 1
    val tc = tabCount.toString
    val tabName = "tab-" ++ tc

    val tabItem = a(cls := "item", "data-tab".attr := tabName)(tc).render
    val tab = div(cls := "ui tab", "data-tab".attr := tabName)(
      editorTab.uiElement
    ).render

    jQuery(".right.menu").before(tabItem)
    jQuery("#sketch-tabs").append(tab)

    jQuery(tabItem).tab(sjs.Dynamic.literal(
      onVisible = (s: String) => { activeTab = Some(editorTab) }
    ))

    jQuery(tabItem).click()

  }

  def unescapeUnicode(str: String): String =
    """\\u([0-9a-fA-F]{4})""".r.replaceAllIn(str,
      m => Integer.parseInt(m.group(1), 16).toChar.toString)

  def updateLabel: Unit = 
    for {
      tab <- activeTab
      bs <- tab.activeBox
    } {

      val box = bs.value
      val labelVal = jQuery("#label-input").value().toString

      box.optLabel match {
        case None => box.optLabel = Some(Marker(bs.n)(unescapeUnicode(labelVal), DefaultColorSpec))
        case Some(mk) => box.optLabel = Some(mk.withLabel(unescapeUnicode(labelVal)))
      }

      box.panel.refresh
      tab.editor.refreshGallery

    }

  def updateFillColor: Unit = 
    for {
      tab <- activeTab
      bs <- tab.activeBox
    } {

      // val (f, fh, fs) = CellMarker.colorTripleGen(fillColor)

      // bs.value.optLabel match {
      //   case None => bs.value.optLabel = Some(CellMarker("", 
      //     DefaultColorSpec.copy(fill = f, fillHovered = fh, fillSelected = fs)
      //   ))
      //   case Some(mk) => bs.value.optLabel = Some(mk.copy(
      //     colorSpec = mk.colorSpec.copy(fill = f, fillHovered = fh, fillSelected = fs)
      //   ))
      // }

      // bs.value.panel.refresh
      // tab.editor.refreshGallery

    }

  val propConfig: GalleryConfig =
    GalleryConfig(
      panelConfig = defaultPanelConfig,
      width = 900,
      height = 150,
      spacing = 1500,
      minViewX = Some(60000),
      minViewY = Some(6000),
      spacerBounds = Bounds(0, 0, 600, 600)
    )

  var activeTab: Option[EditorTab] = None

  def refreshFacePreview: Unit = 
    for {
      tab <- activeTab
      bs <- tab.activeBox
    } {

      import tab._
      implicit val bsDim = bs.n

      val lblStr = (bs.value.optLabel map (_.label)) getOrElse ""
      jQuery("#label-input").value(lblStr)

      @natElim
      def doRefresh[N <: Nat](n: N)(box: tab.editor.CardinalCellBox[N]) : Unit = {
        case (Z, box) => {
          for {
            lc <- box.labelComplex
          } {

            val gallery = ActiveGallery(propConfig, lc)
            jQuery("#face-pane").empty().append(gallery.element.uiElement)
            jQuery("#edge-prop-tab").empty().text("Cell is an object")

          }
        }
        case (S(p: P), box) => {
          for {
            lc <- box.labelComplex
          } {

            val panel = ActivePanel(lc.tail.head)

            val gallery = ActiveGallery(propConfig, lc)
            jQuery("#face-pane").empty().append(gallery.element.uiElement)
            jQuery("#edge-prop-tab").empty().append(panel.element.uiElement)

          }
        }
      }

      doRefresh(bs.n)(bs.value)

    }

  def takeSnapshot: Unit = 
    for {
      tab <- activeTab
      bs <- tab.activeBox
      lblCmplx <- bs.value.labelComplex
    } {

      // val exporter = new SvgExporter(lblCmplx)

      // jQuery(".ui.modal.svgexport").find("#exportlink").
      //   attr(sjs.Dynamic.literal(href = "data:text/plain;charset=utf-8," ++
      //     sjs.URIUtils.encodeURIComponent(exporter.svgString)))

      // jQuery(".ui.modal.svgexport").modal("show")

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
      case "grey"   => ("#767676", "#767676", "#767676")
      case "black"  => ("#1B1C1D", "#1B1C1D", "#1B1C1D")
      case _ => ("#FFFFFF", "#F3F4F5", "#DCDDDE")
    }

}
