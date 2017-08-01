
import tas.types.{
  Interval,
  Period,
  Fraction,
  Time
}

import tas.probing.types.{FileType, FractionType, IntervalType}

import tas.readers.TicksFileMetrics

import tas.input.Sequence
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.sources.periods.Ticks2Periods

import tas.timers.JustNowFakeTimer

import java.io.File

import tas.output.logger.{PrefixTimerTime, Logger}
import tas.output.format.Formatting

import tas.probing.ProbeApp
import tas.probing.running.ProbeRunner
import tas.probing.running.run.ProbeRun

import tas.sources.ticks.TicksFromSequence

import tas.sources.PeriodDirection.Direction

import tas.previousdaydirection.strategy.Strategy


class CollectStatisticsConfig(val input:Input,
                              val period:Interval,
                              val periodStartShift:Interval,                              
                              val directionDetectionTolerance:Fraction) extends Serializable

class CollectStatisticsRun(logging:ProbeRunner.Logging, config:CollectStatisticsConfig) extends ProbeRun {
  
  val timer = new JustNowFakeTimer


  val timedLogger = new PrefixTimerTime(timer, logging.runLogger)

  val fileName = config.input.fileName
  
  val inputMetrics = TicksFileMetrics.fromFile(fileName)

  val activenessCondition = Config.activeness(timer, inputMetrics.firstTickTime)
  
  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(new File(fileName),
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache))
  
  val composer = new Ticks2Periods(timer,
                                   config.period,
                                   Strategy.nextPeriodStartTime(inputMetrics.firstTickTime,
                                                                config.period,
                                                                config.periodStartShift))

  def logPeriodStats[T](prefix:String, func:(Period)=>T):Unit = {
    composer.periodCompleted += (period => {
                                   if (activenessCondition.isActive) {
                                     timedLogger.log(prefix, Formatting.format(func(period)))
                                   }
                                 })
  }

  logPeriodStats("period completed: ", period => period)

  var previousDirection:Option[Direction] = None
  var directionChanged = false

  logPeriodStats("period direction: ", period => {
                   val newDirection = Strategy.directionOf(period,
                                                           config.directionDetectionTolerance)

                   if (previousDirection != None) {
                     directionChanged = previousDirection.get != newDirection
                   }

                   previousDirection = Some(newDirection)
                   newDirection
                 } )

  logPeriodStats("period direction changed: ", _ => directionChanged)

  logPeriodStats("period range: ", period => { period.priceMax - period.priceMin } )
  logPeriodStats("period result: ", period => { period.priceClose - period.priceOpen } )

  def middlePrice(period:Period) = (period.priceClose + period.priceOpen) / 2

  logPeriodStats("period middle price: ", period => middlePrice(period))

  logPeriodStats("period up deviation: ", period => (period.priceMax - middlePrice(period)))
  logPeriodStats("period down deviation: ", period => (middlePrice(period) - period.priceMin))


  timer.callAt(inputMetrics.lastTickTime,
               timer.stop)

  composer.bindTo(ticksSource.tickEvent)

  final def run():Boolean = {
    timer.loop
    true
  }
}



object CollectStatistics extends ProbeApp[CollectStatisticsConfig]("StatisticsCollectorRunnersStarter") {

  val _sourceFile                  = withParameter(FileType,     "ticks",                       "Source file with ticks to run test on")
  val _period                      = withParameter(IntervalType, "period",                      "Period size to work with")
  val _periodStartShift            = withParameter(IntervalType, "periodStartShift",            "PeriodStartShift size to work with")
  val _directionDetectionTolerance = withParameter(FractionType, "directionDetectionTolerance", "Tolerance when detecting period direction")

  override def defaultPropertiesFileName = "statistics.properties"
  override def defaultOutDirectory = "statistics"

  def strategyName = "periods statistics collector"

  def createConfig = new CollectStatisticsConfig(new Input(_sourceFile.value.toString),
                                                 _period.value,
                                                 _periodStartShift.value,
                                                 _directionDetectionTolerance.value)
}

object StatisticsCollectorRunnersStarter extends tas.probing.running.RunnersStarter("StatisticsCollectorProbeRunner")


object StatisticsCollectorProbeRunner extends tas.probing.running.ProbeRunner[CollectStatisticsConfig](new CollectStatisticsRun(_, _)) 
