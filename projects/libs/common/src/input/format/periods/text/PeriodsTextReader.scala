package tas.input.format.periods.text

import scala.collection.mutable.ListBuffer

import tas.input.format.Reader

import tas.types.{Time, PeriodBid, Fraction}

private object Config {
  val possibleSeparators = List(",", "\t", " ")
  
  val priceOpen  = new FractionColumn("<OPEN>")
  val priceClose = new FractionColumn("<CLOSE>")
  val priceMin   = new FractionColumn("<LOW>")
  val priceMax   = new FractionColumn("<HIGH>")

  val time       = new TimeColumns("<DATE>", "<TIME>")
}

private abstract class Detector[T] {
  def detect(separatedHeader:Array[String]):Array[String]=>T
}

private class FractionColumn(private val columnName:String) extends Detector[Fraction] {

  def detect(separatedHeader:Array[String]):Array[String]=>Fraction = {
    val index = separatedHeader indexOf columnName
    if (index >= 0) {
      return separated => Fraction(separated(index))
    } else {
      return null
    }
  }
}

private class TimeColumns(private val dateColumn:String,
                          private val timeColumn:String) extends Detector[Time] {

  private trait TimeValuesExtractor {
    def extract(str:String):Option[(Int,Int,Int)]
  }

  private class DateYYYYMMDD extends TimeValuesExtractor {
    override def extract(str:String):Option[(Int,Int,Int)] = {
      if (str.length == 8) {
        Option(str.substring(0, 4).toInt,
               str.substring(4, 6).toInt,
               str.substring(6, 8).toInt)
      } else {
        Option.empty[(Int, Int, Int)]
      }
    }
  }

  private class TimeHHMMSS extends TimeValuesExtractor {
    override def extract(str:String):Option[(Int,Int,Int)] = {
      if (str.length == 6) {
        Option(str.substring(0, 2).toInt,
               str.substring(2, 4).toInt,
               str.substring(4, 6).toInt)
      } else {
        Option.empty[(Int, Int, Int)]
      }
    }
  }
  
  private val _dateFormats = List(new DateYYYYMMDD)
  private val _timeFormats = List(new TimeHHMMSS)
  
  private class TimeExtractor(dateIndex:Int, timeIndex:Int) extends Function1[Array[String], Time] {

    private var _timeExtractor:TimeValuesExtractor = null
    private var _dateExtractor:TimeValuesExtractor = null
    
    override def apply(separated:Array[String]):Time = {
      val dateString = separated(dateIndex)
      val timeString = separated(timeIndex)

      val time = extract(_timeExtractor,
                         _timeExtractor = _,
                         _timeFormats,
                         timeString)

      val date = extract(_dateExtractor,
                         _dateExtractor = _,
                         _dateFormats,
                         dateString)
      
      
      Time.fromCalendar(date._1,
                        date._2,
                        date._3,
                        time._1,
                        time._2,
                        time._3)
    }

    private def extract(extractor:TimeValuesExtractor, setter:(TimeValuesExtractor)=>Unit, variants:List[TimeValuesExtractor], str:String):(Int,Int,Int) = {
      if (extractor != null) {
        val res = extractor.extract(str)
        if (res.isEmpty) throw new Error("Existing extractor " + extractor
                                           + " failed to extract from: " + str)
        res.get
      } else {

        variants.foreach(e => {
                           val res = e.extract(str)

                           if ( ! res.isEmpty ) {
                             setter(e)
                             return res.get
                           }
                         } )
        
        throw new Error("Failed to find extractor for: " + str)
      }
    }
  }
  
  def detect(separatedHeader:Array[String]):Array[String]=>Time = {

    return new TimeExtractor(separatedHeader indexOf dateColumn,
                             separatedHeader indexOf timeColumn)
  }
}

private class Format(separator:String,
                     priceOpen:Array[String]=>Fraction,
                     priceClose:Array[String]=>Fraction,
                     priceMin:Array[String]=>Fraction,
                     priceMax:Array[String]=>Fraction,
                     time:Array[String]=>Time) {

  def extractPeriod(str:String):PeriodBid = {
    val separated = str.split(separator)

    try {
      new PeriodBid(priceOpen(separated),
                 priceClose(separated),
                 priceMin(separated),
                 priceMax(separated),
                 time(separated))
    } catch {
      case _:ArrayIndexOutOfBoundsException => null
    }
  }
}

private object Format {

  def detect(header:String):Format = {
    val separator = Config.possibleSeparators.find(t => header.indexOf(t) >= 0)

    if (separator.isEmpty) return null

    val separated = header.split(separator.get)

    new Format(separator.get,
               Config.priceOpen.detect(separated),
               Config.priceClose.detect(separated),
               Config.priceMin.detect(separated),
               Config.priceMax.detect(separated),
               Config.time.detect(separated))
  }
}

class PeriodsTextReader(stream:java.io.InputStream) extends Reader[PeriodBid] {

  val _reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))

  private val _format = {
    val headerLine = _reader.readLine
    if (headerLine != null) Format.detect(headerLine)
    else null
  }

  def read():Option[PeriodBid] = {

    if (_format == null) throw new Reader.FormatError("Format not detected!")

    val nextString = _reader.readLine

    if (nextString == null) return None

    return Some(_format.extractPeriod(nextString))
  }

  def close() = _reader.close
}
