package tas.trading.simulation

import tas.types.{
  Fraction,
  Trade,
  Price,
  Boundary
}

import tas.NotBound
import tas.events.Event
import tas.trading.TradeExecutor

import tas.output.logger.Logger
import tas.output.format.Formatting
import tas.ActiveValue
import tas.trading.TradeRequest
import tas.trading.TradeStatus
import tas.trading.TradeResult
import tas.trading.OpenedTradeValues
import tas.trading.TradeParameters

import tas.trading.simulation.config.{
  Config,
  OpenAndCloseBy
}

import tas.utils.format.Format.Tp



object TradeSimulationExecutor {

  trait Trading {
    def calculateComission(values:OpenedTradeValues):Fraction
    def price:Price

    def onTradeOpened(executor:TradeSimulationExecutor)
    def onTradeCompleted(executor:TradeSimulationExecutor)
    def config:Config
    def isMarginAvailable(margin:Fraction):Boolean
    def logger:Logger
  }

  def checkCrossingExpectedOpenPrice(isOpened:Boolean,
                                     boundary:Option[Boundary],
                                     price:Fraction,
                                     delay:Option[Boundary]):Boolean = {
    if (boundary == None) false
    else if (delay.isEmpty || isOpened) boundary.get.isCrossed(price)
    else delay.get.firstCrossingWillAlsoCross(boundary.get)
  }

  trait StopsController {
    private var _isOpened:Boolean = false
    private var currentPriceValue:Price = null
    protected def currentPrice = currentPriceValue

    protected final def isOpened = _isOpened

    final def update(tradeOpened:Boolean,
                    price:Price):Unit = {
      currentPriceValue = price
      _isOpened = tradeOpened
      doUpdate()
    }

    protected def doUpdate() = { /* nothing to do */ }

    private var requestedStopValue:Boundary = null
    private var requestedTpValue:Option[Boundary] = null
    private var requestedDelayValue:Option[Boundary] = null

    def requestedStop_=(newValue:Boundary) = requestedStopValue = newValue
    def requestedTp_=(newValue:Option[Boundary]) = requestedTpValue = newValue
    def requestedDelay_=(newValue:Option[Boundary]) = requestedDelayValue = newValue

    def requestedStop:Boundary = requestedStopValue
    def requestedTp:Option[Boundary] = requestedTpValue
    def requestedDelay:Option[Boundary] = requestedDelayValue

    def isCanBeClosed:Boolean

    def actualStop:Boundary
    def actualTp:Option[Boundary]
    def actualDelay:Option[Boundary]
  }
}

final class TradeSimulationExecutor(tradeId:Long,
                                    trading:TradeSimulationExecutor.Trading,
                                    tradeRequest:TradeRequest) extends TradingSimulation.Executor with NotBound {


  protected[simulation] val logger = new Logger() {
      override def log(os:Any*) = trading.logger.log((List("Trade %03d".format(tradeId),
                                                           " (" , tradeRequest.tradeType, "): ")
                                                        ++ os):_*)
    }

  private val stops = trading.config.limits.newStopsController(logger,
                                                               tradeRequest.tradeType)
  stops.update(false, trading.price)



  import trading.calculateComission
  import trading.isMarginAvailable
  import TradeSimulationExecutor.checkCrossingExpectedOpenPrice

  def handlePriceChanged = handlePriceOrBoundariesChanged

  private def handlePriceOrBoundariesChanged = {
    if ( isClean ) {
      if (isDelayedMet) {
        doOpenTrade(logMessageAndDeactivate)
      }
    } else if ( isOpened ) {
      recheckBoundaries()
    }

    stops.update(isOpened, trading.price)

    if (stops.isCanBeClosed && _closePending) {
      closeTrade
    }
  }





  private var _onExtrnallyClosed:()=>Unit = null
  private var _onOpened:()=>Unit = null

  private var _openPrice:Fraction = Fraction.ZERO

  private var _status:TradeStatus = null
  private var _result:TradeResult = null

  private var _closePending = false

  logger.log("trade requested: value: ", tradeRequest.value)

  private def openPrice = {
    if (isOpened) _status.openPrice
    else throw new IllegalStateException("Not opened, have no open price")
  }

  def result = {
    if ( ! isClosed ) throw new IllegalStateException("Can't get result if trade not closed!")

    _result
  }

  def status = {
    if ( ! isOpened ) throw new IllegalStateException("Can't get status, not opened")

    _status
  }

  def isOpened = _status != null && _result == null
  def isClean =  _status == null && _result == null
  def isClosed = _status == null && _result != null
  

  def delayUntil = stops.actualDelay // _delay.currentValue
  def stop =       stops.actualStop // _stop.currentValue
  def takeProfit = stops.actualTp // _takeProfit.currentValue

  override def openTrade(stopValue:Boundary, takeProfit:Option[Boundary], onOpened:()=>Unit, onExternallyClosed:()=>Unit):Unit = {

    if ( ! isClean ) throw new IllegalStateException("Not clean!")

    stops.requestedDelay = tradeRequest.delayUntil

    _onOpened = onOpened
    _onExtrnallyClosed = onExternallyClosed
    setStopInternal(stopValue)
    setTakeProfitInternal(takeProfit)

    if (isDelayedMet) {
      doOpenTrade(simulationError)
    } else {
      logger.log("will open if: ", delayUntil.get)
    }
  }

  private def doOpenTrade(failAction:(String)=>Unit):Unit = {
    if ( ! isClean ) throw new IllegalStateException("Not clean!")

    if (isStopOrTPCrossed) {
      failAction("Tried to open trade when stop or tp crossed")
      return
    }

    val currentPrice = priceOpen(trading.price)

    val openPrice = if (delayUntil.isEmpty) currentPrice
                    else trading.config.openAndCloseBy.delayOpenPrice(currentPrice,
                                                                      delayUntil.get.value)

    val openStatus = tradeRequest.openWithPrice(openPrice)

    if ( ! isMarginAvailable(openStatus.margin(trading.config.leverage))) {

      failAction("Tried to open trade with tradeMargin > freeMargin")
      return
    }

    _status = openStatus

    logger.log("was opened on price = ", openPrice, ", margin: ", _status.margin(trading.config.leverage), ", value: ", _status.value)

    trading.onTradeOpened(this)
    _onOpened()
  }

  private def isDelayedMet = {

    val trade = tradeRequest.tradeType

    val delayedMet = delayUntil.isEmpty || delayUntil.get.isCrossed(trade.openPrice(trading.price))

    delayedMet
  }

  private def isStopOrTPCrossed = {
    val price = priceClose(trading.price)

    val stopValue = stops.actualStop
    val stopCrossed = stopValue != null && stopValue.isCrossed(price)


    val tpValue = stops.actualTp
    val tpCrossed = (tpValue != null
                       && tpValue.isDefined
                       && tpValue.get.isCrossed(price))

    stopCrossed || tpCrossed
  }

  override def closeTrade = {
    if ( ! isOpened ) {
      // if we closing trade which was not opened - we have cancelled delayed trade
      logger.log("cancelled")
    }

    if (stops.isCanBeClosed) {

      if ( !isClosed ) {
        close()

        trading.onTradeCompleted(this)
      }

    } else {
      logger.log("Trade freezed, can't be closed")
      _closePending = true
    }
  }

  def closeByMarginCall = {
    logger.log("closing by margin call")
    onExternallyClosed()
  }

  private def setStopInternal(stopValue:Boundary):Unit = {
    logger.log("requested stop changed: ", stopValue)

    validateNewStopBoundary("stop", Some(stopValue))

    stops.requestedStop = stopValue

    if ( ! isOpened ) return

    recheckBoundaries()
  }

  override def setStop(stopValue:Boundary):Unit = {
    setStopInternal(stopValue)

    handlePriceOrBoundariesChanged
  }

  override def setTakeProfit(takeProfitValue:Option[Boundary]):Unit = {
    setTakeProfitInternal(takeProfitValue)

    handlePriceOrBoundariesChanged
  }

  def setTakeProfitInternal(takeProfitValue:Option[Boundary]):Unit = {

    logger.log("requested take changed: ", takeProfitValue.tpToString)

    if (takeProfitValue.isDefined
          && !isOpened
          && delayUntil.isDefined
          && delayUntil.get.cmp.isBelow == takeProfitValue.get.cmp.isBelow) {

      // this is error because in this case one of:
      //
      // 1. tp is crossed at the opening time
      //
      // 2. tp acts as stop
      simulationError("Take profit is invalid - it have same direction as delay, will work as stop.")
    }

    validateNewStopBoundary("take profit", takeProfitValue)

    stops.requestedTp = takeProfitValue

    if ( ! isOpened ) return

    recheckBoundaries()
  }

  private def recheckBoundaries() = {
    val trade = tradeRequest.tradeType
    val closePrice = trade.closePrice(trading.price)

    def getPriceClosedOn(boundaryPrice:Fraction,
                         getPriceSelector:((OpenAndCloseBy)=>((Fraction, Fraction)=>Fraction))):Option[Fraction] =
      Some(getPriceSelector(trading.config.openAndCloseBy)(closePrice,
                                                           boundaryPrice))

    val stopValue = stops.actualStop

    if (stopValue != null && stopValue.isCrossed(closePrice)) {
      logger.log("closing by stop: " + stopValue)

      onExternallyClosed(getPriceClosedOn(stopValue.value,
                                          _.stopClosePrice))
    }

    val takeProfitValue = stops.actualTp

    if (takeProfitValue != null && takeProfitValue.map(_.isCrossed(closePrice)).getOrElse(false)) {
      logger.log("closing by take: " + takeProfitValue.tpToString)

      onExternallyClosed(getPriceClosedOn(takeProfitValue.get.value,
                                          _.takeClosePrice))
    }
  }

  private def onExternallyClosed(suggestedClosePrice:Option[Fraction] = None):Unit = {
    if ( ! isOpened ) throw new IllegalStateException("Not opened, can not be closed.")

    close(suggestedClosePrice)

    _onExtrnallyClosed()

    trading.onTradeCompleted(this)
  }

  private def close(suggestedClosePrice:Option[Fraction] = None) = {
    if (_status != null) {

      val comission = calculateComission(_status)

      _result = suggestedClosePrice match {
          case Some(suggestedPrice) => _status.close(suggestedPrice, comission)
          case None                 => _status.close(trading.price,  comission)
        }

    }
    _status = null
  }

  private def validateNewStopBoundary(boundaryName:String,
                                      boundary:Option[Boundary]) = {

    val delayToCheckAgainst = stops.requestedDelay

    val notDelayed = delayToCheckAgainst.isEmpty || isOpened

    val invalid = boundary.map(boundary => {
                                 if (notDelayed) {
                                   boundary.isCrossed(priceClose)
                                 } else {
                                   delayToCheckAgainst.get.firstCrossingWillAlsoCross(boundary)
                                 }
                               } ).getOrElse(false)

    if ( invalid ) {

      val targetPrice = if (notDelayed) "current"
                        else "expected open"

      simulationError("New " + boundaryName + " is invalid - it crossed at "
                        + targetPrice
                        + " price")
    }
  }

  private def simulationError(message:String) = {
    logger.log(message)
    throw new SimulationError(message)
  }

  private def logMessageAndDeactivate(message:String) = {
    if ( ! isClean ) throw new IllegalStateException("Not clean!")
    logger.log("Deactivating because: ", message)
    trading.onTradeCompleted(this)
  }

  private def priceCurrent = trading.price

  private def priceClose(price:Price):Fraction = tradeRequest.tradeType.closePrice(price)
  private def priceClose:Fraction = priceClose(priceCurrent)

  private def priceOpen(price:Price):Fraction = tradeRequest.tradeType.openPrice(price)
  private def priceOpen:Fraction = priceOpen(priceCurrent)

}
