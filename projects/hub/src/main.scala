
import tas.timers.RealTimeTimer

import tas.concurrency.RunLoop
import tas.service.AddressByName

import tas.hub.HubService

import tas.output.logger.ScreenLogger
import tas.output.logger.PrefixTimerTime


object Hub extends App {

  val runLoop = new RunLoop()
  val rtTimer = new RealTimeTimer(runLoop)

  val logger = new PrefixTimerTime(rtTimer, ScreenLogger)


  val service = new HubService(runLoop,
                               logger,
                               new AddressByName("0.0.0.0",
                                                 9101))

  runLoop()
}
