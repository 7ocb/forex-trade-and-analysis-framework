package tas.raters.polygon.data

import java.io.{
  File,
  FileInputStream
}

import tas.types.Fraction

import tas.utils.files.lined.LinedFileReader

class Curve(file:File) {
  if ( ! file.exists() ) throw new RuntimeException("Curve: file " + file + " does not exists.")
  if ( ! file.isFile ) throw new RuntimeException("Curve: file " + file + " is not a file.")

  val points = LinedFileReader.read(new FileInputStream(file))
    .significantLines
    .map(_.trim)
    .map(Fraction(_))
}
