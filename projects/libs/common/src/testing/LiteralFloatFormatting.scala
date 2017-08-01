package tas.testing

import tas.output.format.{Formatting, FloatFormatter}

trait LiteralFloatFormatting {
  Formatting.addFormatter(new FloatFormatter {
    override def format(o:Any) = o.toString
  } )
} 
