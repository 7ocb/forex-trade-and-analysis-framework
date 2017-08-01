
import java.io.File

import tas.input.Sequence
import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.ticks.bincache.TicksBinaryCache

import tas.periodstatisticscollector.collectors.{
  Collector,
  PeriodValue,
  CalculatedValue,
  PeriodWay,
  SlidingAverageCollector,
  DerivedCollector
}

import tas.probing.{
  ProbeApp,
  RunValue
}

import tas.probing.running.{
  ProbeRunner,
  RunnersStarter
}

import tas.probing.running.run.ProbeRun

import tas.probing.types.{
  Type,
  IntType,
  FileType,
  IntervalType
}

import tas.types.{
  Interval,
  Fraction,
  Price,
  PeriodBid
}


import tas.timers.JustNowFakeTimer
import tas.output.logger.PrefixTimerTime


import tas.readers.TicksFileMetrics

import tas.sources.ticks.TicksFromSequence
import tas.sources.periods.Ticks2Periods

import tas.sources.PeriodDirection



import scala.collection.mutable.ListBuffer

case class RunConfig(sourceTicks:File,
                     period:Interval,
                     periodStartShift:Interval,
                     slidingAverageSteps:Int)

object Run {
  val Spread = Fraction.ZERO
}

class Run(logging:ProbeRunner.Logging,
          config:RunConfig) extends ProbeRun {

  val priceAccessor = Price.Bid
  val collectors = new ListBuffer[Collector[_]]


  val timer = new JustNowFakeTimer
  val timedLogger = new PrefixTimerTime(timer, logging.runLogger)

  val inputMetrics = TicksFileMetrics.fromFile(config.sourceTicks)

  val ticksSource = new TicksFromSequence(timer,
                                          Sequence.fromFile(config.sourceTicks,
                                                            MetatraderExportedTicks,
                                                            TicksBinaryCache),
                                          Run.Spread)

  val periodsComposer =
    new Ticks2Periods(timer,
                      ticksSource.tickEvent,
                      config.period,
                      inputMetrics.firstTickTime.nextPeriodStartTime(config.period,
                                                                     config.periodStartShift))

  val subperiodInterval = Interval.minutes(1)

  val subperiodsComposer =
    new Ticks2Periods(timer,
                      ticksSource.tickEvent,
                      subperiodInterval,
                      inputMetrics.firstTickTime.nextPeriodStartTime(subperiodInterval))

  periodsComposer.periodCompleted += { periodToDump =>
    timedLogger.log("--- Ended period: " + PeriodBid(periodToDump))
  }

  collect(new PeriodValue(periodsComposer,
                          "start time",
                          _.time))

  collect(new PeriodValue(periodsComposer,
                          "direction",
                          PeriodDirection.directionOf(_,
                                                      priceAccessor)))

  val rangeCollector =
    collect(new PeriodValue(periodsComposer,
                            "range",
                            _.range(priceAccessor)))

  collectDerived(collectDerived(rangeCollector))
  collectDerived(collectDerived(collectSliding(rangeCollector)))

  val changeCollector =
    collect(new PeriodValue(periodsComposer,
                            "change",
                            _.change(priceAccessor)))

  collectDerived(collectDerived(changeCollector))
  collectDerived(collectDerived(collectSliding(changeCollector)))

  val deltaCollector =
    collect(new PeriodValue(periodsComposer,
                            "delta",
                            _.change(priceAccessor).abs))

  collectDerived(collectDerived(deltaCollector))
  collectDerived(collectDerived(collectSliding(deltaCollector)))

  val wayCollector =
    collect(new PeriodWay(periodsComposer,
                          subperiodsComposer,
                          Price.Bid))

  collectDerived(collectDerived(wayCollector))
  collectDerived(collectDerived(collectSliding(wayCollector)))


  val r2dCollector =
    collect(new CalculatedValue[Fraction]
              ("r2d",
               () => for ( range <- rangeCollector.value;
                           delta <- deltaCollector.value if delta != Fraction.ZERO)
                     yield range/delta))

  collectDerived(collectDerived(r2dCollector))
  collectDerived(collectDerived(collectSliding(r2dCollector)))

  val maxMinusOpenCollector =
    collect(new PeriodValue(periodsComposer,
                            "max - open",
                            period => priceAccessor(period.priceMax) - priceAccessor(period.priceOpen)))

  collectDerived(collectDerived(maxMinusOpenCollector))
  collectDerived(collectDerived(collectSliding(maxMinusOpenCollector)))

  val openMinusMinCollector =
    collect(new PeriodValue(periodsComposer,
                            "open - min",
                            period => priceAccessor(period.priceOpen) - priceAccessor(period.priceMin)))

  collectDerived(collectDerived(openMinusMinCollector))
  collectDerived(collectDerived(collectSliding(openMinusMinCollector)))


  periodsComposer.periodCompleted += { periodIgnored =>
    collectors.foreach(collector => {
                         val value = collector.value
                         if (value.isDefined) {
                           timedLogger.log(collector.name, ": ", value.get)
                         }
                       } )
  }

  timer.callAt(inputMetrics.lastTickTime, timer.stop)


  override def run():Boolean = {


    timer.loop()

    true
  }

  private def collect[T](collector:Collector[T]):Collector[T] = {
    collectors += collector
    collector
  }

  def collectSliding(collector:Collector[Fraction]) = {
    val sliding = new SlidingAverageCollector(periodsComposer,
                                              config.slidingAverageSteps,
                                              collector)

    collectors += sliding

    sliding
  }

  def collectDerived(collector:Collector[Fraction]) = {
    val derived = new DerivedCollector(periodsComposer,
                                       collector)
    collectors += derived

    derived
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

  val SourceFile          = parameter(FileType,     "ticks",               "Source file with ticks to collect statistics from")
  val Period              = parameter(IntervalType, "period",              "Period size to work with")
  val PeriodStartShift    = parameter(IntervalType, "periodStartShift",    "Period's shift from normal period start point")
  val SlidingAverageSteps = parameter(IntType,      "slidingAverageSteps", "Count of steps to calculate sliding averages")
}


object CollectStatistics extends ProbeApp[RunConfig]("CollectStatisticsRunnersStarter") {

  override def defaultPropertiesFileName = "collector.properties"

  def withParameter[T](parameter:Parameters.ParameterDefinition[T]):RunValue[T] =
    withParameter(parameter.theType,
                  parameter.name,
                  parameter.description)

  val _sourceFile          = withParameter(Parameters.SourceFile)
  val _period              = withParameter(Parameters.Period)
  val _periodStartShift    = withParameter(Parameters.PeriodStartShift)
  val _slidingAverageSteps = withParameter(Parameters.SlidingAverageSteps)


  def strategyName = "periods statistics collector"

  def createConfig = new RunConfig(_sourceFile.value,
                                   _period.value,
                                   _periodStartShift.value,
                                   _slidingAverageSteps.value)
}

object CollectStatisticsRunnersStarter extends tas.probing.running.RunnersStarter("CollectStatisticsProbeRunner")

object CollectStatisticsProbeRunner extends ProbeRunner[RunConfig](new Run(_, _))
