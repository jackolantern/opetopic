/**
  * JsDomFramework.scala - Dom Backend for Opetopic Rendering
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js

import scala.scalajs._
import org.scalajs.dom._

import org.scalajs.dom.raw.SVGElement
import org.scalajs.dom.raw.SVGGElement
import org.scalajs.dom.raw.SVGSVGElement
import org.scalajs.dom.raw.SVGPathElement
import org.scalajs.dom.raw.SVGRectElement
import org.scalajs.dom.raw.SVGPolygonElement
import org.scalajs.dom.raw.SVGTransformable

import opetopic.ui._

object JsDomFramework extends ActiveFramework {

  val svgns = "http://www.w3.org/2000/svg"

  val isNumeric: Numeric[Int] = implicitly[Numeric[Int]]
  val isOrdered: Ordering[Int] = implicitly[Ordering[Int]]

  type Size = Int

  def half(u: Int) : Int = u / 2

  type UIElementType = SVGElement

  type PathType = JsDomPath
  type TextType = JsDomText
  type GroupType = JsDomGroup
  type PolygonType = JsDomPolygon
  type ViewportType = JsDomViewport
  type RectangleType = JsDomRectangle

  abstract class JsDomElement extends Element {

    def transform(t: Transform) = {
      // There may be a better way to do this with the dom api ...
      val translateStr = "translate(" ++ t.translateX.toString ++ ", " ++ t.translateY.toString ++ ")"
      val scaleStr = "scale(" ++ t.scaleX.toString ++ ", " ++ t.scaleY.toString ++ ")"
      uiElement.setAttributeNS(null, "transform", translateStr ++ " " ++ scaleStr)
    }

    implicit def mouseEventEncode(ev: MouseEvent) : UIMouseEvent = 
      new UIMouseEvent {
        def button = ev.button
        def altKey = ev.altKey
        def ctrlKey = ev.ctrlKey
        def shiftKey = ev.shiftKey
      }

    var onClick : UIMouseEvent => Unit = { _ => () }
    var onMouseOut : UIMouseEvent => Unit = { _ => () }
    var onMouseOver : UIMouseEvent => Unit = { _ => () }
    var onKeyDown : UIKeyEvent => Unit = { _ => () }

    def installHandlers(el: SVGElement) : Unit = {
      el.onclick = { (e : MouseEvent) => onClick(e) }
      el.onmouseover = { (e : MouseEvent) => onMouseOver(e) }
      el.onmouseout = { (e : MouseEvent) => onMouseOut(e) }
    }

  }

  def makeMouseInvisible(el: Element) : Element = {
    el.uiElement.setAttributeNS(null, "pointer-events", "none") ; el
  }

  abstract class JsDomParentElement extends JsDomElement {

    private var myChildren: Seq[Element] = Seq()

    def children: Seq[Element] = myChildren
    def children_=(els: Seq[Element]) : Unit = {
      var last = uiElement.lastChild

      while (last != null) { 
        uiElement.removeChild(last)
        last = uiElement.lastChild
      }

      for { el <- els } {
        uiElement.appendChild(el.uiElement) 
      }

      myChildren = els
    }

  }

  //============================================================================================
  // VIEWPORTS
  //

  def viewport : ViewportType = 
    new JsDomViewport

  def viewport(width: Int, height: Int, bounds: Bounds, elems: Element*) : ViewportType = 
    new JsDomViewport(bounds, elems)

  class JsDomViewport extends JsDomParentElement with Viewport {

    def this(bounds: Bounds, elems: Seq[Element]) = {
      this ; viewX = bounds.x ; viewY = bounds.y 
      viewWidth = bounds.width ; viewHeight = bounds.height
      children = elems
    }

    val svgSvg = 
      document.createElementNS(svgns, "svg").
        asInstanceOf[SVGSVGElement]

    // val defs = document.createElementNS(svgns, "defs")
    // val filter = document.createElementNS(svgns, "filter")
    // val feColorMatrix = document.createElementNS(svgns, "feColorMatrix")

    // filter.setAttributeNS(null, "id", "rectHover")
    // filter.setAttributeNS(null, "x", "0")
    // filter.setAttributeNS(null, "y", "0")

    // feColorMatrix.setAttributeNS(null, "in", "SourceGraphic")
    // feColorMatrix.setAttributeNS(null, "type", "hueRotate")
    // feColorMatrix.setAttributeNS(null, "values", "90")

    // filter.appendChild(feColorMatrix)
    // defs.appendChild(filter)
    // svgSvg.appendChild(defs)

    installHandlers(svgSvg)

    val uiElement = svgSvg

    def width: Int = svgSvg.getAttributeNS(null, "width").toInt
    def width_=(i: Int) = svgSvg.setAttributeNS(null, "width", i.toString)

    def height: Int = svgSvg.getAttributeNS(null, "height").toInt
    def height_=(i: Int) = svgSvg.setAttributeNS(null, "height", i.toString)

    private var myViewX: Int = 0
    private var myViewY: Int = 0
    private var myViewWidth: Int = 0
    private var myViewHeight: Int = 0

    def viewX: Int = myViewX
    def viewX_=(i: Int) = { myViewX = i ; setViewboxString }

    def viewY: Int = myViewY
    def viewY_=(i: Int) = { myViewY = i ; setViewboxString }

    def viewWidth: Int = myViewWidth
    def viewWidth_=(i: Int) = { myViewWidth = i ; setViewboxString }

    def viewHeight: Int = myViewHeight
    def viewHeight_=(i: Int) = { myViewHeight = i ; setViewboxString }

    def viewBoxString : String = 
      viewX.toString ++ " " ++ viewY.toString ++ " " ++
        viewWidth.toString ++ " " ++ viewHeight.toString

    def setViewboxString : Unit =
      svgSvg.setAttributeNS(null, "viewBox", viewBoxString)

    def getViewboxString : String = 
      svgSvg.getAttributeNS(null, "viewBox")

  }

  //============================================================================================
  // POLYGONS
  //

  def polygon : PolygonType = 
    new JsDomPolygon

  def polygon(stroke: String, strokeWidth: Int, fill: String, pts: List[(Int, Int)]) : PolygonType = {
    val p = new JsDomPolygon
    p.stroke = stroke ; p.strokeWidth = strokeWidth ; p.fill = fill ; p.points = pts
    p
  }

  class JsDomPolygon extends JsDomElement with Polygon {

    val svgPoly = 
      document.createElementNS(svgns, "polygon").
        asInstanceOf[SVGPolygonElement]

    installHandlers(svgPoly)

    val uiElement = svgPoly

    var myPoints : List[(Int, Int)] = Nil

    def points: List[(Int, Int)] = myPoints
    def points_=(pts: List[(Int, Int)]): Unit = {
      myPoints = pts
      svgPoly.setAttributeNS(null, "points", 
        (pts map ({ case (x, y) => x.toString ++ "," ++ y.toString })).mkString(" ")
      )
    }

    def stroke: String = svgPoly.getAttributeNS(null, "stroke")
    def stroke_=(s: String): Unit = svgPoly.setAttributeNS(null, "stroke", s)

    def strokeWidth: Int = svgPoly.getAttributeNS(null, "stroke-width").toInt
    def strokeWidth_=(i : Int): Unit = svgPoly.setAttributeNS(null, "stroke-width", i.toString)

    def fill: String = svgPoly.getAttributeNS(null, "fill")
    def fill_=(s: String): Unit = svgPoly.setAttributeNS(null, "fill", s)

  }

  //============================================================================================
  // RECTANGLES
  //

  def rect : RectangleType = 
    new JsDomRectangle

  def rect(x: Int, y: Int, width: Int, height: Int, r: Int, stroke: String, strokeWidth: Int, fill: String) : RectangleType = {
    val rct = new JsDomRectangle
    rct.x = x ; rct.y = y ; rct.width = width ; rct.height = height ; rct.r = r
    rct.stroke = stroke ; rct.strokeWidth = strokeWidth ; rct.fill = fill
    rct
  }

  class JsDomRectangle extends JsDomElement with Rectangle {

    val svgRect = 
      document.createElementNS(svgns, "rect").
        asInstanceOf[SVGRectElement]

    installHandlers(svgRect)

    val uiElement = svgRect

    def x: Int = svgRect.getAttributeNS(null, "x").toInt
    def x_=(i : Int): Unit = svgRect.setAttributeNS(null, "x", i.toString)

    def y: Int = svgRect.getAttributeNS(null, "y").toInt
    def y_=(i : Int): Unit = svgRect.setAttributeNS(null, "y", i.toString)

    def width: Int = svgRect.getAttributeNS(null, "width").toInt
    def width_=(i : Int): Unit = svgRect.setAttributeNS(null, "width", i.toString)

    def height: Int = svgRect.getAttributeNS(null, "height").toInt
    def height_=(i : Int): Unit = svgRect.setAttributeNS(null, "height", i.toString)

    def r: Int = svgRect.getAttributeNS(null, "rx").toInt
    def r_=(i: Int) = {
      svgRect.setAttributeNS(null, "rx", i.toString)
      svgRect.setAttributeNS(null, "ry", i.toString)
    }

    def stroke: String = svgRect.getAttributeNS(null, "stroke")
    def stroke_=(s: String): Unit = svgRect.setAttributeNS(null, "stroke", s)

    def strokeWidth: Int = svgRect.getAttributeNS(null, "stroke-width").toInt
    def strokeWidth_=(i : Int): Unit = svgRect.setAttributeNS(null, "stroke-width", i.toString)

    def fill: String = svgRect.getAttributeNS(null, "fill")
    def fill_=(s: String): Unit = svgRect.setAttributeNS(null, "fill", s)

  }

  //============================================================================================
  // GROUPS
  //

  def group : GroupType = 
    new JsDomGroup 

  def group(els: Element*) : GroupType = 
    new JsDomGroup(els)

  class JsDomGroup extends JsDomParentElement with Group {

    def this(els: Seq[Element]) = {
      this ; children = els
    }

    val svgGroup =
      document.createElementNS(svgns, "g").
        asInstanceOf[SVGGElement]

    val uiElement = svgGroup

  }

  //============================================================================================
  // PATHS
  //

  def path : PathType = 
    new JsDomPath

  def path(d: String, stroke: String, strokeWidth: Int, fill: String) : PathType = 
    new JsDomPath(d, stroke, strokeWidth, fill)

  class JsDomPath extends JsDomElement with Path {

    def this(d: String, stroke: String, strokeWidth: Int, fill: String) = {
      this ; this.d = d ; this.stroke = stroke ; this.strokeWidth = strokeWidth ; this.fill = fill
    }

    val svgPath =
      document.createElementNS(svgns, "path").
        asInstanceOf[SVGPathElement]

    val uiElement = svgPath

    def d: String = svgPath.getAttributeNS(null, "d")
    def d_=(s: String): Unit = svgPath.setAttributeNS(null, "d", s)

    def stroke: String = svgPath.getAttributeNS(null, "stroke")
    def stroke_=(s: String): Unit = svgPath.setAttributeNS(null, "stroke", s)

    def strokeWidth: Int = svgPath.getAttributeNS(null, "stroke-width").toInt
    def strokeWidth_=(i : Int): Unit = svgPath.setAttributeNS(null, "stroke-width", i.toString)

    def fill: String = svgPath.getAttributeNS(null, "fill")
    def fill_=(s: String): Unit = svgPath.setAttributeNS(null, "fill", s)

  }


  //============================================================================================
  // TEXT
  //

  def text(str: String) : BoundedElement = {
    new BoundedElement {
      val element = new JsDomText(str)
      val bounds = element.bounds
    }
  }

  class JsDomText extends JsDomElement with Text {

    def this(str: String) = {
      this ; this.text = str
    }

    def uiElement = myGroup.svgGroup

    private var myText: String = ""
    private var myBounds: Bounds = Bounds(0,0,0,0)
    private var myGroup: JsDomGroup = null

    def text: String = myText 
    def text_=(s: String) = {
      val (g, b) = renderTextGroup(s, AsanaMathMain, "black", 1, "black")
      myGroup = g
      myBounds = b

      // If there are event handlers attached to this text element, 
      // they will get lost when you update the text, I think.
      // Watch out for this ...

    }

    def bounds: Bounds = myBounds

  }

  //============================================================================================
  // TOASTS
  //

  var onToast : String => Unit = { _ => () }

  override def toast(str: String) : Unit = 
    onToast(str)

}
