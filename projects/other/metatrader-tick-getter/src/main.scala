
import tas.utils.HelpTarget

import tas.utils.parsers.{
  Arguments,
  IntervalParser
}

import tas.concurrency.RunLoop
import tas.timers.RealTimeTimer

import tas.sources.ticks.MetatraderExportedTicksSource

import tas.output.logger.{
  ScreenLogger,
  PrefixTimerTime
}

import java.io.File

object Run extends App {

  val arguments = new Arguments(args) {
      def printHelp = {
        println("This is utility for testing ticks source from exported from metatrader ticks")
        println("Mandatory parameters:")
        println("  exportedDir   - directory where exported tick files are stored")
        println("  checkInterval - interval to search tick files")
      }
    }

  val runLoop = new RunLoop

  val rtTimer = new RealTimeTimer(runLoop)

  val logger = new PrefixTimerTime(rtTimer,
                                   ScreenLogger)
 
  
  val ticker =
    new MetatraderExportedTicksSource(runLoop,
                                      rtTimer,
                                      logger,
                                      arguments.mandatoryParameter("checkInterval",
                                                                   IntervalParser(_)),
                                      arguments.mandatoryParameter("exportedDir",
                                                                   new File(_)))

  ticker.tickEvent += (tick => {
    println("" + rtTimer.currentTime + ", tick: " + tick.price)
  })

  runLoop()
} 

class HelpImpl extends HelpTarget("previous day direction enter strategy",
                                  List("Run - start loading ticks from export dir"))

object Help extends HelpImpl
object help extends HelpImpl 
