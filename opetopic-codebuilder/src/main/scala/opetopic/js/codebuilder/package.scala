/**
  * package.scala - Package level definitions
  * 
  * @author Eric Finster
  * @version 0.1 
  */

package opetopic.js

import opetopic._
import opetopic.tt._

import scalaz.\/
import scalaz.\/-
import scalaz.-\/

import OpetopicTypeChecker._

package object codebuilder {

  type EditorM[+A] = \/[String, A]

  def fromShape[A](s: ShapeM[A]) : EditorM[A] =
    s match {
      case -\/(ShapeError(msg)) => -\/(msg)
      case \/-(a) => \/-(a)
    }

  def attempt[A](opt: Option[A], msg: String) : EditorM[A] =
    opt match {
      case Some(a) => \/-(a)
      case None => -\/(msg)
    }

  def editorError[A](msg: String) : EditorM[A] = -\/(msg)
  def editorSucceed[A](a: A) : EditorM[A] = \/-(a)

  def forceNone[A](opt: Option[A], msg: String) : EditorM[Unit] =
    opt match {
      case None => editorSucceed(())
      case Some(_) => editorError(msg)
    }

  def runCheck[A](m: G[A])(
    onError : String => EditorM[A]
  )(
    onSucceed : A => EditorM[A]
  ) : EditorM[A] =
    m match {
      case -\/(msg) => onError(msg)
      case \/-(a) => onSucceed(a)
    }

}
