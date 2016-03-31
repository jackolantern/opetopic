/**
  * Pickle.scala - Custom Pickler implementations for Opetopic types
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic

import upickle.Js
import upickle.default._

object Pickler {

  //============================================================================================
  // TREE PICKLING
  //

  def treeWriter[A, N <: Nat](implicit wrtr: Writer[A]) : Writer[Tree[A, N]] = 
    new Writer[Tree[A, N]] { 
      def write0: Tree[A, N] => Js.Value = {
        case Pt(a) => Js.Obj(("type", Js.Str("pt")), ("val", wrtr.write(a)))
        case Leaf(d) => Js.Obj(("type", Js.Str("leaf")))
        case Node(a, sh) => {
          val shellWriter : Writer[Tree[Tree[A, S[Nat]], Nat]] =
            treeWriter[Tree[A, S[Nat]], Nat](this)
          Js.Obj(("type", Js.Str("node")), ("val", wrtr.write(a)), ("shell", shellWriter.write(sh)))
        }
      }
    }

  @natElim
  def treeReader[A, N <: Nat](n: N)(implicit rdr: Reader[A]) : Reader[Tree[A, N]] = {
    case Z => {
      new Reader[Tree[A, _0]] {
        def read0: PartialFunction[Js.Value, Tree[A, _0]] = {
          case Js.Obj(("type", Js.Str("pt")), ("val", a)) => Pt(rdr.read(a))
        }
      }
    }
    case S(p: P) => {
      new Reader[Tree[A, S[P]]] { thisRdr =>
        def read0: PartialFunction[Js.Value, Tree[A, S[P]]] = {
          case Js.Obj(("type", Js.Str("leaf"))) => Leaf(S(p))
          case Js.Obj(("type", Js.Str("node")), ("val", a), ("shell", sh)) => {
            val shellReader : Reader[Tree[Tree[A, S[P]], P]] =
              treeReader[Tree[A, S[P]], P](p)(thisRdr)

            Node(rdr.read(a), shellReader.read(sh))
          }
        }
      }
    }
  }

  //============================================================================================
  // NESTING PICKLING
  //

  implicit def nestingWriter[A, N <: Nat](implicit wrtr: Writer[A]) : Writer[Nesting[A, N]] = 
    new Writer[Nesting[A, N]] {
      def write0: Nesting[A, N] => Js.Value = {
        case Obj(a) => Js.Obj(("type", Js.Str("obj")), ("val", wrtr.write(a)))
        case Dot(a, d) => Js.Obj(("type", Js.Str("dot")), ("val", wrtr.write(a)))
        case Box(a, cn) => {
          val canopyWriter : Writer[Tree[Nesting[A, N], N]] = treeWriter(this)
          Js.Obj(("type", Js.Str("box")), ("val", wrtr.write(a)), ("canopy", canopyWriter.write(cn)))
        }
      }
    }

  @natElim
  implicit def nestingReader[A, N <: Nat](n: N)(implicit rdr: Reader[A]) : Reader[Nesting[A, N]] = {
    case Z => {
        new Reader[Nesting[A, _0]] { thisRdr =>
          def read0: PartialFunction[Js.Value, Nesting[A, _0]] = {
            case Js.Obj(("type", Js.Str("obj")), ("val", a)) => Obj(rdr.read(a))
            case Js.Obj(("type", Js.Str("box")), ("val", a), ("canopy", cn)) => {
              val canopyReader : Reader[Tree[Nesting[A, _0], _0]] = treeReader(Z)(thisRdr)
              Box(rdr.read(a), canopyReader.read(cn))
            }
          }
        }
    }
    case S(p: P) => {
      new Reader[Nesting[A, S[P]]] { thisRdr =>
        def read0: PartialFunction[Js.Value, Nesting[A, S[P]]] = {
          case Js.Obj(("type", Js.Str("dot")), ("val", a)) => Dot(rdr.read(a), S(p))
          case Js.Obj(("type", Js.Str("box")), ("val", a), ("canopy", cn)) => {
            val canopyReader : Reader[Tree[Nesting[A, S[P]], S[P]]] = treeReader(S(p))(thisRdr)
            Box(rdr.read(a), canopyReader.read(cn))
          }
        }
      }
    }
  }

}
