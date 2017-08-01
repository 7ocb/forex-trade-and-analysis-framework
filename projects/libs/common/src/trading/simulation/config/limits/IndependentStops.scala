package tas.trading.simulation.config.limits

import tas.output.logger.Logger

import tas.trading.simulation.TradeSimulationExecutor

import tas.types.{
  Fraction,
  Boundary,
  Trade,
  Price
}

import tas.utils.format.Format.Tp

object IndependentStops {

  private def selectBoundary(current:Option[Boundary], minimalSetable:Boundary, requested:Boundary) = {
    if (current != None
          && (current.get == minimalSetable
                || current.get.includes(minimalSetable))) current.get
    else if (minimalSetable == requested
               || minimalSetable.includes(requested)) requested
    else minimalSetable
  }

  private abstract class DelayedValue[Type >: Null](logger:Logger,
                                                    name:String) {
    private var _currentValue:Type = null
    private var _requestedValue:Type = null

    private var isSatisfied = true

    def set(value:Type) {
      _requestedValue = value

      performSet()
    }

    def currentValue = _currentValue
    def requestedValue = _requestedValue

    def format(value:Type):String

    def tryUpdate():Boolean = {
      _currentValue = null

      performSet()
    }

    def tryUpdatePreservingCurrent():Boolean = {
      if ( ! isSatisfied ) {
        performSet()
      } else false
    }

    private def performSet():Boolean = {
      val previous = _currentValue

      _currentValue = performSet(_requestedValue)

      isSatisfied = _currentValue == _requestedValue

      val isChanged = _currentValue != previous

      if (isChanged) {
        logger.log(name, " ", format(_currentValue), " set",
                   if ( isSatisfied ) " as requested"
                   else ", requested " + format(_requestedValue))
      }

      isChanged
    }

    def performSet(requested:Type):Type
  }
}

class IndependentStops(stopMinimalDistance:Fraction,
                       takeMinimalDistance:Fraction,
                       delayMinimalDistance:Fraction,
                       freezeDistance:Fraction) extends Limits {


  def newStopsController(logger:Logger,
                         trade:Trade) = new TradeSimulationExecutor.StopsController()
  {

    import IndependentStops.{
      DelayedValue,
      selectBoundary
    }

    private val stopValue = new DelayedValue[Boundary](logger, "stop") {
        def performSet(requestedStop:Boundary) = {
          val minimalSetableStop =
            trade.stop(priceForStopsCheck,
                       stopMinimalDistance)


          selectBoundary(if (currentValue == null) None
                         else Some(currentValue),
                         minimalSetableStop,
                         requestedStop)
        }

        def format(stop:Boundary) = stop.toString
      }

    private val takeProfitValue = new DelayedValue[Option[Boundary]](logger, "take") {
        def performSet(requestedTp:Option[Boundary]) = {
          val minimalSetableTp =
            trade.takeProfit(priceForStopsCheck,
                             takeMinimalDistance)

          if (requestedTp == None) None
          else Some(selectBoundary(if (currentValue == null) None
                                   else currentValue,
                                   minimalSetableTp,
                                   requestedTp.get))
        }

        def format(tp:Option[Boundary]) = tp.tpToString
      }

    private val delayValue = new DelayedValue[Option[Boundary]](logger, "delay") {
        def performSet(requestedDelay:Option[Boundary]) = {

          val minimalSetableDelay = trade.delay(currentPrice,
                                                delayMinimalDistance)

          if (requestedDelay == None) None
          else Some(selectBoundary(if (currentValue == null) None
                                   else currentValue,
                                   minimalSetableDelay,
                                   requestedDelay.get))
        }

        def format(tp:Option[Boundary]) = tp.tpToString
      }

    private def priceForStopsCheck = {
      val useDelayPrice = !isOpened && delayValue.currentValue.isDefined

      if (useDelayPrice) delayValue.currentValue.get.value
      else priceClose(currentPrice)
    }

    override protected def doUpdate() = {
      val forceMoveClosingStops = !isOpened && delayValue.tryUpdatePreservingCurrent()

      if (forceMoveClosingStops) {
        stopValue.tryUpdate()
        takeProfitValue.tryUpdate()
      } else {
        stopValue.tryUpdatePreservingCurrent()
        takeProfitValue.tryUpdatePreservingCurrent()
      }
    }

    override def requestedStop_=(stop:Boundary):Unit = {
        super.requestedStop_=(stop)
        stopValue.set(stop)
      }
    override def requestedTp_=(tp:Option[Boundary]):Unit = {
        super.requestedTp_=(tp)
        takeProfitValue.set(tp)
      }

    override def requestedDelay_=(delay:Option[Boundary]):Unit = {
        super.requestedDelay_=(delay)
        delayValue.set(delay)
      }


    def isCanBeClosed:Boolean = !isFreezed

    def actualStop:Boundary           = stopValue.currentValue
    def actualTp:Option[Boundary]     = takeProfitValue.currentValue
    def actualDelay:Option[Boundary]  = delayValue.currentValue

    private def isFreezed:Boolean = {
      if (freezeDistance == Fraction.ZERO) return false

      val closePrice = priceClose(currentPrice)

      val freezeRangeBottom = closePrice - freezeDistance
      val freezeRangeTop = closePrice + freezeDistance

      def isInFreezeZone(value:Fraction) = value >= freezeRangeBottom && value <= freezeRangeTop


      return (isOpened
                && (isInFreezeZone(stopValue.currentValue.value)
                      || (delayValue.currentValue.map(delay => isInFreezeZone(delay.value)).getOrElse(false))))
    }

    private def priceClose(price:Price):Fraction = trade.closePrice(price)

    // private var isOpened = false
  }

}
