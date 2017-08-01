
import tas.utils.HelpTarget

import tas.types.{
  Interval,
  Time,
  Fraction,
  PeriodBid
}

import tas.utils.parsers.{
  Arguments,
  TimeParser,
  IntervalParser
}

import tas.readers.{
  PeriodsSequence,
  ShiftedPeriodsSequence,
  PeriodsFileMetrics
}
import tas.timers.JustNowFakeTimer
import tas.timers.Timer
import tas.sources.periods.Ticks2Periods
import tas.sources.ticks.PeriodsToTicksSource
import tas.sources.ticks.decomposer.PeriodToOpenMinMaxClose

import tas.utils.IO

import java.io.File

import tas.input.format.periods.text.PeriodsText


object ConvertHistory extends App {

  val Arg_In       = "in"
  val Arg_Out      = "out"
  val Arg_First    = "first"
  val Arg_Last     = "last"
  val Arg_Interval = "interval"
  val Arg_Shift    = "shift"

  val Arg_Help     = "help"
  
  val arguments = new Arguments(args) {
    def printHelp = {
      println("Utility to transform history files.")
      println("Mandatory arguments: ")
      println(" " + Arg_In + "=<file> - path to source file")
      println(" " + Arg_Out + "=<file> - path to output file")
      println("Operation arguments (optional):")
      println(" " + Arg_First + "=<time>   - start time of first period to present in output")
      println("     this allows to trim history from start.")
      println(" " + Arg_Last + "=<time>   - start time of last period to present in output")
      println("     this allows to trim history from end.")
      println(" " + Arg_Interval + "=<interval> - new interval of periods to present in output")
      println("     this allows to change period interval.")
      println(" " + Arg_Shift + "=[-]<interval> - shift periods on specified interval, can be negative.")
      println("Alternatively, parameter '" + Arg_Help + "' can be specified to display this help.")
      println("Formats:")
      println(" Time format: " + TimeParser.sample)
      println(" Interval format: " + IntervalParser.sample)
    } 
  }

  if (arguments.isProvided(Arg_Help)) {
    arguments.printHelp
    System.exit(0)
  }

  val inPath  = arguments.mandatoryParameter(Arg_In,  str => str)
  val outPath = arguments.mandatoryParameter(Arg_Out, str => str)

  val fileMetrics = PeriodsFileMetrics.fromFile(inPath)



  val timeShifter = arguments.optionalParameter(Arg_Shift, (string) => {


                                                  if (string(0) == '-') {
                                                    val interval = IntervalParser(string.substring(1))

                                                    (time:Time)=> time - interval
                                                  } else {
                                                    val interval = IntervalParser(string.substring(1))

                                                    (time:Time)=> time + interval
                                                  }

                                                } ).getOrElse((time:Time) => time)


  val firstStartTime = timeShifter(arguments.optionalParameter(Arg_First, TimeParser.apply).getOrElse(fileMetrics.firstStartTime))
  val lastStartTime = timeShifter(arguments.optionalParameter(Arg_Last, TimeParser.apply).getOrElse(fileMetrics.lastStartTime))

  val interval = arguments.optionalParameter(Arg_Interval, IntervalParser.apply).getOrElse(fileMetrics.interval)

  if (interval.isZero) {
    println("Zero-time interval")
    System.exit(1)
  } 

  val output = PeriodsText.writer(IO.fileOutputStream(new File(outPath)))

  val spread = Fraction.ZERO

  JustNowFakeTimer { timer =>
    val ticker = new PeriodsToTicksSource(timer,
                                          spread,
                                          new PeriodToOpenMinMaxClose(fileMetrics.interval),
                                          new ShiftedPeriodsSequence(PeriodsSequence.fromFile(inPath),
                                                                     timeShifter))

    val periods = new Ticks2Periods(timer,
                                    ticker.tickEvent,
                                    interval,
                                    firstStartTime,
                                    lastStartTime + interval)

    periods.periodCompleted += (period => output.write(PeriodBid(period)))
  }

  output.close()
} 

class HelpImpl extends HelpTarget("previous day direction enter strategy",
                                  List("ConvertHistory - history converting tools"))

object Help extends HelpImpl
object help extends HelpImpl 
