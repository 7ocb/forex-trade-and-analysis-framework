package tas.probing

import scala.collection.mutable.ListBuffer
import tas.types.{
  Interval,
  Time
}
import tas.output.logger.ScreenLogger
import tas.output.logger.Logger
import tas.output.format.Formatting
import tas.output.format.Formatter
import tas.probing.types.Type
import scala.annotation.tailrec

import tas.utils.args.{
  ArgumentsSource,
  PropertyFileArguments,
  CommandLineArguments
}

import tas.probing.shortener.{
  NameShortener,
  CamelNotationMarker
}

import tas.probing.running.RunnerUtility

import tas.probing.utils.VariantsIndex

object ShortWordIntervalFormatter extends Formatter {
  def isSupports(o:Any):Boolean = o.isInstanceOf[Interval]
  def format(o:Any):String = o.asInstanceOf[Interval].toStringShortForm
}

trait RunValue[T] {
  def value:T
}

private class ParamWarningLogger(key:String) extends Logger{
  override def log(o:Any*) = ScreenLogger.log((List("parameter warning: ", key, ": ")
                                               ++ o.toList):_*)
}

private class Parameter[T](val paramType:Type[T],
                           val key:String,
                           val description:String) extends RunValue[T] {

  private var _variants:List[T] = null

  def setFrom(string:String) = {
    _variants = paramType.parse(string, new ParamWarningLogger(key))
  }

  def variants:List[T] = _variants

  private var _value:Option[T] = None

  def value = _value.get
  def switchToVariant(index:Int) = _value = Some(variants(index))

  def currentValueAsString:String = paramType.format(value)
  def allVariantsAsStrings:List[String] = variants.map(paramType.format(_))
}



object ProbeApp {
  val ArgumentName_PropertiesFile = "properties"
  val ArgumentName_Help           = "help"
  val ArgumentName_Out            = "out"
  val ArgumentName_StartFromProbe = "startFromProbe"
  val FileName_DefaultProperties  = "probing.properties"

  val DefaultOutDirectory = "out"

  val PredefinedArguments = List(ArgumentName_PropertiesFile,
                                 ArgumentName_Help,
                                 ArgumentName_Out)
}


private class ProbeIterator[Result](parameters:List[Parameter[_]],
                                    isRunValid:()=>Boolean,
                                    action:()=>Result) {

  val _indexes = new VariantsIndex(parameters.map(_.variants.size))

  private def switchParametersToCurrentState() = {
    _indexes.forSlots((parameterIndex, valueIndex) => {
                        parameters(parameterIndex).switchToVariant(valueIndex)
                      })
  }
  
  @tailrec final def runNextStep():Option[Result] = {
    if (_indexes.isOverflow) return None

    switchParametersToCurrentState()


    if (isRunValid()) {
      val returnValue = Some(action())
      _indexes.increment()
      returnValue
    } else {
      _indexes.increment()
      runNextStep()
    }
  }

  @tailrec final def runAllSteps():Unit = {
    if (runNextStep() != None) runAllSteps()
  }
}


abstract class ProbeApp[ConfigType <: Serializable](runnersStarterClass:String = "tas.probing.running.DefaultRunnersStarter") extends App {

  private val MAX_LOG_NAME_LENGTH = 200

  import scala.language.implicitConversions

  private val MinProbesToCalculateEstimation = 4

  private val _parameters = new ListBuffer[Parameter[_]]

  private var _loggingDir = defaultOutDirectory

  implicit def runValue2Value[T](runValue:RunValue[T]):T = runValue.value

  def withParameter[T](paramType:Type[T], key:String, description:String):RunValue[T] = {
    if (ProbeApp.PredefinedArguments.contains(key)) {
      println("Warning: " + key + " will be hided by predefined key")
    }

    val param = new Parameter(paramType, key, description)
    _parameters += param
    param
  }

  def isRunValid:Boolean = true
  def strategyName:String

  def createConfig:ConfigType
  def defaultPropertiesFileName:String = ProbeApp.FileName_DefaultProperties
  def defaultOutDirectory:String = ProbeApp.DefaultOutDirectory

  private var _skipProbes = 0
  private var _totalProbesCount = 0
  private var _probesRunned = 0

  private def gatherProbesInfo = {
    _totalProbesCount += 1
  }

  private def logFileName(maxNameLength:Int) = {
    val singleLogName = "single"
    val prefix = "probe-"
    val mutableParameters = _parameters.filter(_.variants.length > 1)

    if (mutableParameters.length > 0) {

      val marker = new CamelNotationMarker(importantWords = List("factor", "limit", "min", "max", "start", "end"),
                                           unsignificantWords = List("of", "on", "in", "then"),
                                           shortUnsignificantWordsOnLevel = 1,
                                           shortenAllButImportantOnLevel = 2,
                                           shortenAllOnLevel = 3)

      import NameShortener.Drop
      import NameShortener.Exact
      import NameShortener.Alternative

      def dropable(string:String) = new Drop(string, onLevel = 4)

      def exact(string:String) = new Exact(string)

      def alternated(from:String, to:String) = new Alternative(from, to, changeOnLevel = 4)


      val markedForShortening =
        (List(dropable(prefix))
           ++ (mutableParameters
                 .map(parameter => {

                        val allPossibleValues = parameter.allVariantsAsStrings

                        val currentValue = parameter.currentValueAsString
                        val aligned = parameter.paramType.align(currentValue, allPossibleValues)

                        (dropable("[")::
                           marker.mark(parameter.key)
                           ++ List(exact("= " + aligned),
                                   dropable("]")))
                      } )
                 .reduce(_ ++ List(alternated("-", " ")) ++ _)))

      NameShortener(markedForShortening,
                    maxNameLength)
    } else {
      prefix + singleLogName
    }
  }



  private def prefixPostfixLines() = {
    List("strategy: " + strategyName,
         "Parameters:") ++ _parameters.map(parameter => { parameter.key + " = " + Formatting.format(parameter.value) } )
  }

  private def setupSkipProbesCount(source:CommandLineArguments) = {
    try {
      _skipProbes =
        source.value(ProbeApp.ArgumentName_StartFromProbe)
          .map(_.toInt - 1)
          .getOrElse(0)

    } catch {
      case _:NumberFormatException => {
        println("Error: startFrom is not a number")
        System.exit(1)
      }
    }
  }

  private def setupParameters(sources:List[ArgumentsSource]):Boolean = {
    val sourcesToTry = sources.reverse

    def findValueString(key:String):Option[String] = {
      sourcesToTry.foreach(source => {
        val value = source.value(key)
        if (value.isDefined && value.get != null) return value
      } )
      None
    }

    // setup output directory
    val newOut = findValueString(ProbeApp.ArgumentName_Out)

    if (newOut.isDefined) {
      _loggingDir = newOut.get
    }

    // setup parameters
    _parameters.map(parameter => {

      val key = parameter.key
      val value = findValueString(key)
      val presentValue = value.isDefined

      if (presentValue) {
        try {
          parameter.setFrom(value.get)

          if ( parameter.variants.isEmpty ) {
            println("value " + key + " have no variants, wrong range?" )
          }

          ! parameter.variants.isEmpty
        } catch {
          case _ => {
            println("failed to parse value (" + parameter.paramType.name + "): " + key + "=" + value.get)

            false
          }
        }
      } else {
        println(key + ": value not specified")

        false
      }

    } ).reduce( _ && _)
  }

  private def showHelp = {
    println("This is probing runner for \"" + strategyName + "\" strategy.")
    println("")
    println("")
    println("Following probe parameters can be specified (refer 'types reference'")
    println("for detailed information about how to specify values according to type):")
    println("")

    val typesUsed = new ListBuffer[Type[_]]
    
    _parameters.foreach(parameter => {
      val paramType = parameter.paramType
      println("  " + parameter.key + " (" + paramType.name + ") - " + parameter.description)
      if ( ! typesUsed.contains(paramType) ) {
        typesUsed += paramType
      }
    } )

    def describeParam(name:String, descriptions:String*) = {
      println(name + " - " + descriptions.head)
      descriptions.tail.foreach(line => {
        println((" " * (name.length() + 3)) + line)
      } )
    }

    println("")
    println("")
    println("Types reference for used types:")
    println("")

    typesUsed.foreach(paramType => {
      println("  " + paramType.name + ", can be specified as: ")
      paramType.parsers.foreach(parser => {
        println("    " + parser.name + ":")
        println("      value=" + parser.example)
      } )

    } )

    
    println("")
    println("")
    println("Note: parameters can be specified as:")
    println("  key=value pairs in the file " + defaultPropertiesFileName)
    println("  key=value pairs in file, which specified in command line")
    println("            with " + ProbeApp.ArgumentName_PropertiesFile + " option")
    println("  key=value pairs in the command line")
    println("Values will be overriden in this order.")
    println("")
    println("Next keys have special meaning:")

    val onlyCommandLine = "note that this parameter is read only from command line"

    describeParam(ProbeApp.ArgumentName_Help + "=[anything]",
                  "with or without parameters leads to showing this help",
                  onlyCommandLine)

    describeParam(ProbeApp.ArgumentName_Out + "=dirname",
                  "name of directory to put probe logs to",
                  "default value is \"" + ProbeApp.DefaultOutDirectory + "\"")

    describeParam(ProbeApp.ArgumentName_PropertiesFile + "=filename",
                  "properties file to load (see overriding)",
                  onlyCommandLine)

    describeParam(ProbeApp.ArgumentName_StartFromProbe + "=number",
                  "probe number to start from, skipping probes before number",
                  "defaults to 1, which runs all probes",
                  onlyCommandLine)

  }

  private def printProbesCountMessage() = {
    
    def probeCount(count:Int) = if (count == 1) count + " probe"
                                else count + " probes"

    println("Totally " + probeCount(_totalProbesCount) + " to run"
              + ( if (_skipProbes > 0) " (" + probeCount(_skipProbes) + " will be skipped)"
                  else "" ))
  }



  override final def main(args: Array[String]) = {
    super.main(args)

    val argumentsSource = new CommandLineArguments(args)
    val specificPropertiesFile = argumentsSource.value(ProbeApp.ArgumentName_PropertiesFile).getOrElse(null)
    val sources = List(new PropertyFileArguments(defaultPropertiesFileName),
                       new PropertyFileArguments(specificPropertiesFile),
                       argumentsSource)

    setupSkipProbesCount(argumentsSource)

    if (specificPropertiesFile != null) {
      println("Using additional properties file: " + specificPropertiesFile)
    }

    val helpArgumentSpecified = argumentsSource.value(ProbeApp.ArgumentName_Help).isDefined

    if (helpArgumentSpecified) {
      showHelp
    } else {
      if (setupParameters(sources)) {

        newProbeIterator(gatherProbesInfo).runAllSteps()

        println("Parameters information: ")
        _parameters.foreach(parameter => {

          val variants = parameter.variants
          
          println(parameter.key + " variants (" + variants.size + "):")

          variants.foreach(variant => {
            println("  " + Formatting.format(variant))
          } )
        })
        
        printProbesCountMessage()

        // iterateParameters(performProbeRun _)
        if (_totalProbesCount > 0) {
          runProbingService()
        } else {
          println("Can't start probing - no probes to run!")
        }

      } else {
        println("Can't start probes: there is unspecified parameters")
      }
    }
  }

  private def newProbeIterator[Result](action: => Result) = {
    new ProbeIterator(_parameters.toList, isRunValid _, () => action)
  } 

  private def runProbingService() = {
    println("Starting probe controlling service...")

    val probeIterator = newProbeIterator {
      val loggerFile = _loggingDir + "/" + logFileName(MAX_LOG_NAME_LENGTH)

      new ProbeController.Task(loggerFile,
                               prefixPostfixLines(),
                               createConfig)
    }
    
    try {
      val startTime = Time.now
  
      val controller = new ProbeController(new ProbeController.TasksProvider[ConfigType] {
                                             def nextTask = probeIterator.runNextStep()
                                             def count = _totalProbesCount
                                             def skip = _skipProbes
                                           })

      println("Spawning local runners...")

      spawnLocalRunners(controller.serviceBindAddress.toString)


      controller.run()
      println("Total run time: " + (Time.now - startTime))
    } catch {
      case _:ProbeController.CantStartService => {
        println("Error: failed to bind probe controlling service.")
        println("Too many probes already running?")
      } 
    } 
  }
  
  private def spawnLocalRunners(serviceBindAddress:String) = {
    RunnerUtility.startClassAsProcess("",
                                      runnersStarterClass,
                                      serviceBindAddress,
                                      // passing count as limit to make
                                      // sure only one runner to be started
                                      // if only one probe to be run
                                      _totalProbesCount.toString)
  }
}
