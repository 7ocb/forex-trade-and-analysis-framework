package tas.utils.parsers

import scala.collection.mutable.HashMap

object Arguments {
  def toMap(args:Array[String]) = {
    val map = new HashMap[String, String]
    
    args.foreach(arg => {
      val keyValue = arg.split("=", 2)

      val key = keyValue(0)
      val value = if (keyValue.length > 1) keyValue(1) else null

      val previous = map.put(key, value)

      if (previous.isDefined) {
        println("Warning: command line argument \"" + key + "\" repeated")
      } 
    })

    map
  }
}

abstract class Arguments(args:Array[String]) {
  private val argMap = Arguments.toMap(args)
  
  val Arg_Help     = "help"

  if (isProvided(Arg_Help)) {
    printHelp()
    System.exit(0)
  }

  final def isProvided(key:String) = argMap.get(key).isDefined

  final def mandatoryParameter[T](key:String, parser:String=>T):T = {
    val param = optionalParameter(key, parser)
    if (param.isEmpty) {
      error(key, "unspecified")
    }
    param.get
  }

  final def optionalParameter[T](key:String, parser:String=>T):Option[T] = {
    val param = argMap.get(key)

    if (param.isEmpty) None
    else {
      val paramValue = param.get

      try {
        Some(parser(paramValue))
      } catch {
        case e:Throwable => {
          error(key, "can't parse: " + e.toString)
          None
        } 
      }
    } 
  }

  def printHelp()

  final def error(key:String, msg:String) = {
    println("argument error: " + key + ": " + msg)
    printHelp
    System.exit(1)
  } 
  
}

