package com.gu.devenv

import fansi.{Bold, Color, Str}

/** Formatting conventions:
  *   - Filenames/paths: Cyan
  *   - Commands: Bold Cyan
  *   - Status: Green (success), Light Gray (neutral), Red (error), Yellow (warning)
  *   - Code snippets: Bold Green
  *   - Section headings: Bold White
  *   - Warning/Error headings: Bold Yellow / Bold Red
  *   - Dividers: Light Blue
  */
trait OutputFormatter {
  def render(value: Str): String

  final def filename(value: Str): Str       = Color.Cyan(value)
  final def link(value: Str): Str           = Color.Cyan(value)
  final def command(value: Str): Str        = Bold.On(Color.Cyan(value))
  final def emphasis(value: Str): Str       = Bold.On(value)
  final def success(value: Str): Str        = Color.Green(value)
  final def neutral(value: Str): Str        = Color.LightGray(value)
  final def warning(value: Str): Str        = Color.Yellow(value)
  final def error(value: Str): Str          = Color.Red(value)
  final def validCode(value: Str): Str      = Bold.On(Color.Green(value))
  final def invalidCode(value: Str): Str    = Bold.On(Color.Red(value))
  final def sectionHeading(value: Str): Str = Bold.On(value)
  final def successHeading(value: Str): Str = Bold.On(Color.Green(value))
  final def warningHeading(value: Str): Str = Bold.On(Color.Yellow(value))
  final def errorHeading(value: Str): Str   = Bold.On(Color.Red(value))
  final def sectionDivider(value: Str): Str = Color.LightBlue(value)
  final def successDivider(value: Str): Str = Color.Green(value)
  final def warningDivider(value: Str): Str = Color.Yellow(value)
  final def errorDivider(value: Str): Str   = Color.Red(value)
}

object OutputFormatter {
  val coloured: OutputFormatter = new OutputFormatter {
    override def render(value: Str): String = value.render
  }

  val plain: OutputFormatter = new OutputFormatter {
    override def render(value: Str): String = value.plainText
  }

  extension (context: StringContext) {
    def styled(arguments: Str*): Str = {
      val parts  = context.parts.iterator
      val values = List.newBuilder[Str]

      values += Str(parts.next().stripMargin)
      arguments.foreach { argument =>
        values += argument
        values += Str(parts.next().stripMargin)
      }

      Str(values.result()*)
    }
  }
}
