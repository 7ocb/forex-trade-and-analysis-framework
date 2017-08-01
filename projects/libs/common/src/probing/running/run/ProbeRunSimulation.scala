package tas.probing.running.run

import tas.output.logger.{PrefixTimerTime, Logger}

import java.io.File

import tas.types.{
  Fraction,
  Interval
}

import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.trading.simulation.{
  TradingSimulation,
  SimulationError
}

import tas.readers.TicksFileMetrics

import tas.sources.ticks.{
  TickSource,
  TicksFromSequence
}

import tas.input.Sequence

import tas.timers.JustNowFakeTimer

import tas.probing.running.ProbeRunner

object ProbeRunSimulation {
  abstract class Config() {
    val leverage:Int
    val startBalance:Fraction
    val tradeComissionFactor:Fraction
    val probeTerminateIfEquityRelativeDrawDown:Fraction
  }

}

class ProbeRunSimulation(logging:ProbeRunner.Logging, config:ProbeRunSimulation.Config) extends ProbeRun {

  import tas.trading.simulation.config.{
    Config,
    OpenAndCloseBy,
    ComissionFactor
  }

  import tas.trading.simulation.config.limits.{
    Limits,
    IndependentStops
  }

  import PrematureEndSearcher.PrematureEnd

  protected def tradingLimits:Limits =
    new IndependentStops(stopMinimalDistance = Fraction("0.0005"),
                         takeMinimalDistance = Fraction("0.0005"),
                         delayMinimalDistance = Fraction("0.0005"),
                         freezeDistance = Fraction("0.0005"))

  protected def priceSpread = Fraction.ZERO

  protected def ticksFile:File = throw new RuntimeException("ticksFileName not implemented")

  protected def createTicksSource():TickSource = 
    new TicksFromSequence(timer,
                          Sequence.fromFile(ticksFile,
                                            MetatraderExportedTicks,
                                            TicksBinaryCache),
                          priceSpread)

  protected lazy val ticksSource = createTicksSource()

  protected lazy val inputMetrics = TicksFileMetrics.fromFile(ticksFile)

  protected lazy val timedLogger = new PrefixTimerTime(timer,
                                                       logging.runLogger)

  protected lazy val timer = new JustNowFakeTimer



  protected def tradingSimulationConfig =
    new Config(config.leverage,
               config.startBalance,
               OpenAndCloseBy.WorstPrice,
               tradingLimits,
               new ComissionFactor(config.tradeComissionFactor))

  protected def prematureEndConditionCheckInterval = Interval.days(2)


  private val terminateOnEquityRelativeDD = new PrematureEndSearcher.Condition() {

      val criticalValue = config.probeTerminateIfEquityRelativeDrawDown

      val name:String = "Equity max draw down > " + criticalValue

      def check():Boolean = {
        if (criticalValue <= Fraction.ZERO) return false

        val currentEquityRelativeDD = trading.equityMaxDrawDownRelative.getOrElse(Fraction.ZERO)

        return currentEquityRelativeDD >= criticalValue
      }
    }


  protected def prematureEndConditions =
    List(terminateOnEquityRelativeDD)

  private val prematureEndSearcher =
    new PrematureEndSearcher(timer,
                             inputMetrics.firstTickTime,
                             prematureEndConditionCheckInterval,
                             prematureEndConditions)

  protected val trading =
    new TradingSimulation(tradingSimulationConfig,
                          timer,
                          timedLogger,
                          ticksSource.tickEvent)

  final def run():Boolean = {
    var succeed = false
    try {
      // ensure that timer will stop after data is ended
      timer.callAt(inputMetrics.lastTickTime,
                   timer.stop)

      timer.loop
      succeed = true
    } catch {
      case SimulationError(msg) => {
        logging.combinedStatsLogger.log("Simulation ended with error: " + msg)
      }

      case PrematureEnd(msg) => {
        logging.combinedStatsLogger.log("Simulation terminated premature: " + msg)
      }
    }

    trading dumpResultTo logging.combinedStatsLogger

    succeed
  }
}


