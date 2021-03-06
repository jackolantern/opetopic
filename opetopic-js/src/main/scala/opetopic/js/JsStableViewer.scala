/**
  * JsStableViewer.scala - Stable Complex Viewer
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js

import org.scalajs.dom
import org.scalajs.jquery._
import scalatags.JsDom.all._
import scala.scalajs.js.Dynamic.{literal => lit}

import opetopic._
import opetopic.ui._
import JsDomFramework._
import JQuerySemanticUI._

class JsStableViewer[A: Renderable] {

  type GalleryType = SimpleActiveGallery[A, JsDomFramework.type]
  type CellType = GalleryType#SimpleActiveCell

  var activeGallery: Option[GalleryType] = None
  var activeComplex: Option[SComplex[A]] = None

  var onSelectAsRoot: CellType => Unit = { _ => () }
  var onHover: CellType => Unit = { _ => () }
  var onUnhover: CellType => Unit = { _ => () }

  var firstPanel: Option[Int] = None
  var lastPanel: Option[Int] = None

  def complex: Option[SComplex[A]] = activeComplex
  def complex_=(oc: Option[SComplex[A]]): Unit = 
    oc match {
      case None => jQuery(uiElement).empty()
      case Some(cc) => {

        val g: GalleryType =
          new SimpleActiveGallery[A, JsDomFramework.type](JsDomFramework)(cc)

        g.hoverCofaces = false
        g.firstPanel = firstPanel
        g.lastPanel = lastPanel

        g.onSelectAsRoot = (c: CellType) => onSelectAsRoot(c)
        g.onHover = (c: CellType) => onHover(c)
        g.onUnhover = (c: CellType) => onUnhover(c)

        jQuery(uiElement).empty().append(g.element.uiElement)

        activeGallery = Some(g)
        activeComplex = Some(cc)
        g.renderAll
        resizeViewer

      }
    }

  val uiElement = 
    div(style := "min-height: 200px").render

  var viewerWidth : Int = 0
  var viewerHeight : Int = 190

  def initialize: Unit = {
    jQuery(dom.window).on("resize", () => { resizeViewer })
    resizeViewer
  }

  def resizeViewer: Unit = {

    viewerWidth = jQuery(uiElement).width.toInt

    for { gallery <- activeGallery } { 
      gallery.galleryViewport.width = viewerWidth
      gallery.galleryViewport.height = viewerHeight
    }

  }

  def refreshViewer: Unit = 
    activeGallery.foreach(_.renderAll)

  def rootAction(f: CellType => Unit): Unit = 
    for {
      g <- activeGallery
      r <- g.selectionRoot
    } { f(r) }

}
