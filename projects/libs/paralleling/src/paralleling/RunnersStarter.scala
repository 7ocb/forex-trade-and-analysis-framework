package tas.paralleling

import java.io.File

import scala.collection.mutable.ListBuffer

import tas.utils.args.PropertyFileArguments

object RunnersStarter {

  val runnerClassName = "tas.paralleling.Runner"

  val LimitCoresToUseParameter = "limitCoresToUse"
  val ConfigFileName = ".tas.probe-runners-starter.properties"


  lazy val coresCount = Runtime.getRuntime().availableProcessors()

  lazy val propertyFile = new File(System.getProperty("user.home"),
                                   ConfigFileName)

  lazy val coresUseLimitFromConfig = {
    val props = new PropertyFileArguments(propertyFile.getCanonicalPath())

    val limit = props.value(LimitCoresToUseParameter)
    if (limit.isDefined) {
      parseInt(limit.get,
               ifNonNumber = {
                 println("Warning, in properties file, " + LimitCoresToUseParameter + " property is no a number")
                 Int.MaxValue
               })
    } else {
      Int.MaxValue
    }
  }

  def parseInt(string:String, ifNonNumber: =>Int):Int = {
    try {
      string.toInt
    } catch {
      case nfe:NumberFormatException => {
        ifNonNumber
      }
    }
  }

  val maxRunners = scala.math.min(Runtime.getRuntime().availableProcessors(),
                                  RunnersStarter.coresUseLimitFromConfig)

  def startRunners(serviceTarget:String,
                   limit:Option[Int]) = {

    val count = limit.map(_.min(maxRunners)).getOrElse(maxRunners)

    List.fill(count) {
      RunnerUtility.startClassAsProcess("runner: ",
                                        RunnersStarter.runnerClassName,
                                        serviceTarget)
    }

  }
}

class RunnersStarter extends App {

  private var runnersLimit:Option[Int] = None;
  
  private var serviceTarget:String = tryFindOutServiceAddress()
  private val _runners = new ListBuffer[RunnerUtility.ProcessHandle]
  // try to parse parameters

  if (parseParametes()) {

    _runners ++= RunnersStarter.startRunners(serviceTarget, runnersLimit)
    
  } else printHelp()

  def tryFindOutServiceAddress():String = {
    // TODO: not implemented now
    null
  } 
  
  def printHelp() = {
    println("Start several " + RunnersStarter.runnerClassName + " as runners")
    println("Arguments: <service.address:port> [runners_limit]")
    println("")
    println("  You an specify runners limit, to create less runners")
    println("than cpu cores available.")
  }

  def printHelpAndExit() {
    printHelp()
    sys.exit(1)
  }

  def parseParametes():Boolean = {
    if (args.length > 0) {
      if (args(0) == "help") {
        printHelp()
        return false
      }
      serviceTarget = args(0)
    }

    if (serviceTarget == null) {
      println("error: target server not specified")
      printHelp()
      return false
    } 

    if (args.length > 1) {

      runnersLimit =
        Some(RunnersStarter.parseInt(args(1),
                                     ifNonNumber = {
                                       printHelpAndExit()
                                       0
                                     } ))

    }

    if (args.length > 2) {
      println("extra arguments ignored")
    }

    return true
  } 
  
}

