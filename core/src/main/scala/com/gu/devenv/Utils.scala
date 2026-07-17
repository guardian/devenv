package com.gu.devenv

import cats.data.EitherT

import scala.util.Try

/** Program helper types and functions
  *
  * These allow us to write for-comps that can exit early when a condition is met.
  *
  * We use Try to represent failure throughout the application, and EitherT to handle conditional
  * early exit from the for-comprehension, with a result value.
  *
  * Usage:
  *   - Use [[withConditions]] to wrap a for-comprehension that may exit early
  *   - Use [[exitIf]] to exit early with a result value if a condition is met
  *   - Add a [[liftF]] suffix to normal Try-returning operations so they can be used alongside
  *     exitIf checks.
  *
  * {{{
  * for {
  *   // exit early if some bad condition is met
  *   _ <- exitIf(someBadCondition, earlyResultValue)
  *   // continue with normal operations
  *   value <- someOperation.liftF
  * } yield ...
  * }}}
  */
object Utils {

  /** Wrap a for-comprehension that may exit early with a result value, handling the conversion back
    * to a `Try`.
    *
    * This supports Devenv's approach, which is to use `Try` for handling failures, and EitherT for
    * allowing conditional early-exit in the program's for-comprehensions.
    */
  def withConditions[Res](block: => EitherT[Try, Res, Res]): Try[Res] =
    block.value.map(_.merge)

  /** A helper for exiting early from a for-comprehension with a result value if a condition is met.
    */
  def exitIf[Res, A](condition: => Boolean, result: Res): EitherT[Try, Res, Unit] =
    if (condition)
      EitherT.leftT(result)
    else
      EitherT.rightT(())

  /** Alias liftF onto Try, so we don't need to write EitherT.liftF up front on every step.
    *
    * This way round we have the step's intent up front, and its conversion to EitherT at the end of
    * the line. I prefer this to needing to start every line with EitherT.liftF boilerplate.
    */
  extension [A](ta: Try[A]) {
    def liftF[Res]: EitherT[Try, Res, A] =
      EitherT.liftF(ta)
  }

  /** Convert a typed domain result into a step that either produces its parsed value or exits with
    * an expected program result.
    */
  extension [Error, A](result: Either[Error, A]) {
    def orExit[Res](toResult: Error => Res): EitherT[Try, Res, A] =
      EitherT.fromEither[Try](result.left.map(toResult))
  }

  /** This is needed to allow for-comps to use withFilter on EitherT steps (e.g. to unpack tuples).
    *
    * {{{
    * for {
    *   (thing1, thing2) <- operationReturningTuple
    * } yield ()
    * }}}
    */
  extension [Res, A](op: EitherT[Try, Res, A]) {
    def withFilter(p: A => Boolean): EitherT[Try, Res, A] =
      op.subflatMap { a =>
        if p(a) then Right(a)
        else
          throw new RuntimeException(
            "Internal error: withFilter predicate did not match - impossible state"
          )
      }
  }
}
