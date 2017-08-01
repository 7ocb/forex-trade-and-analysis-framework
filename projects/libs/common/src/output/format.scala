package tas.output.format

import tas.types.Time

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import scala.collection.mutable.ListBuffer

trait Formatter {
  def isSupports(o:Any):Boolean
  def format(o:Any):String
}


class CallToStringFormatter extends Formatter {
  override def isSupports(o:Any):Boolean = true
  override def format(o:Any):String = {
    if (o == null) "null";
    else o.toString
  } 
}

private object FloatFormatter {
  private lazy val df = {
    val symbols = new DecimalFormatSymbols()
    symbols.setDecimalSeparator('.')

    new DecimalFormat("#.########", symbols)
  } 
} 

class FloatFormatter extends Formatter {
  override def isSupports(o:Any):Boolean = {
    o.isInstanceOf[Double] || o.isInstanceOf[Float]
  }

  override def format(o:Any):String = FloatFormatter.df.format(o.asInstanceOf[Double])
}

class StringFormatter extends Formatter {
  override def isSupports(o:Any):Boolean = o.isInstanceOf[String] 
  override def format(o:Any):String = o.asInstanceOf[String]
}

class TimeFormatter extends Formatter {

  private var lastFormattedTime:Time = null
  private var lastFormattedTimeResult:String = "no time"

  override def isSupports(o:Any):Boolean = o.isInstanceOf[Time]
  override def format(o:Any):String = {
    val time = o.asInstanceOf[Time]

    if (time == lastFormattedTime) lastFormattedTimeResult
    else {
      lastFormattedTime = time
      lastFormattedTimeResult = time.toString()
      lastFormattedTimeResult
    }
  }
}

object Formatting {

  private var _formatters = ListBuffer(new TimeFormatter(),
                                       new StringFormatter(),
                                       // new FloatFormatter(),
                                       new CallToStringFormatter())
  
  def format(os:Any*):String = {
    if (os.length == 0) return ""

    os.map(o => {
      // there is no possibility that there will be no formatter, so we
      // need not to work with Option.
      val formatterForO = _formatters.find(_.isSupports(o)).get

      formatterForO.format(o)

    } ).reduce(_ + _)
  }

  def addFormatter(formatter:Formatter) = {
    _formatters.+=:(formatter)
  } 
  def removeFormatter(formatter:Formatter) = _formatters -= formatter

  def withSpecific[T](formatters:Formatter*)(body: => T) = {
    val oldFormatters = _formatters
    formatters.foreach(addFormatter(_))
    val result = body
    _formatters = oldFormatters
    result
  }

  
} 
