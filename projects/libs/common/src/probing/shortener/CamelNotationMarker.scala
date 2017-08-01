package tas.probing.shortener

import scala.util.matching.Regex.Match
import scala.annotation.tailrec

class CamelNotationMarker(unsignificantWords:List[String] = List[String](),
                          importantWords:List[String] = List[String](),
                          shortUnsignificantWordsOnLevel:Int,
                          shortenAllButImportantOnLevel:Int,
                          shortenAllOnLevel:Int) {

  private class SplitRegex(regexString:String, pointFromMatch:Match=>Int) {

    private lazy val regex = regexString.r

    def findSplitPoints(string:String) = regex.findAllMatchIn(string).map(pointFromMatch).toList
  }

  private val splitCases = List(new SplitRegex("[a-z][A-Z]", _.start + 1),
                                new SplitRegex("[A-Z][A-Z]", _.start + 1),
                                new SplitRegex("[0-9][A-Z]", _.start + 1),
                                new SplitRegex("[0-9][a-z]", _.start + 1),
                                new SplitRegex("[A-Z][0-9]", _.start + 1),
                                new SplitRegex("[a-z][0-9]", _.start + 1))

  def mark(string:String) = {
    val substringBorders = (List(0, string.length)
                              ++ splitCases.map(_.findSplitPoints(string)).flatten.distinct).sortWith(_ < _)

    @tailrec def extractSubstring(borders:List[Int], accumulator:List[String] = List[String]()):List[String] = {
      val from = borders.head
      val rest = borders.tail

      if (rest.isEmpty) accumulator
      else {
        val to = rest.head

        val substring = string.substring(from, to)

        extractSubstring(rest, substring::accumulator)
      }
    }

    val substrings = extractSubstring(substringBorders).reverse


    substrings.map(markWord)
  }

  private def shortenOnLevel(word:String, level:Int):NameShortener.ShorteningMark = {
    val shortened = word.substring(0, 1).toUpperCase

    new NameShortener.Alternative(word,
                                  shortened,
                                  level)
  }

  private def markWord(word:String):NameShortener.ShorteningMark = {
    if (isNumber(word)) return new NameShortener.Exact(word)

    val lowercased = word.toLowerCase

    if (importantWords.contains(lowercased)) return shortenOnLevel(word, shortenAllOnLevel)

    if (unsignificantWords.contains(lowercased)) return shortenOnLevel(word, shortUnsignificantWordsOnLevel)

    return shortenOnLevel(word, shortenAllButImportantOnLevel)
  }

  private def isNumber(string:String) = ! string.exists( ! _.isDigit)
}
