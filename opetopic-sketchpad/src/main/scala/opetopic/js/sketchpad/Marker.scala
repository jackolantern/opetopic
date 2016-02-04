/**
  * Marker.scala - Cell visualization marker
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js.sketchpad

import opetopic._
import opetopic.ui._
import opetopic.js._
import syntax.tree._

sealed trait Marker[N <: Nat] {
  def label: String
  def colorSpec: ColorSpec

  def withLabel(lbl: String) : Marker[N]
  def withColorSpec(spec: ColorSpec) : Marker[N]
}

case class ObjectMarker(
  val label: String,
  val colorSpec: ColorSpec = DefaultColorSpec
) extends Marker[_0] {
  def withLabel(lbl: String) = ObjectMarker(lbl, colorSpec)
  def withColorSpec(spec: ColorSpec) = ObjectMarker(label, spec)
}

case class CellMarker[P <: Nat](
  val label: String,
  val colorSpec: ColorSpec = DefaultColorSpec,
  val rootEdgeDecoration: Boolean = false,
  val leafEdgeDecorations: Option[Tree[Boolean, P]] = None
) extends Marker[S[P]] {
  def withLabel(lbl: String) = CellMarker(lbl, colorSpec, rootEdgeDecoration, leafEdgeDecorations)
  def withColorSpec(spec: ColorSpec) = CellMarker(label, spec, rootEdgeDecoration, leafEdgeDecorations)
}

object Marker {

  type OptMarker[N <: Nat] = Option[Marker[N]]

  // def apply[N <: Nat](n: N)(lbl: String) : Marker[N] = 
  //   Marker(n)(lbl, DefaultColorSpec)

  @natElim
  def apply[N <: Nat](n: N)(lbl: String, spec: ColorSpec) : Marker[N] = {
    case (Z, lbl, spec) => ObjectMarker(lbl, spec)
    case (S(p), lbl, spec) => CellMarker(lbl, spec)
  }

  object ActiveInstance {

    import JsDomFramework._

    implicit val markerFamily : VisualizableFamily[Marker] = 
      new VisualizableFamily[Marker] {
        @natElim
        def visualize[N <: Nat](n: N)(mk: Marker[N]) : Visualization[N] = {
          case (Z, ObjectMarker(lbl, spec)) => ObjectVisualization(spec, text(lbl))
          case (S(p: P), CellMarker(lbl, spec, rd, eds)) => {

            val markRoot = 
              if (rd)
                Some(BoundedElement(rect(0, 0, 300, 300, 100, "red", 100, "red"), Bounds(0, 0, 300, 300)))
              else
                None

            val markLeaves = eds map ((tr : Tree[Boolean, P]) => {
              tr map ((b: Boolean) => 
                if (b)
                  Some(BoundedElement(rect(0, 0, 300, 300, 100, "red", 100, "red"), Bounds(0, 0, 300, 300)))
                else None
              )
            })

            CellVisualization(spec, text(lbl), markRoot, markLeaves)
          }
        }
      }

  }

  object StaticInstance {

    import opetopic.ui.ScalatagsTextFramework._

    implicit val skinFamily : VisualizableFamily[Marker] = ???

  }

}

// // object CellMarker {

// //   type OptCellMarker[N <: Nat] = Option[CellMarker[N]]

// //   object ActiveInstance {

// //     import JsDomFramework._

// //     implicit object CellMarkerFamily extends VisualizableFamily[CellMarker] {

// //       def spcr = spacer(Bounds(0,0,600,600))
// //       def emkr = rect(0, 0, 300, 300, 100, "red", 100, "red")

// //       @natElim
// //       def visualize[N <: Nat](n: N)(mk: CellMarker[N]) : Visualization[N] = {
// //         case (Z, mk) => Visualization(Z)(mk.colorSpec, if (mk.label == "") spcr else text(mk.label))
// //         case (S(p), mk) => {
// //           CellVisualization(
// //             mk.colorSpec,
// //             if (mk.label == "") spcr else text(mk.label),
// //             Some(BoundedElement(emkr, Bounds(0, 0, 300, 300))),
// //             None
// //           )
// //         }
// //       }
// //     }

// //   }

// //   object StaticInstance {

// //     import opetopic.ui.ScalatagsTextFramework._

// //     implicit object CellMarkerFamily extends VisualizableFamily[CellMarker] {
// //       def visualize[N <: Nat](n: N)(mk: CellMarker[N]) : Visualization[N] = 
// //         Visualization(n)(mk.colorSpec, text(mk.label))
// //     }

// //   }

// // }

