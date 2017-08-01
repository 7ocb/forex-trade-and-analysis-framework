package tests.previousdaydirection

import tas.previousdaydirection.strategy.SerieSearcher

import org.scalatest.FlatSpec
import org.scalamock.scalatest.MockFactory

import tas.sources.PeriodDirection.{Direction,
                                    Up,
                                    Down,
                                    NoDirection}

class SerieSearcherTests extends FlatSpec with MockFactory {


  def testSerieFound(len:Int, directions:List[Direction], result:Direction) = {
    it should ("in " + directions.map(_.toString).mkString(", ") + " find serie len " + len + " with direction " + result) in {
      val expectCall = mock[(Direction)=>Unit]

      (expectCall.apply _).expects(result)

      val searcher = new SerieSearcher(len, expectCall)

      directions.foreach(direction => searcher.onPeriodEnded(direction))
    } 
  }


  def testFoundNothing(len:Int, directions:List[Direction]) = {
    it should ("not find serie with len " + len + " in " + directions.map(_.toString).mkString(", ") ) in {

      val failIfCalled = mock[(Direction)=>Unit]

      val searcher = new SerieSearcher(len, failIfCalled)

      directions.foreach(direction => searcher.onPeriodEnded(direction))
    } 
  }

  testSerieFound(1, List(Up), Up)
  testSerieFound(1, List(Up, Down), Up)

  testSerieFound(2, List(Up, Up), Up)
  testSerieFound(2, List(Up, NoDirection), Up)
  testSerieFound(2, List(Down, Up, Down, Up, Up), Up)

  testSerieFound(2, List(NoDirection, Up, Down, Down), Down)

  testFoundNothing(2, List(NoDirection, Up, Down, Up, Down))
  testFoundNothing(1, List(NoDirection, NoDirection, NoDirection))


  // testSerieCollected(1, 1, List(Up))
  // testSerieCollected(2, 1, List(Up, Up))
  // testSerieCollected(2, 2, List(Up, Up, Up))
  // testSerieCollected(3, 2, List(Up, Up, Up, Up))
  // testSerieCollected(2, 2, List(Down, Down, NoDirection))

  // testSerieCollectedThenFailed(2, 1, List(Up, Up, Down))
  // testSerieCollectedThenFailed(2, 1, List(Up, NoDirection, Down))
  // testSerieCollectedThenFailed(2, 1, List(Down, Down, Up))
  // testSerieCollectedThenFailed(2, 1, List(Down, NoDirection, Up))
  // testSerieCollectedThenFailed(5, 1, List(Down, NoDirection, Down, Down, Down, Up))
  // testSerieCollectedThenFailed(5, 1, List(Up, NoDirection, Up, NoDirection, Up, Down))
  // testSerieCollectedThenFailed(2, 1, List(NoDirection, Down, Down, Up))
  // testSerieCollectedThenFailed(2, 1, List(NoDirection, Down, NoDirection, Up))

  // testFailToCollectSerie(3, List(NoDirection, Down, Down, Up))
  // testFailToCollectSerie(3, List(NoDirection, Down, NoDirection, Up))
  
  // testFailToCollectSerie(2, List(Up, Down))
  // testFailToCollectSerie(2, List(Down, Up))

  // testFailToCollectSerie(4, List(Up, Up, Up, Down, Down, Down, Down))

  // testCollectNothing(2, List(Up))
  // testCollectNothing(2, List(Down))

  // testCollectNothing(2, List(NoDirection, NoDirection, NoDirection, Down))
  
} 
