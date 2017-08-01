package tests.probing.shortener

import org.scalatest.FlatSpec
import tas.probing.shortener.NameShortener
import tas.probing.shortener.CamelNotationMarker
import tas.probing.shortener.NameShortener.{
  ShorteningMark,
  Drop,
  Exact,
  Alternative
}

class NameShortenerTests extends FlatSpec {
  

  case class Expectation(limit:Int, expected:String)

  def testShortening(marked:List[ShorteningMark], expectations:List[Expectation]) = {
    expectations.foreach(
      expectation => {
        it should ("shorten " + marked + " to " + expectation.expected + " if limited to " + expectation.limit) in {
          assert(NameShortener(marked, expectation.limit) === expectation.expected)
        }
      })
  }

  behavior of "NameShortener.Drop"

  testShortening(List(new Exact("valuable"),
                      new Drop("-postfix",
                               1)),
                 List(Expectation(5, "valuable"),
                      Expectation(10, "valuable"),
                      Expectation(15, "valuable"),
                      Expectation(16, "valuable-postfix")))

  testShortening(List(new Exact("valuable"),
                      new Drop("-lessvaluable",
                               2),
                      new Drop("-postfix",
                               1)),
                 List(Expectation(5, "valuable"),
                      Expectation(10, "valuable"),
                      Expectation(21, "valuable-lessvaluable"),
                      Expectation(50, "valuable-lessvaluable-postfix")))

  behavior of "NameShortener.Alternative"

  testShortening(List(new Exact("valuable"),
                      new Alternative("-long-description-variant",
                                      "-shortdescr",
                                      2),
                      new Drop("-postfix",
                               1)),
                 List(Expectation(5, "valuable-shortdescr"),
                      Expectation(35, "valuable-long-description-variant"),
                      Expectation(25, "valuable-shortdescr")))

  behavior of "CamelNotationMarker"

  it should "correcty break to words" in {
    val expected = List(new Alternative("some", "S", 2),
                        new Exact("99"),
                        new Alternative("Test", "T", 2),
                        new Alternative("Text", "T", 2))

    val marker = new CamelNotationMarker(shortUnsignificantWordsOnLevel = 1,
                                         shortenAllButImportantOnLevel = 2,
                                         shortenAllOnLevel = 3)

    assert(marker.mark("some99TestText") === expected)
  }

  it should "take into account important and unimportant words" in {
    val expected = List(new Alternative("min", "M", 3),
                        new Alternative("Max", "M", 3),
                        new Alternative("Some", "S", 2),
                        new Alternative("Of", "O", 1),
                        new Exact("99"),
                        new Alternative("Test", "T", 2),
                        new Alternative("Text", "T", 2))

    val marker = new CamelNotationMarker(importantWords = List("min", "max"),
                                         unsignificantWords = List("of"),
                                         shortUnsignificantWordsOnLevel = 1,
                                         shortenAllButImportantOnLevel = 2,
                                         shortenAllOnLevel = 3)

    assert(marker.mark("minMaxSomeOf99TestText") === expected)
  }
  
}
