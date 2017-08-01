package tas.trading.simulation

import tas.types.{
  Fraction,
  Trade, Buy, Sell,
  Price,
  Boundary
}

import tas.timers.Timer
import tas.Bound
import tas.events.Event
import tas.trading.TradeExecutor

import tas.output.logger.Logger
import tas.output.format.Formatting
import tas.ActiveValue

import tas.trading.TradeBackend
import tas.trading.simulation.calculation.StatisticsCalculation
import tas.trading.simulation.calculation.TradingStatus
import tas.trading.TradeRequest

import tas.trading.TradeValue
import tas.trading.TradeMargin

import tas.trading.TradeResult
import tas.trading.OpenedTradeValues

import scala.collection.mutable.ListBuffer


import tas.trading.simulation.config.Config

object TradingSimulation {
  val MarginCall_MarginLevel_Boundary = Boundary <= Fraction(0.3)


  trait Executor extends TradeExecutor {
    def delayUntil:Option[Boundary]
    def stop:Boundary
    def takeProfit:Option[Boundary]
  }
}

// actual action of this class - call of it's argument every time price
// range is extended, so it is better to rename it and make it more
// universal
final class MarginCallChecker(check:()=>Unit) {

  private var _max:Option[Price] = None
  private var _min:Option[Price] = None

  def reset() = {
    _max = None
    _min = None
  }

  def checkForPrice(price:Price) = {
    if (isOutOfCheckedRange(price)) {

      check()

      _max = Some(price.max(_max.getOrElse(price)))
      _min = Some(price.min(_min.getOrElse(price)))
    } 
  }

  private def isOutOfCheckedRange(price:Price) = {
    _min.isEmpty || _max.isEmpty || price < _min.get || price > _max.get
  } 
  
} 

class TradingSimulation(_config:Config,
                        timer:Timer,
                        _logger:Logger,
                        priceEvent:Event[Price]) extends TradeBackend {

  private var _price:Option[Price] = None

  private var _balance:Fraction = _config.initialBalance

  private val _statistics = new StatisticsCalculation()
  _statistics.takeIntoAccount(new TradingStatus(timer.currentTime, _balance, _balance, Fraction.ZERO, _balance, None))

  private var _bestFinance:Option[Fraction] = None
  private var _worstFinance:Option[Fraction] = None

  private val _executors = new ListBuffer[TradeSimulationExecutor]

  private var _tradeId = 0

  private val _marginCallChecker = new MarginCallChecker(checkMarginCallClose _)

  priceEvent += (price => {
                   _price = Some(price)
                   _executors.foreach(_.handlePriceChanged)
                   _marginCallChecker.checkForPrice(price)

                   if ( ! _executors.isEmpty ) {
                     _equityMayBeChanged()
                   }
                 } )

  private def checkMarginCallClose() = {
    if (isMarginCallCondition) {
      _logger.log("Margin call!")
      _executors.foreach(_.closeByMarginCall)
    }
  } 

  def newTradeExecutor(request:TradeRequest):TradeSimulationExecutor with Bound = {

    if (request.value <= Fraction.ZERO) simulationError("Trade cant be requested with value <= 0")
    
    val trading = new TradeSimulationExecutor.Trading {
        def calculateComission(values:OpenedTradeValues):Fraction = _config.comission.calculateComission(values)
        def price:Price = _price.get
        def leverage:Fraction = _config.leverage
        def onTradeOpened(executor:TradeSimulationExecutor) = TradingSimulation.this.onTradeOpened(executor)
        def onTradeCompleted(executor:TradeSimulationExecutor) = TradingSimulation.this.onTradeClosed(executor)
        def config = _config
        def isMarginAvailable(margin:Fraction):Boolean = margin <= freeMargin
        def logger:Logger = _logger
      }
  
    val executor = new TradeSimulationExecutor(_tradeId,
                                               trading,
                                               request)
    _tradeId += 1

    _executors += executor

    executor
  }

  
  private def onTradeOpened(executor:TradeSimulationExecutor) = {
    _marginCallChecker.reset()
    updateStatusForTradeResult(None)
  }

  private def onTradeClosed(executor:TradeSimulationExecutor):Unit = {
    _marginCallChecker.reset()
    _executors -= executor

    // if it not closed, it was not open, no calculations here needed.
    if ( ! executor.isClosed ) return

    val result = executor.result

    _balance += result.profit
    _balanceMayBeChanged()

    import scala.math.max
    import scala.math.min

    val price = _price.get
    
    executor.logger.log("trade closed at price ",
                        result.closePrice,

                        ", trade profit: ",
                        result.profit,

                        (if (result.comission == Fraction.ZERO) ""
                         else " (comission was: " + Formatting.format(result.comission) + ")"))
    
    updateStatusForTradeResult(Some(result))
  }

  def balance:Fraction = _balance

  def equity:Fraction = balance + openedTrades.map( profitFor(_) ).sum

  def equityMaxDrawDownRelative = _statistics.equityMaxDrawDownRelative

  private def profitFor(executor:TradeSimulationExecutor) = executor.status.profit(_price.get,
                                                                                   _config.comission.calculateComission(executor.status))

  def margin = openedTrades.map( _.status.margin(_config.leverage) ).sum

  def profit = openedTrades.map( profitFor(_) ).sum

  def activeTrades = openedTrades.size
  
  def freeMargin = equity - margin

  def marginLevel = if (margin == Fraction.ZERO) None
                    else Some(equity/margin)

  private def openedTrades = _executors.filter(_.isOpened)

  def dumpResult = dumpResultTo(_logger)

  private def calculateComission(tradeResult:OpenedTradeValues) = Fraction.ZERO

  def dumpResultTo(logger:Logger) = {
    if (_statistics.closedTradesCount > 0) {

      logger.log(" ===== Run statistics =====")

      _statistics.dump(logger)

    } else {
      logger.log("No trades was closed, no changes.")
    }
  }

  private def updateStatusForTradeResult(result:Option[TradeResult]) = {
    updateStatus(new TradingStatus(timer.currentTime,
                                   balance,
                                   equity,
                                   margin,
                                   freeMargin,
                                   result))
  }

  def dumpStatus(tag:String, logger:Logger) = {
    logger.log(tag + ": balance: ", balance,
               ", equity: ", equity,
               ", margin: ", margin,
               ", free margin: ", freeMargin)
  } 
  
  private def updateStatus(status:TradingStatus) = {
    _statistics.takeIntoAccount(status)

    dumpStatus("---current status", _logger)
  }

  private def isMarginCallCondition = {
    val ml = marginLevel
    ml.isDefined && TradingSimulation.MarginCall_MarginLevel_Boundary.isCrossed(ml.get)
  }

  private def simulationError(message:String) = {
    _logger.log(message)
    throw new SimulationError(message)
  }

}
