package tas.utils.args

import scala.collection.mutable.HashMap
import java.util.Properties

import tas.utils.IO

class PropertyFileArguments(filename:String) extends ArgumentsSource {

  private val properties = new Properties
  
  {
    try {
      val file = IO.fileInputStream(filename)
      properties.load(file)
      file.close
    } catch {
      case _:Throwable => ;
    } 
  } 
  
  override def value(key:String) = {
    val out = properties.getProperty(key)
    if (out != null) Some(out)
    else None
  } 
  override def sourceName = "property file " + filename
} 
