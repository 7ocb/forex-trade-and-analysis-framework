package tas.sources.ticks

import tas.timers.Timer

import tas.input.Sequence

import tas.events.Event

import tas.types.{
  Fraction,
  TimedBid,
  TimedTick,
  Price
}

class TicksFromSequence(timer:Timer, sequence:Sequence[TimedBid], spread:Fraction) extends TickSource {
  private val _event = Event.newSync[Price]

  override def tickEvent = _event

  var _nextBidValue:Fraction = null

  private def postNextTick() = {
    if (sequence.haveNext) {
      val nextBid = sequence.next

      _nextBidValue = nextBid.bid

      timer.callAt(nextBid.time,
                   dispatchNextTick)
    }
  }

  private def dispatchNextTick() {
    _event << Price.fromBid(_nextBidValue,
                            spread)
    postNextTick()
  }

  postNextTick()
}
