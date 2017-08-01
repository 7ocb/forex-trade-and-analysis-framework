package tests.probing.types

import org.scalatest.FlatSpec

import tas.probing.types.Type
import tas.probing.types.IntType
import tas.probing.types.FileType
import tas.probing.types.FractionType
import tas.probing.types.IntervalType
import org.scalamock.scalatest.MockFactory
import tas.output.logger.Logger

import tas.types.{Interval, Fraction}

import java.io.File

class ProbingTypesTests extends FlatSpec with MockFactory {
  def parseValueTest[T](t:Type[T], string:String, expected:List[T]) = {
    it should ("parse " + expected + " from " + string) in {
      assert(expected === t.parse(string, mock[Logger]))
    } 
  }

  def parseWithWarningTest[T](t:Type[T], string:String, expected:List[T], warningCount:Int = 1) = {
    it should ("parse " + expected + " from " + string + " with warnings") in {

      val logger = mock[Logger]

      for (a <- 1 to warningCount) {
        (logger.log _).expects(*)
      }
      
      assert(expected === t.parse(string, logger))
    } 
  }


  behavior of "int"
  parseValueTest(IntType, "10", List(10))
  parseValueTest(IntType, "-10", List(-10))
  parseWithWarningTest(IntType, "a", List())
  parseValueTest(IntType, "1,2,3", List(1, 2, 3))
  parseWithWarningTest(IntType, "1,a,3", List())
  parseValueTest(IntType, "5,-10,20", List(5, -10, 20))
  parseValueTest(IntType, "range(1,5,1)", List(1, 2, 3, 4, 5))
  parseValueTest(IntType, "range(1,5,2)", List(1, 3, 5))
  parseValueTest(IntType, "range(1,5,3)", List(1, 4))
  parseWithWarningTest(IntType, "range(1,5,6)", List(1))
  parseWithWarningTest(IntType, "range(6,5,6)", List(), 2)

  behavior of "fraction"
  parseValueTest(FractionType, "10.1", List(Fraction("10.1")))

  parseValueTest(FractionType, "-9.2", List(Fraction("-9.2")))

  parseWithWarningTest(FractionType, "a", List())

  parseValueTest(FractionType, "1.2,2.3,3.4", List(Fraction("1.2"),
                                                   Fraction("2.3"),
                                                   Fraction("3.4")))

  parseWithWarningTest(FractionType, "1.2,a,3.4", List())

  parseValueTest(FractionType, "5.1,-10.2,20.3", List(Fraction("5.1"),
                                                      Fraction("-10.2"),
                                                      Fraction("20.3")))

  parseValueTest(FractionType, "range(1,5,0.5)", List(Fraction(1),
                                                      Fraction("1.5"),
                                                      Fraction(2),
                                                      Fraction("2.5"),
                                                      Fraction(3),
                                                      Fraction("3.5"),
                                                      Fraction(4),
                                                      Fraction("4.5"),
                                                      Fraction(5)))

  parseValueTest(FractionType, "range(1,5,2.5)", List(Fraction("1"),
                                                      Fraction("3.5")))

  parseWithWarningTest(FractionType, "range(1,2,3.3)", List(Fraction("1.0")))

  parseWithWarningTest(FractionType, "range(4,2,3.3)", List(), 2)

  behavior of "file"
  parseWithWarningTest(FileType, "/some/file", List(new File("/some/file")))

  behavior of "interval"
  parseValueTest(IntervalType, "1d20s3ms", List(Interval.time(1, 0, 0, 20, 3)))
  parseWithWarningTest(IntervalType, "bababa", List())
  parseValueTest(IntervalType, "1d20s3ms,20d1s", List(Interval.time(1, 0, 0, 20, 3),
                                                      Interval.time(20, 0, 0, 1, 0)))

  parseValueTest(IntervalType, "range(0d,2d,12h)", List(Interval.time(0, 0, 0, 0),
                                                       Interval.time(0, 12, 0, 0),
                                                       Interval.time(1, 0, 0, 0),
                                                       Interval.time(1, 12, 0, 0),
                                                       Interval.time(2, 0, 0, 0)))
  
  // TODO: currently build system will not allow to use files from fs in tests
  // parseValueTest(FileType, "local.properties.template", List(new File("local.properties.template")))

  // def testAligner[T](values:List[T], aligned:List[String])

  // behavior of "number aligner"
  // testAligner(List(0,10,-1), List(" 0", "10", "-1"))
}   
