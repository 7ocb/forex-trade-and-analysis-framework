package testing.trading.simulation.calculation

import tas.types.Fraction

import tas.trading.simulation.calculation.DrawDown
import tas.trading.simulation.calculation.TradingStatus

import tas.trading.simulation.calculation.ReduceHistoryToValue

import org.scalatest.FlatSpec

class StatisticsCalculationTests extends FlatSpec {
  behavior of "max draw down calculator"

  it should "show 0 in case of continious rising" in {
    val calculator = new DrawDown("prefix", _.balance)

    for ( value <- 1 to 10) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    } 

    assert(calculator.maximumAbsolute === Some(Fraction(0)))

  }

  it should "show drop value in case of single drop" in {
    val calculator = new DrawDown("prefix", _.balance)

    for ( value <- 1 to 10) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 10 to 8 by -1) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 8 to 12) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    } 

    assert(calculator.maximumAbsolute === Some(Fraction(2)))
  }


  it should "show biggest drop value in case of several drops" in {
    val calculator = new DrawDown("prefix", _.balance)

    for ( value <- 1 to 10) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 10 to 8) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 8 to 12) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 12 to 2 by -1) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 2 to 8) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    for ( value <- 8 to 7 by -1) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    }

    assert(calculator.maximumAbsolute === Some(Fraction(10)))
  }


  it should "show all way in case of continious drop" in {
    val calculator = new DrawDown("prefix", _.balance)

    for ( value <- 10 to 1 by -1) {
      calculator.takeIntoAccount(new TradingStatus(null, value, 0, 0, 0, None))
    } 

    assert(calculator.maximumAbsolute === Some(Fraction(9)))
  }

  behavior of "reduce history to value calculator"

  it should "return None if nothing was pushed" in {
    val reducer = new ReduceHistoryToValue[Int](_ => Some(1), _ + _)

    assert(reducer.result === None)
  } 
  
  it should "return first value in case if only one specified" in {
    val reducer = new ReduceHistoryToValue[Fraction](i => Some(i.balance), _ + _)

    reducer.takeIntoAccount(new TradingStatus(null, 1, 0, 0, 0, None))


    assert(reducer.result === Some(Fraction(1)))
  } 

  it should "return reduced value if more than one specified" in {
    val reducer = new ReduceHistoryToValue[Fraction](i => Some(i.balance), _ + _)

    info("check first")
    reducer.takeIntoAccount(new TradingStatus(null, 1, 0, 0, 0, None))
    assert(reducer.result === Some(Fraction(1)))

    info("check second")
    reducer.takeIntoAccount(new TradingStatus(null, 1, 0, 0, 0, None))
    assert(reducer.result === Some(Fraction(2)))

    info("check third")
    reducer.takeIntoAccount(new TradingStatus(null, 1, 0, 0, 0, None))
    assert(reducer.result === Some(Fraction(3)))
  }

  it should "return none if extractor returns none" in {
    val reducer = new ReduceHistoryToValue[Fraction](_ => None, _ + _)

    reducer.takeIntoAccount(new TradingStatus(null, 1, 0, 0, 0, None))

    assert(reducer.result === None)
  } 
} 
