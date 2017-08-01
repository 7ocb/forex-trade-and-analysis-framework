
import tas.utils.parsers.{
  Arguments,
  TimeParser,
  IntervalParser
}

import tas.utils.HelpTarget

import tas.input.Sequence
import tas.readers.PeriodsFileMetrics
import tas.timers.JustNowFakeTimer
import tas.timers.Timer
import tas.sources.periods.Ticks2Periods
import tas.sources.ticks.PeriodsToTicksSource
import tas.sources.ticks.decomposer.PeriodToOpenMinMaxClose
import tas.sources.ticks.decomposer.PeriodToRandomOrderedTicks

import tas.sources.ticks.decomposer.PeriodToTicksDecomposer

import tas.types.{
  Interval,
  TimedTick,
  Fraction,
  TimedBid
}

import tas.utils.files.naming.{
  SourceFileName,
  Ticks,
  TicksFromPeriods
}

import tas.utils.IO

import java.io.File

import tas.input.format.ticks.metatrader.MetatraderExportedTicks
import tas.input.format.periods.text.PeriodsText
import tas.input.format.periods.bincache.PeriodsBinaryCache

trait Method {
  def decomposer(interval:Interval, seed:Int):PeriodToTicksDecomposer

  def seed(seed:Int):String

  val key:String
}

object MethodOmmc extends Method {
  def decomposer(interval:Interval, seed:Int) = new PeriodToOpenMinMaxClose(interval)
  def seed(seed:Int):String = key

  val key = "ommc"
}

object MethodRandom extends Method {
  def decomposer(interval:Interval, seed:Int) = new PeriodToRandomOrderedTicks(interval, seed)
  def seed(seed:Int):String = seed.toString

  val key = "random"
}

object GenerateTicks extends App {

  val methods = List(MethodOmmc,
                     MethodRandom)

  val Format = MetatraderExportedTicks

  def randomSeed:Int = ((math.random * 100000) % 100000).toInt

  val DefaultMethodKey = MethodRandom.key

  val Arg_In                = "in"
  val Arg_Out               = "out"
  val Arg_Seed              = "seed"
  val Arg_Method            = "method"

  val arguments = new Arguments(args) {
    def printHelp() = {
      println("Utility to transform history files.")
      println("Mandatory arguments: ")
      println(" " + Arg_In + "=<file> - path to source file with periods")
      println("Operation arguments (optional):")
      println(" " + Arg_Method + "=<method> - method to decompose period to ticks:")
      println("   available methods (default is " + DefaultMethodKey + "):")
      println("     " + MethodOmmc.key + " - open-min-max-close decompose: each period decomposed to 4 ticks")
      println("     " + MethodRandom.key + " - random decompose: each period decomposed to random count of ")
      println("              ticks, with random order of min and max")
      println(" " + Arg_Out + "=<dir> - path to output file")
      println("     by default current directory is used")
      println(" " + Arg_Seed + "=<number> - seed use in random generator")
      println("     by default random seed is used")
      println("Alternatively, parameter '" + Arg_Help + "' can be specified to display this help.")
    }
  }

  val inFile  = arguments.mandatoryParameter(Arg_In,  new File(_))
  val outPath = arguments.optionalParameter(Arg_Out, new File(_)).getOrElse(new File("."))

  val methodKey = arguments.optionalParameter(Arg_Method, s => s).getOrElse(DefaultMethodKey)

  val methodOpt = methods.find(_.key == methodKey)

  if (methodOpt == None) {
    println("Error, unknown method: " + methodKey)
    sys.exit(1)
  }

  val method = methodOpt.get

  val seed = arguments.optionalParameter(Arg_Seed, _.toInt).getOrElse(randomSeed)

  val fileMetrics = PeriodsFileMetrics.fromFile(inFile)

  val outFileName = SourceFileName("[pair]",
                                   Ticks,
                                   fileMetrics.firstStartTime,
                                   fileMetrics.lastStartTime,
                                   new TicksFromPeriods("[broker]",
                                                        method.seed(seed)))

  val output = Format.writer(IO.fileOutputStream(new File(outPath,
                                                          outFileName)))

  JustNowFakeTimer { timer =>
    val ticker = new PeriodsToTicksSource(timer,
                                          Fraction.ZERO,
                                          method.decomposer(fileMetrics.interval,
                                                            seed),
                                          Sequence.fromFile(inFile,
                                                            PeriodsText,
                                                            PeriodsBinaryCache))

    ticker.tickEvent += (tick => {
                           output.write(new TimedBid(timer.currentTime,
                                                     tick.bid))
                         } )
  }

  output.close()
}

class HelpImpl extends HelpTarget("fake ticks generator",
                                  List("GenerateTicks - main target, used to generate ticks from periods"))

object Help extends HelpImpl
object help extends HelpImpl 
