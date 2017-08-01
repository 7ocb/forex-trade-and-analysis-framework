package tas.utils.args

import tas.utils.parsers.Arguments

class CommandLineArguments(args:Array[String]) extends ArgumentsSource {
  val argsMap = Arguments.toMap(args)

  override def value(key:String) = argsMap.get(key)
  override def sourceName = "command arguments"
} 
