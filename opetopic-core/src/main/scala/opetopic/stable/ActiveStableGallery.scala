/**
  * ActiveStableGallery.scala - Active Gallery Implementation
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.stable

import scala.collection.mutable.ListBuffer

import opetopic._
import opetopic.ui._

trait HasActiveStableGallery extends HasStableGallery { self: ActiveFramework => 

  import isNumeric._

  class ActiveStableGallery[A](
    val config: StableGalleryConfig, 
    val renderer: A => BoundedElement
  ) extends StableGallery[A] {

    import config._

    type CellType = ActiveCell
    type PanelType = ActivePanel

    val panels: ListBuffer[ActivePanel] = ListBuffer()
    val galleryViewport = viewport

    var onCellClick : ActiveCell => Unit = (_ => ())

    def uiElement: UIElementType =
      galleryViewport.uiElement

    def initialize: Unit = {
      for { p <- panels } { p.initialize }
      galleryViewport.children = panels.map(_.panelGroup)
    }

    def refreshAll: Unit = {
      for { 
        _ <- layoutPanels
      } {

        val ps = panels.toList
        val els = ps.map(p => { p.render ; p.element })

        galleryViewport.width = config.width
        galleryViewport.height = config.height
        galleryViewport.setBounds(galleryBounds)

        galleryViewport.children = els

      }
    }

    class ActivePanel(var baseCell: ActiveCell) extends Panel {

      val panelGroup = group
      def element = panelGroup

      def initialize: Unit = {

        val (extCells, intCells) = 
          baseCell.interiorCells.partition(_.isExternal)

        val edges = 
          baseCell.target match {
            case None => List()
            case Some(tgt) => tgt.interiorCells
          }

        panelGroup.children = 
          intCells.map(_.cellGroup) ++
            edges.map(_.edgePath) ++
            extCells.map(_.cellGroup)

      }

    }

    class ActiveCell(var label: A) extends VisualCell { thisCell =>

      //
      //  Cell Structure Variables
      //

      var labelElement: BoundedElement = 
        renderer(label)

      //
      //  Visual Elements
      //

      val boxRect = {
        val r = rect
        r.r = cornerRadius
        r.strokeWidth = strokeWidth
        r
      }

      boxRect.onMouseOver = { (e : UIMouseEvent) => () }
      boxRect.onMouseOut = { (e : UIMouseEvent) => () }
      boxRect.onClick = { (e : UIMouseEvent) => onCellClick(thisCell) }

      val edgePath = {
        val p = path
        p.stroke = "black"
        p.strokeWidth = strokeWidth
        p.fill = "none"
        makeMouseInvisible(p)
        p
      }

      val cellGroup = group

      def renderCell: Unit = {

        cellGroup.children = Seq(boxRect, labelElement.element)

        boxRect.fill = "white"
        boxRect.stroke = "black"

        boxRect.x = x ; boxRect.y = y ; boxRect.width = width ; boxRect.height = height

        val labelXPos = x + width - strokeWidth - internalPadding - labelWidth
        val labelYPos = y + height - strokeWidth - internalPadding - labelHeight

        translate(labelElement.element, labelXPos - labelBounds.x, labelYPos - labelBounds.y)

      }

      def renderEdge : Unit = {
        edgePath.d = pathString
      }

      override def toString: String = 
        "ActiveCell(" + label.toString + ")"

    }

    object StableBuilder extends ComplexBuilder[A, ActiveCell] {

      def newCell(a: A): ActiveCell = new ActiveCell(a)

      def newCell(a: A, d: Nat): ActiveCell = {
        val cell = newCell(a)
        cell.dim = natToInt(d)
        cell
      }

      def registerBaseCell(cell: ActiveCell): Unit = {
        panels += new ActivePanel(cell)
      }

    }

    object StableFactory extends CellFactory[A, ActiveCell] {
      def newCell(a: A, d: Int): ActiveCell = {
        val c = new ActiveCell(a)
        c.dim = d
        c
      }
    }

  }

}
