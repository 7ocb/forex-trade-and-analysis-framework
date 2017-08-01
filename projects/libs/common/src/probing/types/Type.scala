package tas.probing.types

import scala.annotation.tailrec
import tas.output.logger.Logger
import tas.output.logger.ScreenLogger

import tas.output.format.Formatting

object Type {

  type ParseResult[T] = List[T]
  def nothing[T] = List[T]()

  trait Parser[T] {
    def name:String
    def example:String
    def parse(str:String, logger:Logger):ParseResult[T]
  } 

  def numberParser[T](convert:(String)=>T):Parser[T] = 
    new Parser[T] {
      def name = "single value (constant during all probes)"
      def example = "<single value>"
      def parse(str:String, logger:Logger) = {
        try List(convert(str))
        catch {
          case e:NumberFormatException => nothing[T]
        } 
      }
    }


  def listParser[T](singleValueParser:Parser[T]) = {
    new Parser[T] {
      def name = "list of values"
      def example = "<value>,<value>[,<value>,...]"
      def parse(str:String, logger:Logger):List[T] = {
        val parts = { str
                     .trim
                     .split(",")
                     .map(_.trim)
                     .map(singleValueParser.parse(_, logger))
                   }

        val allParsed = ! parts.exists(_.isEmpty)

        if (allParsed) parts.toList.map(_(0))
        else {
          logger.log(str, " failed to parse as list")
          nothing[T]
        } 
      }
    }    
  } 
  
  def rangeParser[T](singleValueParser:Parser[T], createRange:(T,T,T)=>ParseResult[T]):Parser[T] = {
    val prefix = "range("
    val postfix = ")"
    new Parser[T] {
      def name = "range of values"
      def example = "range(<inclusive start>,<inclusive end>,<step>)"
      def parse(str:String, logger:Logger) = {
        val trimmed = str.trim
        if (trimmed.startsWith(prefix) && trimmed.endsWith(postfix)) {
          val rangeRulesString = trimmed.substring(prefix.length,
                                                   trimmed.length - postfix.length)

          val rangeRules = { rangeRulesString
                            .split(",")
                            .map(singleValueParser.parse(_, logger))
                            .filterNot(_.isEmpty)
                            .map(_(0)) } 

          if (rangeRules.length == 3) {

            val start = rangeRules(0)
            val end = rangeRules(1)
            val step = rangeRules(2)

            val range = createRange(start,
                                    end,
                                    step)
            if (range.isEmpty) logger.log("empty range: ", trimmed)
            else if (range.length == 1) logger.log("single-value range: ", trimmed, " = [", range(0), "]")

            range
          } else nothing[T]
        } else nothing[T]
      }
    } 
  } 
} 

abstract class Type[T](val name:String, val parsers:List[Type.Parser[T]]) {

  import Type.nothing
  import Type.ParseResult

  def align(string:String, all:List[String]):String
  
  def parse(string:String, logger:Logger = ScreenLogger):ParseResult[T] = {
    @tailrec def tryParse(string:String,
                          parsersToTry:List[Type.Parser[T]]):ParseResult[T] = {

      if (parsersToTry.isEmpty) nothing[T]
      else {
        val value = parsersToTry.head.parse(string, logger)
        if (value != nothing[T]) value
        else tryParse(string, parsersToTry.tail)
      } 
    }

    tryParse(string, parsers)
  }

  def format(t:T) = Formatting.format(t)
} 


