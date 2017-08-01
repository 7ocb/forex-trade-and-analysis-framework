package tas.sources.ticks.decomposer

import tas.types.{Interval, PeriodBid, Time, Fraction, TimedBid}

import scala.util.Random

import scala.collection.mutable.ListBuffer

final class PeriodToRandomOrderedTicks(periodInterval:Interval,
                                       seed:Int) extends PeriodToTicksDecomposer {

  private val MaxRandomTicks = 3

  private val _randomizer = new Random(seed)

  private def random(period:PeriodBid):List[Fraction] ={
    val count = _randomizer.nextInt() % (MaxRandomTicks + 1)

    val range = period.bidMax - period.bidMin

    def randomValue = period.bidMin + range * Fraction(_randomizer.nextDouble())

    List.fill(count)(randomValue)
  }

  private def internals(period:PeriodBid):List[Fraction] = {
    def randomPrices = random(period)

    if (_randomizer.nextBoolean()) {
      (randomPrices
         ++ List(period.bidMin)
         ++ randomPrices
         ++ List(period.bidMax)
         ++ randomPrices)
    } else {
      (randomPrices
         ++ List(period.bidMax)
         ++ randomPrices
         ++ List(period.bidMin)
         ++ randomPrices)
    }
  }

  def decompose(period:PeriodBid):List[TimedBid] = {
    var time = period.time

    val ticks = (List(period.bidOpen)
                   ++
                   internals(period)
                   ++
                   List(period.bidClose))

    val tickInterval = periodInterval / (ticks.size + 1)

    ticks.map(tick => new TimedBid({
                                     time += tickInterval
                                     time
                                   }, tick))
  }

}
