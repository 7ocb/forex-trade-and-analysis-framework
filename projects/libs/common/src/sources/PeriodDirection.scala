package tas.sources

import tas.ParameterSet
import tas.ActiveExpresion

import tas.events.Event

import tas.output.logger.Logger
import tas.output.logger.NullLogger

import tas.sources.periods.PeriodSource

import tas.timers.Timer

import tas.types.{
  Period,
  Price
}

object PeriodDirection {
  sealed trait Direction {
    def opposite:Direction
  }
  case object Up extends Direction {
    def opposite = Down
  }
  case object Down extends Direction {
    def opposite = Up
  }
  case object NoDirection extends Direction {
    def opposite = NoDirection
  }

  def directionOf(period:Period):Direction = {
    if (period.priceOpen > period.priceClose) Down
    else if (period.priceClose > period.priceOpen) Up
    else NoDirection
  }

  def directionOf(period:Period, priceAccessor:Price.Accessor) = {
    if (priceAccessor(period.priceOpen) > priceAccessor(period.priceClose)) Down
    else if (priceAccessor(period.priceOpen) < priceAccessor(period.priceClose)) Up
    else NoDirection
  }

  def log(logger:Logger, period:Period, direction:Direction) = {
    logger.log("period direction was =" + direction + "=, started ", period.time,
               ", O: ", period.priceOpen,
               ", C: ", period.priceClose,
               ", L: ", period.priceMin,
               ", H: ", period.priceMax)
  } 
} 

class PeriodDirection(timer:Timer, periodEvent:Event[Period], logger:Logger) extends ActiveExpresion[PeriodDirection.Direction](timer:Timer, ParameterSet.OnValueSet) {
  import PeriodDirection.Direction

  def this(timer:Timer,
           periodEvent:Event[Period]) = this(timer,
                                             periodEvent,
                                             NullLogger)
  
  def this(timer:Timer,
           periodsSource:PeriodSource,
           logger:Logger) = this(timer,
                                 periodsSource.periodCompleted,
                                 logger)

  def this(timer:Timer,
           periodsSource:PeriodSource) = this(timer,
                                              periodsSource.periodCompleted,
                                              NullLogger)

  private val lastPeriod = parameters.binding(periodEvent)

  def recalculate:Direction = {
    import PeriodDirection.directionOf
  
    val period = lastPeriod.value
    val direction = directionOf(period)

    PeriodDirection.log(logger, period, direction)

    direction
  }

} 
