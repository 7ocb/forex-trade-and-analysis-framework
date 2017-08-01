
import tas.types.{
  Time,
  Interval,
  Period,
  Fraction
}

import tas.readers.TicksFileMetrics
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache
import tas.input.Sequence
import tas.sources.ticks.TickSource
import tas.sources.periods.Ticks2Periods
import tas.timers.JustNowFakeTimer
import tas.output.logger.{ScreenLogger, PrefixTimerTime, Logger, FileLogger}

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  FractionType,
  IntervalType,
  StringType
}

import tas.probing.ProbeApp
import tas.probing.RunValue
import tas.probing.running.ProbeRunner
import tas.probing.running.run.ProbeRun

import java.io.File

import tas.sources.ticks.TicksFromSequence

import tas.sources.PeriodDirection
import tas.sources.PeriodDirection.Direction
import tas.sources.PeriodDirection.NoDirection

case class RunConfig(val filePath:String,
                     val periodLength:Interval,
                     val periodStartShift:Interval,
                     val expectReverseAfterPeriodOf:Fraction) extends java.io.Serializable




class Run(logging:ProbeRunner.Logging, config:RunConfig) extends ProbeRun {
  override def run():Boolean = {

    val timer = new JustNowFakeTimer
    val timedLogger = new PrefixTimerTime(timer, logging.runLogger)

    val ticksFile = new File(config.filePath)

    val ticksSource = new TicksFromSequence(timer,
                                            Sequence.fromFile(ticksFile,
                                                              MetatraderExportedTicks,
                                                              TicksBinaryCache))

    val inputMetrics = TicksFileMetrics.fromFile(ticksFile)

    timer.callAt(inputMetrics.lastTickTime,
                 timer.stop)

    val composer = new Ticks2Periods(timer,
                                     config.periodLength,
                                     inputMetrics.firstTickTime.nextPeriodStartTime(config.periodLength,
                                                                                    config.periodStartShift))

    composer.bindTo(ticksSource.tickEvent)

    composer.periodCompleted += ((period) => timedLogger.log("period ended: " + period))

    class Guesser(name:String,
                  getKey:(Period)=>Fraction) {
      var nextDirectionExpected:Direction = null

      var successful = 0
      var failed     = 0

      composer.periodCompleted += ((period) => {

                                     val currentDirection = PeriodDirection.directionOf(period)

                                     if (nextDirectionExpected != null) {
                                       if (currentDirection == nextDirectionExpected) {
                                         timedLogger.log(name + " successfully guessed this period is " + currentDirection)
                                         successful += 1
                                       } else {
                                         timedLogger.log(name + " guessed this period as " + nextDirectionExpected + " was wrong, it is " + currentDirection)
                                         failed += 1
                                       }
                                     }

                                     if (getKey(period) > config.expectReverseAfterPeriodOf && currentDirection != NoDirection) {
                                       nextDirectionExpected = currentDirection.opposite
                                     } else {
                                       nextDirectionExpected = null
                                     }
                                   })

      def dump(logger:Logger) = {
        def out(message:String) = logger.log(name + " " + message)

        out("succeed: " + successful)
        out("failed: " + failed)
        if (successful + failed == 0) {
          out("No guesses were made")
        } else {
          out("Effectiveness: " + (Fraction(successful) / Fraction((failed + successful)) * 100) + " %")
        }
      }

    }

    val guessers = List(new Guesser("by range", _.range),
                        new Guesser("by abs change", _.change.abs))
    

    timer.loop()

    logging.combinedStatsLogger.log("---------------------")

    guessers.foreach(_.dump(logging.combinedStatsLogger))

    true
  }
}

object Parameters {
  case class ParameterDefinition[T](val theType:Type[T],
                                    val name:String,
                                    val description:String)

  def parameter[T](theType:Type[T],
                   name:String,
                   description:String):ParameterDefinition[T] = new ParameterDefinition(theType,
                                                                                        name,
                                                                                        description)

  val SourceFile                             = parameter(FileType,               "ticks",                                  "Source file with ticks to run test on")

  val PeriodLength                           = parameter(IntervalType,           "periodLength",                           "Length of period to check")
  val PeriodStartShift                       = parameter(IntervalType,           "periodStartShift",                       "Shift of period start")

  val ExpectReverseAfterPeriodOfSize         = parameter(FractionType,           "expectReverseAfterPeriodOfSize",         "The size of previous period to expect reverse after")

}


object Probes extends ProbeApp[RunConfig]("PddeRunnersStarter") {

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] = withParameter(parameter.theType,
                                                                                                parameter.name,
                                                                                                parameter.description)

  val _sourceFile                             = withParameter(Parameters.SourceFile)

  val _periodLength                           = withParameter(Parameters.PeriodLength)
  val _periodStartShift                       = withParameter(Parameters.PeriodStartShift)

  val _expectReverseAfterPeriodOfSize         = withParameter(Parameters.ExpectReverseAfterPeriodOfSize)

  def strategyName = "reverse check"

  def createConfig = new RunConfig(_sourceFile.value.toString,
                                   _periodLength.value,
                                   _periodStartShift.value,
                                   _expectReverseAfterPeriodOfSize.value)
}

object PddeRunnersStarter extends tas.probing.running.RunnersStarter("PddeProbeRunner")

object PddeProbeRunner extends ProbeRunner[RunConfig](new Run(_, _))
