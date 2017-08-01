package tas.probing.types

import java.io.File
import tas.output.logger.Logger
import tas.types.{Interval, Fraction}

import tas.utils.parsers.{
  IntervalParser, FormatError
}

import scala.collection.mutable.ListBuffer  


trait NumberAlign {
  def align(srcString:String, srcAll:List[String]):String = {

    def havePoint(str:String) = str.exists(_ == '.')
    val atLeastOneHavePoint = havePoint(srcString) || srcAll.exists(str => havePoint(str))

    if (atLeastOneHavePoint) {
      
      def pointIndex(str:String) = str.indexOf('.')
      
      def resultString(srcString:String) = {
        val index = pointIndex(srcString)
        if (index < 0) srcString + "."
        else srcString      
      } 
      
      val string = resultString(srcString)
      val all = srcAll.map(resultString(_))
      
      val maxBeforePoint = all.map(pointIndex(_)).max
      val maxAfterPoint = all.map(str => str.length - pointIndex(str)).max

      
      ("0" * (maxBeforePoint - pointIndex(string))) + string + ("0" * (maxAfterPoint - (string.length - pointIndex(string))))
    } else {
      val maxValueStringLen = srcAll.map(_.length).max
      val complementLength = maxValueStringLen - srcString.length
      
      ("0" * (complementLength - srcString.length)) + srcString
    } 
  }  
}

trait AlignWithSpaces {
  def align(string:String, all:List[String]):String = {
    val maxValueStringLen = all.map(_.length).max
    val complementLength = maxValueStringLen - string.length

    (" " * complementLength) + string
  }
} 

object StringType extends Type[String]("string",
                                       {
                                         val stringParser =
                                           new Type.Parser[String] {
                                             def name = "string"
                                             def example = "<string>"
                                             def parse(str:String, logger:Logger) = List(str)
                                           }

                                         List(Type.listParser(stringParser),
                                              stringParser)
                                       }) with AlignWithSpaces {
}

object IntType extends Type[Int]("int",
                                 {
                                   val intParser = Type.numberParser(_.toInt)

                                   List(Type.rangeParser(intParser,
                                                         (start, end, step) => {
                                                           (start to end by step).toList
                                                         } ),
                                        Type.listParser(intParser),
                                        intParser)
                                 }) with NumberAlign

object FractionType extends Type[Fraction]("fraction",
                                       {
                                         val fractionParser = Type.numberParser(a => Fraction(a))

                                         List(Type.rangeParser(fractionParser,
                                                               (start, end, step) => {
                                                                 start.range(end, step)
                                                               } ),
                                              Type.listParser(fractionParser),
                                              fractionParser)
                                       }) with NumberAlign

object FileType extends Type[File]("file",
                                   List(new Type.Parser[File] {
                                     def name = "path to file"
                                     def example = "<path to file>"
                                     def parse(str:String, logger:Logger) = {
                                       val file = new File(str.trim)
                                       if ( ! file.isFile ) logger.log("file not found: " + str)
                                       List(file)
                                     } 
                                   } )) with AlignWithSpaces

private [types] object IntervalTypeParsers {
  def apply() = {
    val intervalParser = new Type.Parser[Interval] {
        def name = "interval"
        def example = IntervalParser.sample
        def parse(str:String, logger:Logger) = {
          try {
            List(IntervalParser(str))
          } catch {
            case FormatError(message) => List[Interval]()
          }
        }
      }

    def createRangeMethod(from:Interval, inclusiveTo:Interval, step:Interval):List[Interval] = {
      if (from > inclusiveTo) return List[Interval]()

      val result = new ListBuffer[Interval]

      var interval = from

      while (interval <= inclusiveTo) {
        result += interval
        interval = interval + step
      }
      
      result.toList
    }
    
    List(Type.rangeParser(intervalParser, createRangeMethod),
         Type.listParser(intervalParser),
         intervalParser)
  }
}

object IntervalType extends Type[Interval]("interval",
                                           IntervalTypeParsers()) with AlignWithSpaces {

  override def format(i:Interval) = i.toStringShortForm
}
