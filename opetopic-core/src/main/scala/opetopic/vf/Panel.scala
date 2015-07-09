/**
  * Panel.scala - An abstract Panel Component
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.vf

import scala.collection.mutable.ListBuffer

import opetopic._
import TypeDefs._

import syntax.tree._
import syntax.nesting._

trait PanelDeps[U] 
    extends VisualFramework[U]
    with CellBoxDefn[U]
    with CellEdgeDefn[U]
    with PanelDefn[U]

trait PanelDefn[U] { vf: PanelDeps[U] =>

  import isFractional._
 
  case class PanelConfig(
    val internalPadding : U,
    val externalPadding : U,
    val halfLeafWidth : U,
    val halfStrokeWidth : U,
    val cornerRadius : U
  )

  abstract class Panel[A, N <: Nat](cfg: PanelConfig)(nst: Nesting[View[A], N]) extends Component { thisPanel =>

    //============================================================================================
    // RENDERING OPTIONS
    //

    def internalPadding : U = cfg.internalPadding
    def externalPadding : U = cfg.externalPadding

    def halfLeafWidth : U = cfg.halfLeafWidth
    def halfStrokeWidth : U = cfg.halfStrokeWidth

    def fullStrokeWidth = fromInt(2) * halfStrokeWidth
    def leafWidth = fromInt(2) * halfLeafWidth

    def cornerRadius : U = cfg.cornerRadius

    //============================================================================================
    // INITIALIZATION
    //

    val boxNesting : Nesting[CellBox[A, N], N] = 
      generateBoxes(nst.dim)(nst)

    def generateBoxes(n: N)(nst: Nesting[View[A], N]) : Nesting[CellBox[A, N], N] =
      Nesting.elimWithAddress[View[A], Nesting[CellBox[A, N], N], N](n)(nst)({
        case (view, addr) => Nesting.external(n)(CellBox(n)(thisPanel, view, addr, true))
      })({
        case (view, addr, cn) => Box(CellBox(n)(thisPanel, view, addr, false), cn)
      })

  }

  object Panel {

    val defaultConfig =
      PanelConfig(
        internalPadding = fromInt(5),
        externalPadding = fromInt(5),
        halfLeafWidth = fromInt(8),
        halfStrokeWidth = fromInt(1),
        cornerRadius = fromInt(4)
      )

    @natElim
    def apply[A, N <: Nat](n: N)(nst: Nesting[View[A], N]) : Panel[A, N] = {
      case (Z, nst) => new ObjectPanel(defaultConfig)(nst)
      case (S(p), nst) => new NestingPanel(defaultConfig)(nst)
    }

    def apply[A, N <: Nat](nst: Nesting[A, N])(implicit isViewable: Viewable[A]) : Panel[A, N] = 
      Panel(nst.dim)(nst map (isViewable.view(_)))

  }

  class ObjectPanel[A](cfg: PanelConfig)(nst: Nesting[View[A], _0]) extends Panel[A, _0](cfg)(nst) {

    def layout(nst : Nesting[CellBox[A, _0], _0]) : CellBox[A, _0] =
      nst match {
        case Obj(b) => { b.clear ; b }
        case Box(b, Pt(n)) => {

          val internalMarker = layout(n)

          b.clear

          b.leftInteriorMargin = internalMarker.leftMargin
          b.rightInteriorMargin = internalMarker.rightMargin
          b.interiorHeight = internalMarker.height

          internalMarker.shiftUp(fullStrokeWidth + b.labelHeight)

          b.horizontalDependants += internalMarker
          b.verticalDependants += internalMarker

          b

        }
      }

    def render: Seq[ElementType] = 
      (boxNesting.nodes map (_.render)).flatten

  }

  class NestingPanel[A, P <: Nat](cfg: PanelConfig)(nst: Nesting[View[A], S[P]]) extends Panel[A, S[P]](cfg)(nst) { thisPanel => 

    //============================================================================================
    // RENDERING
    //

    def render: Seq[ElementType] = 
      (boxNesting.nodes map (_.render)).flatten ++
        (edgeNesting.nodes map (_.render)).flatten

    //============================================================================================
    // EDGE NESTING GENERATION
    //

    val edgeNesting : Nesting[CellEdge[A, S[P]], P] = 
      generateEdges(boxNesting.dim.pred)(boxNesting)

    def generateEdges(p: P)(nst: Nesting[CellBox[A, S[P]], S[P]]) : Nesting[CellEdge[A, S[P]], P] = {

      val edgeComp =
        nst match {
          case Dot(_, sp) => fail("Base nesting is external!")
          case Box(_, cn) =>
            for {
              spine <- Nesting.spineFromCanopy(cn)
              res <- Tree.graftRec(p)(spine)(ad => {
                val edge = cellEdge(S(p))(thisPanel)
                succeed(Nesting.external(p)(edge))
              })({ case (mk, cn) => {
                val edge = cellEdge(S(p))(thisPanel)
                mk.outgoingEdge = Some(edge)
                succeed(Box(edge, cn))
              }})
            } yield res
        }

      import scalaz.-\/
      import scalaz.\/-

      edgeComp match {
        case \/-(enst) => enst
        case _ => {
          // A dummy return, since this is an error ...
          Nesting.external(p)(cellEdge(S(p))(thisPanel))
        }
      }

    }

    def edgeLayoutTree : ShapeM[Tree[LayoutMarker, P]] =
      for {
        spine <- Nesting.spineFromDerivative(edgeNesting, Zipper.globDerivative(edgeNesting.dim))
      } yield spine.map(edge => {
        new LayoutMarker {

          val element = new EdgeStartMarker(edge)
          val rootEdge = edge
          val exterior = true

          override def leftInternalMargin = halfLeafWidth
          override def rightInternalMargin = halfLeafWidth

        }
      })

    //============================================================================================
    // LAYOUT 
    //

    def layout : ShapeM[Unit] =
      for {
        et <- edgeLayoutTree
        mk <- layoutNesting(boxNesting, et)
      } yield {

        val baseBox = boxNesting.baseValue

        // Set the positions of incoming edges
        for { edge <- edgeNesting } {
          edge.edgeStartY = baseBox.y - (fromInt(2) * externalPadding)
        }

        // Set the position of the outgoing edge
        mk.rootEdge.edgeEndY = baseBox.rootY + (fromInt(2) * externalPadding)

      }

    def layoutNesting(nst : Nesting[CellBox[A, S[P]], S[P]], lvs : Tree[LayoutMarker, P]) : ShapeM[LayoutMarker] =
      nst match {
        case Dot(bx, d) =>
          for {
            outgoingEdge <- fromOpt(
              bx.outgoingEdge,
              new ShapeError("Missing outgoing edge for" ++ bx.toString)
            )
          } yield {

            bx.clear

            val newEdgeMarker = new EdgeStartMarker(outgoingEdge)

            bx.horizontalDependants += newEdgeMarker
            bx.verticalDependants += newEdgeMarker

            val leafMarkers = lvs.nodes
            val leafCount = leafMarkers.length

            // Zeroed out for external dots
            bx.leftInteriorMargin = zero
            bx.rightInteriorMargin = zero
            bx.interiorHeight = zero

            val marker =
              if (leafCount == 0) {  // This is a drop. Simply return an appropriate marker ...

                new LayoutMarker {

                  val element = bx
                  val exterior = false
                  val rootEdge = outgoingEdge

                  override def height = bx.height
                  override def leftInternalMargin = bx.leftMargin
                  override def rightInternalMargin = bx.rightMargin

                }

              } else { // We have children.  Arrange them and calculate the marker.

                val isOdd = (leafCount & 1) != 0

                val firstMarker = leafMarkers.head
                val lastMarker = leafMarkers.last

                val midMarker = leafMarkers(leafCount / 2)

                if (isOdd) {

                  midMarker.rootEdge.edgeEndX = bx.rootX
                  midMarker.rootEdge.edgeEndY = bx.rootY - bx.height

                  val edgeMarker = new EdgeEndMarker(midMarker.rootEdge)

                  bx.horizontalDependants += edgeMarker
                  bx.verticalDependants += edgeMarker

                  bx.horizontalDependants += midMarker.element

                }

                if (leafCount > 1) {

                  val leftChildren = leafMarkers.slice(leafCount / 2 + (leafCount & 1), leafCount)
                  val rightChildren = leafMarkers.slice(0, leafCount / 2)

                  val leftChild = leftChildren.head
                  val rightChild = rightChildren.last

                  val midLeftOffset = if (isOdd) midMarker.leftMargin else zero
                  val midRightOffset = if (isOdd) midMarker.rightMargin else zero

                  val leftChildShift = max(midLeftOffset + externalPadding + leftChild.rightMargin, bx.leftMargin + externalPadding)
                  val rightChildShift = max(midRightOffset + externalPadding + rightChild.leftMargin, bx.rightMargin + externalPadding)

                  def doLeftPlacement(marker : LayoutMarker, shift : U) : Unit = {

                    marker.element.shiftLeft(shift)
                    bx.horizontalDependants += marker.element

                    val edgeMarker = new EdgeEndMarker(marker.rootEdge)

                    marker.rootEdge.edgeEndX = bx.x
                    marker.rootEdge.edgeEndY = bx.y + fullStrokeWidth + internalPadding + bx.halfLabelHeight

                    bx.horizontalDependants += edgeMarker
                    bx.verticalDependants += edgeMarker

                  }

                  def doRightPlacement(marker : LayoutMarker, shift : U) : Unit = {

                    marker.element.shiftRight(shift)
                    bx.horizontalDependants += marker.element

                    val edgeMarker = new EdgeEndMarker(marker.rootEdge)

                    marker.rootEdge.edgeEndX = bx.x + bx.width
                    marker.rootEdge.edgeEndY = bx.y + fullStrokeWidth + internalPadding + bx.halfLabelHeight

                    bx.horizontalDependants += edgeMarker
                    bx.verticalDependants += edgeMarker

                  }

                  doLeftPlacement(leftChild, leftChildShift)
                  doRightPlacement(rightChild, rightChildShift)

                  val leftEdge =
                    (leftChildren.tail foldLeft (leftChildShift + leftChild.leftMargin))({
                      case (leftShift, currentMarker) => {
                        val thisShift = leftShift + externalPadding + currentMarker.rightMargin
                        doLeftPlacement(currentMarker, thisShift)
                        thisShift + currentMarker.leftMargin
                      }
                    })

                  val rightEdge =
                    (rightChildren.init foldRight (rightChildShift + rightChild.rightMargin))({
                      case (currentMarker, rightShift) => {
                        val thisShift = rightShift + externalPadding + currentMarker.leftMargin
                        doRightPlacement(currentMarker, thisShift)
                        thisShift + currentMarker.rightMargin
                      }
                    })

                }

                new LayoutMarker {

                  val element = bx
                  val exterior = false
                  val rootEdge = outgoingEdge

                  override def height = bx.height

                  override def leftInternalMargin =
                    if (firstMarker.element.rootX < bx.x) {
                      (bx.rootX - firstMarker.element.rootX) + halfLeafWidth
                    } else {
                      bx.leftMargin
                    }

                  override def rightInternalMargin =
                    if (lastMarker.element.rootX > (bx.x + bx.width)) {
                      (lastMarker.element.rootX - bx.rootX) + halfLeafWidth
                    } else {
                      bx.rightMargin
                    }

                  override def leftSubtreeMargin =
                    if (firstMarker.element.rootX < bx.x) {
                      firstMarker.leftMargin - halfLeafWidth
                    } else zero

                  override def rightSubtreeMargin =
                    if (lastMarker.element.rootX > (bx.x + bx.width)) {
                      lastMarker.rightMargin - halfLeafWidth
                    } else zero

                }

              }

            marker

          }

        case Box(bx, cn) => {

          bx.clear

          val (leafCount : Int, leavesWithIndices : Tree[(LayoutMarker, Int), P]) = lvs.zipWithIndex

          def verticalPass(tr : Tree[Nesting[CellBox[A, S[P]], S[P]], S[P]]) : ShapeM[LayoutMarker] =
            Tree.graftRec[Nesting[CellBox[A, S[P]], S[P]], LayoutMarker, P](tr)({
              case addr =>
                for {
                  leafMarkerWithIndex <- Tree.valueAt(leavesWithIndices, addr)
                } yield {

                  val (leafMarker, leafIndex) = leafMarkerWithIndex

                  if (leafIndex == 0 && leafCount == 1) {
                    leafMarker.truncateUnique
                  } else if (leafIndex == 0) {
                    leafMarker.truncateRight
                  } else if (leafIndex == leafCount - 1) {
                    leafMarker.truncateLeft
                  } else {
                    leafMarker.truncateMiddle
                  }
                }
            })({
              case (sn, layoutTree) =>
                for {
                  localLayout <- layoutNesting(sn, layoutTree)
                } yield {

                  val descendantMarkers : List[LayoutMarker] = layoutTree.nodes

                  val (leftMostChild, rightMostChild, heightOfChildren) =
                    (descendantMarkers foldLeft (localLayout, localLayout, zero))({
                      case ((lcMarker, rcMarker, ht), thisMarker) => {

                        if (! thisMarker.exterior) {
                          thisMarker.element.shiftUp(localLayout.height + externalPadding)
                          localLayout.element.verticalDependants += thisMarker.element
                        }

                        val newLeftChild = if (thisMarker.leftEdge < lcMarker.leftEdge) thisMarker else lcMarker
                        val newRightChild = if (thisMarker.rightEdge > rcMarker.rightEdge) thisMarker else rcMarker

                        (newLeftChild, newRightChild, max(ht, thisMarker.height))

                      }
                    })

                  val marker = new LayoutMarker {

                    val element = localLayout.element
                    val exterior = false
                    val rootEdge = localLayout.rootEdge

                    override def height = localLayout.height + externalPadding + heightOfChildren

                    override def leftInternalMargin =
                      (localLayout.element.rootX  - leftMostChild.element.rootX) + leftMostChild.leftInternalMargin

                    override def rightInternalMargin =
                      (rightMostChild.element.rootX - localLayout.element.rootX) + rightMostChild.rightInternalMargin

                    override def leftSubtreeMargin = leftMostChild.leftSubtreeMargin
                    override def rightSubtreeMargin = rightMostChild.rightSubtreeMargin

                  }

                  marker

                }
            })

          for {
            layout <- verticalPass(cn)
          } yield {

            // Set interior margins
            bx.leftInteriorMargin = layout.leftMargin
            bx.rightInteriorMargin = layout.rightMargin
            bx.interiorHeight = layout.height

            // Even if the layout was exterior  (meaning that we just
            // rendered a loop) we need to move it horizontally
            bx.horizontalDependants += layout.element

            if (! layout.exterior) {
              layout.element.shiftUp(fullStrokeWidth + bx.labelHeight + internalPadding + internalPadding)
              bx.verticalDependants += layout.element
            }

            // Setup and return an appropriate marker
            val marker = new LayoutMarker {

              val element = bx
              val exterior = false
              val rootEdge = layout.rootEdge

              override def height = bx.height
              override def leftInternalMargin = bx.leftMargin
              override def rightInternalMargin = bx.rightMargin

            }

            marker

          }
        }
      }


  }

  //============================================================================================
  // ROOTED HELPER TRAIT
  //

  trait Rooted {

    var rootX : U
    var rootY : U

    val horizontalDependants : ListBuffer[Rooted] = ListBuffer.empty
    val verticalDependants : ListBuffer[Rooted] = ListBuffer.empty

    def shiftRight(amount : U) : Unit = {
      if (amount != 0) {
        rootX = (rootX + amount)
        horizontalDependants foreach (_.shiftRight(amount))
      }
    }

    def shiftDown(amount : U) : Unit = {
      if (amount != 0) {
        rootY = (rootY + amount)
        verticalDependants foreach (_.shiftDown(amount))
      }
    }

    def shiftLeft(amount : U) : Unit = shiftRight(-amount)
    def shiftUp(amount : U) : Unit = shiftDown(-amount)

  }

  //============================================================================================
  // LAYOUT MARKER HELPER CLASS
  //

  // Why don't you clean this up and make it a case class?  This complicated version
  // is really unnecessary .....

  abstract class LayoutMarker { thisMarker =>

    val element : Rooted
    val exterior : Boolean  // This is really a bad name for this .. you should change it

    val rootEdge : EdgeLike

    def height : U = zero

    def leftSubtreeMargin : U = zero
    def rightSubtreeMargin : U = zero
    def leftInternalMargin : U = zero
    def rightInternalMargin : U = zero

    def leftMargin : U = leftSubtreeMargin + leftInternalMargin
    def rightMargin : U = rightSubtreeMargin + rightInternalMargin

    def leftEdge : U = element.rootX - leftMargin
    def rightEdge : U = element.rootX + rightMargin

    // Truncations

    def truncateLeft : LayoutMarker =
      new LayoutMarker {
        val element = thisMarker.element
        val exterior = true
        val rootEdge = thisMarker.rootEdge
        override def rightSubtreeMargin = thisMarker.rightSubtreeMargin
        override def rightInternalMargin = thisMarker.rightInternalMargin
      }

    def truncateRight : LayoutMarker =
      new LayoutMarker {
        val element = thisMarker.element
        val exterior = true
        val rootEdge = thisMarker.rootEdge
        override def leftSubtreeMargin = thisMarker.leftSubtreeMargin
        override def leftInternalMargin = thisMarker.leftInternalMargin
      }

    def truncateUnique : LayoutMarker =
      new LayoutMarker {
        val element = thisMarker.element
        val rootEdge = thisMarker.rootEdge
        val exterior = true
      }

    def truncateMiddle : LayoutMarker =
      new LayoutMarker {
        val element = thisMarker.element
        val exterior = true
        val rootEdge = thisMarker.rootEdge
        override def leftSubtreeMargin = thisMarker.leftSubtreeMargin
        override def rightSubtreeMargin = thisMarker.rightSubtreeMargin
        override def leftInternalMargin = thisMarker.leftInternalMargin
        override def rightInternalMargin = thisMarker.rightInternalMargin
      }

    override def toString = "LM(" ++ element.toString ++ ")" ++
    "(we = " ++ exterior.toString ++ ", ht = " ++ height.toString ++
    ", re = " ++ rootEdge.toString ++
    ", rx = " ++ element.rootX.toString ++
    ", ry = " ++ element.rootY.toString ++
    ", lsm = " ++ leftSubtreeMargin.toString ++
    ", lim = " ++ leftInternalMargin.toString ++
    ", rim = " ++ rightInternalMargin.toString ++
    ", rsm = " ++ rightSubtreeMargin.toString ++ ")"

  }

  //============================================================================================
  // EDGE MARKERS
  //

  class EdgeStartMarker(edge : EdgeLike) extends Rooted {

    def rootX : U = edge.edgeStartX
    def rootX_=(u : U) : Unit =
      edge.edgeStartX = u

    def rootY : U = edge.edgeStartY
    def rootY_=(u : U) : Unit =
      edge.edgeStartY = u

  }

  class EdgeEndMarker(edge : EdgeLike) extends Rooted {

    def rootX : U = edge.edgeEndX
    def rootX_=(u : U) : Unit =
      edge.edgeEndX = u

    def rootY : U = edge.edgeEndY
    def rootY_=(u : U) : Unit =
      edge.edgeEndY = u

  }

}