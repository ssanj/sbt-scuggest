package net.ssanj

import java.io.File

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import play.api.libs.json.JsValue

package object sbtscuggest {

  sealed trait ProjectLoadError
  final case class CouldNotReadFile(file: File, error: Throwable) extends ProjectLoadError
  final case class CouldNotWriteFile(file: File, error: Throwable) extends ProjectLoadError
  final case class CouldNotParseContent(content: String, error: Throwable) extends ProjectLoadError
  final case class InvalidProjectStructure(json: JsValue) extends ProjectLoadError

  implicit final class TryOps[A](val t: Try[A]) extends AnyVal {
    def toEither[B](f: Throwable => B): Either[B, A] = t match {
      case Success(a) => Right(a)
      case Failure(e) => Left(f(e))
    }

    def fold[B](f: Throwable => B, s: A => B): B = t match {
      case Success(a) => s(a)
      case Failure(e) => f(e)
    }
  }

  implicit final class RightBiasedEitherOps[A, B](e: Either[A, B]) {
    def map[C](f: B => C): Either[A, C] = e.right map f
    def flatMap[AA >: A, C](f: B => Either[AA, C]): Either[AA, C] = e.right flatMap f
    def filter(f: B => Boolean): Option[Either[A, B]] = e.right filter f
  }
}
