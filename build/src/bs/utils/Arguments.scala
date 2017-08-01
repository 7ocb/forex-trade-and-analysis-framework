package bs.utils

import scala.collection.mutable.ListBuffer

class Arguments {
  private type Processor = (String)=>Boolean

  private var processors = new ListBuffer[Processor]
    
  def flag(flagName:String, action: => Unit):Unit = {
    processors += ((arg) => {
      if (arg.trim == flagName) {
        action
        true
      } else false
    })
  }

  def value(valueName:String, action:(String)=>Unit):Unit = {
    processors += ((arg) => {
      val parts = arg.split("=", 2)
      if (parts.length == 2 && parts(0).trim == valueName) {
        action(parts(1))
        true
      } else false
    })
  }
  
  def process(args:List[String]):List[String] = {
    var processed = args
    processors.foreach(processor => {
      processed = processed.filter(arg => !processor(arg))
    } )
    processed
  } 
} 
