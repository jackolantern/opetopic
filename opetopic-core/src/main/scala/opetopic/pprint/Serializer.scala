/**
  * Serializer.scala - Base Trait for Serializers
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.serialize

import scala.{Iterator => Iter}
import annotation.tailrec

import opetopic._
import syntax.tree._
import syntax.nesting._

case class Config(
  width: Int = 80,
  depth: Int = 0,
  indent: Int = 2
) { def deeper = copy(depth = depth + 1) }

trait Chunker[T] {
  def chunk(t: T, c: Config): Iter[Iter[String]]
}

trait PPrint[T] {
  def render(t: T, c: Config): Iter[String]
}

trait PPrintBase {

  def pprint[A](a: A)(implicit c: Config, p: PPrint[A]) : String = 
    p.render(a, c).mkString

  def pprintTree[A, N <: Nat](n: N)(tr: Tree[A, N], c: Config)(implicit ap: PPrint[A]): Iter[String] 
  def pprintNesting[A, N <: Nat](n: N)(nst: Nesting[A, N], c: Config)(implicit ap: PPrint[A]): Iter[String] 

  //============================================================================================
  // DEFAULT CONFIG
  //

  implicit val defaultConfig = Config()


  //============================================================================================
  // IMPLICITS
  //

  implicit object IntPPrint extends PPrint[Int] {
    def render(i: Int, c: Config) = Iter(i.toString)
  }

  implicit def treeIsPPrint[A, N <: Nat](implicit ap: PPrint[A]): PPrint[Tree[A, N]] = 
    new PPrint[Tree[A, N]] {
      def render(t: Tree[A, N], c: Config) = pprintTree(t.dim)(t, c)
    }

  implicit def nestingIsPPrint[A, N <: Nat](implicit ap: PPrint[A]): PPrint[Nesting[A, N]] = 
    new PPrint[Nesting[A, N]] {
      def render(n: Nesting[A, N], c: Config) = pprintNesting(n.dim)(n, c)
    }

  //============================================================================================
  // CHUNKING
  //

  type ChunkFunc = Config => Iter[Iter[String]]
  
  val ansiRegex = "\u001B\\[[;\\d]*m"

  def handleChunks(name: String, c: Config, chunkFunc: ChunkFunc): Iter[String] 
  def handleVerticalChunks(name: String, c: Config, chunkFunc: ChunkFunc): Iter[String] 

}

object ScalaPPrint extends PPrintBase {

  //============================================================================================
  // IMPLEMENTATIONS
  //

  @natElim
  def pprintTree[A, N <: Nat](n: N)(tr: Tree[A, N], c: Config)(implicit ap: PPrint[A]): Iter[String] = {
    case (Z, Pt(a), c) => handleChunks("Pt", c, (c0: Config) => Iter(ap.render(a, c0)))
    case (S(p: P), Leaf(d), c) => handleChunks("Leaf", c, (c0: Config) => Iter(pprintNat(d, c0)))
    case (S(p: P), Node(a, sh), c) => 
      handleChunks("Node", c, (c0: Config) => {

        object TreePP extends PPrint[Tree[A, S[P]]] {
          def render(t : Tree[A, S[P]], c1: Config) = 
            pprintTree(S(p))(t, c1)
        }

        Iter(ap.render(a, c0), pprintTree(p)(sh, c0)(TreePP))
      })
  }

  def pprintNesting[A, N <: Nat](n: N)(nst: Nesting[A, N], c: Config)(implicit ap: PPrint[A]): Iter[String] = 
    nst match {
      case Obj(a) => handleChunks("Obj", c, (c0: Config) => Iter(ap.render(a, c0)))
      case Dot(a, d) => handleChunks("Dot", c, (c0: Config) => Iter(ap.render(a, c0), pprintNat(d, c0)))
      case Box(a, cn) => 
        handleChunks("Box", c, (c0: Config) => {

          object NestingPP extends PPrint[Nesting[A, N]] {
            def render(nst1: Nesting[A, N], c1: Config) =
              pprintNesting(n)(nst1, c1)
          }

          Iter(ap.render(a, c0), pprintTree(n)(cn, c0)(NestingPP))
        })
    }

  // def pprintComplex[A[_ <: Nat], N <: Nat](n: N)(cmplx: Complex[A, N], c: Config)(implicit ap: IndexedPPrint[A]) : Iter[String] = {
  // }

  def pprintNat[N <: Nat](n: N, c: Config) : Iter[String] = 
    n match {
      case Z => Iter("Z")
      case S(p) => handleChunks("S", c, (c0: Config) => Iter(pprintNat(p, c0)))
    }

  //============================================================================================
  // CHUNKING IMPLEMENTATION
  //

  def handleChunks(name: String, c: Config, chunkFunc: ChunkFunc): Iter[String] = {

    // Prefix, contents, and all the extra ", " "(" ")" characters
    val horizontalChunks =
      chunkFunc(c).flatMap(", " +: _.toStream)
                  .toStream
                  .drop(1)

    val effectiveWidth = c.width - (c.depth * c.indent)

    @tailrec def checkOverflow(chunks: Stream[String], currentWidth: Int): Boolean = chunks match{
      case Stream.Empty => false
      case head #:: rest =>
        if (head.contains("\n")) true
        else {
          val nextWidth = currentWidth + head.replaceAll(ansiRegex, "").length
          if (nextWidth > effectiveWidth) true
          else checkOverflow(rest, nextWidth)
        }
    }

    val overflow = checkOverflow(horizontalChunks, name.length + 2)

    if (overflow) 
      handleVerticalChunks(name, c, chunkFunc)
    else 
      Iter(name, "(") ++ horizontalChunks ++ Iter(")")

  }

  def handleVerticalChunks(name: String, c: Config, chunkFunc: ChunkFunc): Iter[String] = {

    val chunks2 = chunkFunc(c.deeper)

    // Needs to be a def to avoid exhaustion
    def indent = Iter.fill(c.depth)("  ")

    Iter(name, "(\n") ++ 
      chunks2.flatMap(Iter(",\n", "  ") ++ indent ++ _).drop(1) ++ 
      Iter("\n") ++ indent ++ Iter(")")

  }

}