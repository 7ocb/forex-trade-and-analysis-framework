package tas.sources.ticks

import tas.events.Event
import tas.timers.Timer

import tas.types.{
  Fraction,
  PeriodBid,
  Time,
  Interval,
  Price
}

object TicksFromRemotePeriods {

  type ResultHandler = (List[PeriodBid]) => Unit

  trait PeriodsGetter {

    def setResultHandler(handler:ResultHandler) = _handler = handler

    private var _handler:ResultHandler = null

    protected def passResult(data:List[PeriodBid]) = _handler(data)

    def request(startPeriodStartTime:Option[Time]):Unit
  }
}

import TicksFromRemotePeriods.PeriodsGetter

class TicksFromRemotePeriods(timer:Timer,
                             _getter:PeriodsGetter,
                             _updateInterval:Interval,
                             spread:Fraction) extends TickSource {

  private var lastDispatchedPrice:Option[Fraction] = None
  private var _lastUpdatingPeriod:Option[PeriodBid] = None
  private val _event = Event.newSync[Price]

  override def tickEvent = _event

  _getter.setResultHandler(onPeriodsUpdate)
  _getter.request(None)

  private def onPeriodsUpdate(periods:List[PeriodBid]) = {
    if (periods.size > 0) {
      val lastPeriod = periods.last

      if (_lastUpdatingPeriod == None) {
        // this is case of first data received
        dispatchTicks(allPrices(lastPeriod))

      } else {

        val lastUpdated = _lastUpdatingPeriod.get


        if (lastUpdated.time == lastPeriod.time) {
          // last updating period not changed - we have no new periods.

          dispatchTicks(updatedPrices(lastPeriod))

        } else {
          // we have new periods - we will dispatch updated prices for period
          // we tracked as last before and all ticks for every new period

          lastDispatchedPrice = None

          val interestedRange = periods.filter(_.time >= lastUpdated.time)
          
          val previouslyTracked = interestedRange.head
          
          dispatchTicks(updatedPrices(previouslyTracked)
                          ++ interestedRange.tail.map(allPrices(_)).reduce( _ ++ _))
        }
      }

      _lastUpdatingPeriod = Some(lastPeriod)
    }

    timer.after(_updateInterval) {
      _getter.request(Some(_lastUpdatingPeriod.get.time))
    }
  }

  private def updatedPrices(period:PeriodBid) = {
    val lastUpdated = _lastUpdatingPeriod.get
    var ticks = List[Fraction]()
    
    if (period.bidMax > lastUpdated.bidMax) {
      ticks = ticks.:+(period.bidMax)
    }

    if (period.bidMin < lastUpdated.bidMin) {
      ticks = ticks.:+(period.bidMin)
    }

    val dispatchPriceClose = ticks.size > 0 || lastDispatchedPrice == None || period.bidClose != lastDispatchedPrice.get

    if (dispatchPriceClose) {
      ticks = ticks.:+(period.bidClose)
    }

    ticks
  }

  private def allPrices(period:PeriodBid) = List(period.bidOpen,
                                                 period.bidMax,
                                                 period.bidMin,
                                                 period.bidClose)

  private def dispatchTicks(tickPrices:List[Fraction]):Unit = {
    if (tickPrices.isEmpty) return

    val step = _updateInterval / tickPrices.length
    var offset = Interval.milliseconds(0)

    tickPrices.foreach (price => {

                          timer.after(offset) {
                            _event << Price.fromBid(price, spread)
                          }

                          offset += step
                        })

    lastDispatchedPrice = Some(tickPrices.last)
  }
}
