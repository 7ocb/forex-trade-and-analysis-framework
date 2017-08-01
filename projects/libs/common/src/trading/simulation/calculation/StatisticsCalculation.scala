package tas.trading.simulation.calculation

import tas.types.{
  Sell,
  Buy
}

import tas.trading.TradeResult

import tas.output.logger.Logger
import tas.output.format.Formatter
import tas.output.format.Formatting
import scala.collection.mutable.ListBuffer

import tas.types.Fraction

object StatisticsCalculation {

  class Percents(val percents:Fraction)

  Formatting.addFormatter(new Formatter {
                            def isSupports(o:Any) = o.isInstanceOf[Percents]
                            def format(o:Any) = {
                              val percents = o.asInstanceOf[Percents]
                              Formatting.format(percents.percents) + " %"
                            }
                          } )

  trait ParameterCalculator[Out] {
    def takeIntoAccount(in:TradingStatus):Unit
    def result:Option[Out]
  }

  private [calculation] trait StatisticsOutput {
    def dump(l:Logger)
  }

  private [calculation] trait DependingOnHistory {
    def takeIntoAccount(in:TradingStatus):Unit
  }

  private [calculation] trait SingleResult[T] {
    def result:Option[T]
  }
  
  private [calculation] trait DumpingSingleValue[T] extends SingleResult[T] with StatisticsOutput {
    val name:String

    final def dump(l:Logger) = {
      if (result.isDefined) {
        l.log(name, ": ", result.get)
      } else {
        l.log(name, ": ", "N/A")
      }
    }
  }
  
  private class HistoryParameter[T](val name:String,
                                    val calculator:ParameterCalculator[T]) extends DumpingSingleValue[T] with DependingOnHistory {
    override def takeIntoAccount(in:TradingStatus) = calculator.takeIntoAccount(in)
    override def result = calculator.result
  }

  private class CalculationParameter[T](val name:String, calculation: =>Option[T]) extends DumpingSingleValue[T] {
    override def result = calculation
  }

  private class PercentCalculation(val name:String, overall: =>Fraction, value: =>Fraction) extends DumpingSingleValue[Percents] {
    override def result = {
      if (overall != Fraction.ZERO) {
        Some(new Percents((value * Fraction(100)) / overall))
      } else {
        None
      }
    }
  }

  private abstract class CountCalculator extends ParameterCalculator[Int] {
    private var _count = 0
    def takeIntoAccount(in:TradingStatus) = if (ifCountThis(in).getOrElse(false)) _count += 1
    def result = Some(_count)

    def ifCountThis(in:TradingStatus):Option[Boolean]
  }
}

private [calculation] trait OutputsSet {

  // make StatisticsCalculation created
  StatisticsCalculation
  
  import StatisticsCalculation.StatisticsOutput
  import StatisticsCalculation.DependingOnHistory

  
  private val _outputs = new ListBuffer[StatisticsOutput]
  
  protected def add[T <: StatisticsOutput](o:T):T = {
    _outputs += o
    o
  }


  protected def Section(name:String):Unit = {
    _outputs += new StatisticsOutput {
        def dump(logger:Logger) = {
          logger.log("---- ", name, ":")
        }
      }
  }

  private lazy val _historical = _outputs.filter(_.isInstanceOf[DependingOnHistory]).map(_.asInstanceOf[DependingOnHistory])

  def takeIntoAccount(in:TradingStatus) = {
    _historical.foreach(_.takeIntoAccount(in))
  }

  def dump(logger:Logger) = {
    _outputs.foreach(_.dump(logger))
  }
}

class StatisticsCalculation extends OutputsSet {
  import StatisticsCalculation.ParameterCalculator
  import StatisticsCalculation.HistoryParameter
  import StatisticsCalculation.CalculationParameter
  import StatisticsCalculation.CountCalculator
  import StatisticsCalculation.PercentCalculation
  import StatisticsCalculation.SingleResult


  private def summaryOnAccountProperty(name:String,
                               accessor:(TradingStatus)=>Fraction,
                               storeDrawdown:DrawDown=>Unit = null) = {

    Section(name)

    
    add(new HistoryParameter("End " + name,
                             new ReduceHistoryToValue[Fraction](in => Some(accessor(in)),
                                                                       (was, last) => last)))

    add(new HistoryParameter("Start " + name,
                             new ReduceHistoryToValue[Fraction](in => Some(accessor(in)),
                                                                       (was, last) => was)))
    
    add(new HistoryParameter("Max " + name,
                             new ReduceHistoryToValue[Fraction](in => Some(accessor(in)),
                                                                       _.max(_))))

    add(new HistoryParameter("Min " + name,
                             new ReduceHistoryToValue[Fraction](in => Some(accessor(in)),
                                                                       _.min(_))))

    val drawdown = add(new DrawDown(name, accessor))

    if (storeDrawdown != null) {
      storeDrawdown(drawdown)
    }
  }
  
  private var BalanceDrowdown:DrawDown = null
  summaryOnAccountProperty("Balance", _.balance, storeDrawdown = dd => BalanceDrowdown = dd)

  private var EquityDrawdown:DrawDown = null
  summaryOnAccountProperty("Equity", _.equity, storeDrawdown = dd => EquityDrawdown = dd)
  summaryOnAccountProperty("Margin", _.margin)
  summaryOnAccountProperty("Free Margin", _.freeMargin)

  Section ("Profit/Loss")
  
  private val BestProfit = add(new HistoryParameter("Best Profit",
                                                    new ReduceHistoryToValue[Fraction](_.tradeResult.map(_.profit),
                                                                                              _.max(_))))

  private val WorstProfit = add(new HistoryParameter("Worst Profit",
                                                     new ReduceHistoryToValue[Fraction](_.tradeResult.map(_.profit),
                                                                                               _.min(_))))

  private val GrossProfit = add(new HistoryParameter("Gross Profit",
                                                     new ReduceHistoryToValue[Fraction](_.tradeResult.map(_.profit.max(Fraction.ZERO)),
                                                                                               _ + _)))
  
  private val GrossLoss = add(new HistoryParameter("Gross Loss",
                                                   new ReduceHistoryToValue[Fraction](_.tradeResult.map(_.profit.min(Fraction.ZERO)),
                                                                                             _ + _)))
  
  private val TotalNetProfit = add(new CalculationParameter("Total Net Profit",
                                                            Some(GrossProfit.result.get + GrossLoss.result.get)))
  
  private val ProfitFactor = add(new CalculationParameter("Profit Factor",
                                                          if (GrossLoss.result.getOrElse(Fraction.ZERO) != Fraction.ZERO) {
                                                            Some(GrossProfit.result.get / (- GrossLoss.result.get))
                                                          } else {
                                                            None
                                                          } ))
  
  private val ExpectedPayoff = add(new CalculationParameter("Expected Payoff",
                                                            Some(TotalNetProfit.result.get / TotalTrades.result.get)))






  
  private val RecoveryFactor = add(new CalculationParameter("Recovery Factor",
                                                            if (BalanceDrowdown.maximumAbsolute.getOrElse(Fraction.ZERO) != Fraction.ZERO) {
                                                              Some(TotalNetProfit.result.get / BalanceDrowdown.maximumAbsolute.get)
                                                            } else {
                                                              None
                                                            } ))

  Section ("Trades")
  
  private val TotalTrades = add(new HistoryParameter("Total Trades",
                                                     new CountCalculator {
                                                       def ifCountThis(in:TradingStatus) = Some(in.tradeResult.isDefined)
                                                     }))

  
  private val SellPositions = add(new HistoryParameter("Sell Positions Count",
                                                       new CountCalculator {
                                                         def ifCountThis(in:TradingStatus) = in.tradeResult.map(_.tradeType == Sell)
                                                       }))

  private val SellPositionsWonCount = add(new HistoryParameter("Sell Positions Won Count",
                                                               new CountCalculator {
                                                                 def ifCountThis(in:TradingStatus) = in.tradeResult.map(in => in.tradeType == Sell && in.profit > Fraction.ZERO)
                                                               }))

  private val SellPositionsWonPercent =
    add(new PercentCalculation("Sell Positions Won %",
                               Fraction(SellPositions.result.get),
                               Fraction(SellPositionsWonCount.result.get)))
  

  private val BuyPositions = add(new HistoryParameter("Buy Positions Count",
                                                      new CountCalculator {
                                                        def ifCountThis(in:TradingStatus) = in.tradeResult.map(_.tradeType == Buy)
                                                      }))

  private val BuyPositionsWonCount = add(new HistoryParameter("Buy Positions Won Count",
                                                              new CountCalculator {
                                                                def ifCountThis(in:TradingStatus) = in.tradeResult.map(in => in.tradeType == Buy && in.profit > Fraction.ZERO)
                                                              }))

  private val BuyPositionsWonPercent =
    add(new PercentCalculation("Buy Positions Won %",
                               Fraction(BuyPositions.result.get),
                               Fraction(BuyPositionsWonCount.result.get)))

  private val ProfitTradesCount = add(new HistoryParameter("Profit Trades Count",
                                                           new CountCalculator {
                                                             def ifCountThis(in:TradingStatus) = in.tradeResult.map(_.profit > Fraction.ZERO)
                                                           } ))

  private val ProfitTradesPercent =
    add(new PercentCalculation("Profit Trades Percent",
                               Fraction(TotalTrades.result.get),
                               Fraction(ProfitTradesCount.result.get)))

  private val LossTradesCount = add(new HistoryParameter("Loss Trades Count",
                                                         new CountCalculator {
                                                           def ifCountThis(in:TradingStatus) = in.tradeResult.map(_.profit < Fraction.ZERO)
                                                         } ))

  private val LossTradesPercent =
    add(new PercentCalculation("Loss Trades Percent",
                               Fraction(TotalTrades.result.get),
                               Fraction(LossTradesCount.result.get)))

  private val AverageProfit = add(new CalculationParameter("Average Profit Trade",
                                                           if (ProfitTradesCount.result.getOrElse(0) != 0) {
                                                             Some(GrossProfit.result.get / ProfitTradesCount.result.get)
                                                           } else None))

  private val AverageLoss = add(new CalculationParameter("Average Loss Trade",
                                                         if (LossTradesCount.result.getOrElse(0) != 0) {
                                                           Some(GrossLoss.result.get / LossTradesCount.result.get)
                                                         } else None))

  def equityMaxDrawDownRelative = EquityDrawdown.maximumRelative
  def closedTradesCount = TotalTrades.result.get

}

