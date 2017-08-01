package tas.sources.ticks.utils

import tas.timers.Timer

import tas.types.{
  TimedTick,
  Price
}


class SimulatedTicksDispatcher(timer:Timer,
                               inTicks:List[TimedTick],
                               dispatchTick:Price=>Unit,
                               dispatchedLast:()=>Unit) {
  def this(timer:Timer,
           inTicks:List[TimedTick],
           dispatchTick:Price=>Unit) = this(timer,
                                            inTicks,
                                            dispatchTick,
                                            () => {})

  private var ticks = inTicks

  def submitNextDispatch() = timer.callAt(ticks.head.time, dispatchStep)

  submitNextDispatch()
  
  def dispatchStep():Unit = {

    dispatchTick(ticks.head.price)
    ticks = ticks.tail

    if (ticks.isEmpty) {
      dispatchedLast()
    } else {
      submitNextDispatch()
    }
  }
}
