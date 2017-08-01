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

object MtLikeStops {

}

class MtLikeStops(stopMinimalDistance:Fraction,
                  takeMinimalDistance:Fraction,
                  delayMinimalDistance:Fraction,
                  freezeDistance:Fraction) extends Limits {


  def newStopsController(logger:Logger,
                         trade:Trade) =
    new TradeSimulationExecutor.StopsController()
  {

    private def priceForStopsCheck = {
      val useDelayPrice = delayValue != None && !isOpened

      if (useDelayPrice) delayValue.get.value
      else priceClose(currentPrice)
    }

    override protected def doUpdate() = {
      if (stopValue == null) initializeStops()
      else if (isOpened) correctStops()
      else correctStopsForDelayedTrade()
    }

    private def farthestOf(stops:Boundary*) = stops.toList.sortWith( !_.includes(_) ).head

    private def initializeStops() = {
      if (isOpened || requestedDelay == None) {
        delayValue = None
      } else {
        delayValue = Some(farthestOf(requestedDelay.get,
                                     trade.delay(currentPrice,
                                                 delayMinimalDistance)))
      }

      setClosestToRequiredPossibleStops()
    }

    private def setClosestToRequiredPossibleStops() = {
      if (requestedTp != None) {

        val minimalSetableTp = trade.takeProfit(priceForStopsCheck,
                                                delayMinimalDistance)

        takeProfitValue = Some(farthestOf(requestedTp.get,
                                          minimalSetableTp))
      } else {
        takeProfitValue = None
      }

      val minimalSetableStop = trade.stop(priceForStopsCheck,
                                          stopMinimalDistance)

      stopValue = farthestOf(minimalSetableStop,
                             requestedStop)
    }

    private def correctStops(forceMove:Boolean = false) = {
      // setClosestToRequiredPossibleStops()
    }

    private def correctStopsForDelayedTrade() = {
      // move delay point if needed
      //
      // if delay point was moved, adjust stops

      // val delayUnsatisfied = 

    }

    def isCanBeClosed:Boolean = !isFreezed

    def actualStop:Boundary           = stopValue
    def actualTp:Option[Boundary]     = takeProfitValue
    def actualDelay:Option[Boundary]  = delayValue

    private def isFreezed:Boolean = {
      if (freezeDistance == Fraction.ZERO) return false

      val closePrice = priceClose(currentPrice)

      val freezeRangeBottom = closePrice - freezeDistance
      val freezeRangeTop = closePrice + freezeDistance

      def isInFreezeZone(value:Fraction) = value >= freezeRangeBottom && value <= freezeRangeTop


      return (isOpened
                && (isInFreezeZone(stopValue.value)
                      || (delayValue.map(delay => isInFreezeZone(delay.value)).getOrElse(false))))
    }

    private def priceClose(price:Price):Fraction = trade.closePrice(price)

    // private var isOpened = false

    private var stopValue:Boundary = null
    private var takeProfitValue:Option[Boundary] = null
    private var delayValue:Option[Boundary] = null
  }

}
