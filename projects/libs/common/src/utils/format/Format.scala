package tas.utils.format

import tas.types.Boundary

import tas.output.format.Formatting

object Format {
  implicit class Tp(tp:Option[Boundary]) {
    def tpToString:String = tp.map(Formatting.format(_)).getOrElse("None")
  }
}
