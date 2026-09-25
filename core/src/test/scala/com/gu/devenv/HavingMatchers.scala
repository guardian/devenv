package com.gu.devenv

import org.scalactic.source.Position
import org.scalatest.matchers.HavePropertyMatcher
import org.scalatest.matchers.should.Matchers

trait HavingMatchers extends Matchers {
  def having[A](propertyName: String, propertyValue: A): HavePropertyMatcher[AnyRef, Any] =
    Symbol(propertyName)(propertyValue)

  implicit class HavingTestHelperString(propertyName: String) {
    infix def as[A](propertyValue: A)(implicit pos: Position): HavePropertyMatcher[AnyRef, Any] =
      Symbol(propertyName)(propertyValue)
  }
}
