package bs

import java.util.Properties
import java.io.FileInputStream
import java.io.IOException
import bs.utils.Utils

object Settings {

  val Key_ScalaHome = "scala.home"
  val Key_JarTool = "tool.jar"

  var fast = false
  var clean = false
  var tests = false
  var verbose = false

  var testClass:String = null

  var scalaHome:String = null
  var jarTool:String = null
  
  private var _root:String = null
  def root:String = _root

  def root_=(r:String) = {
    _root = r

    try {
      // load properties from properties file
      val props = new Properties()
      props.load(new FileInputStream(_root + "/" + Paths.FileName_LocalProperties))

      def property(key:String):String = {
        val prop = props.getProperty(key)

        Utils.failIf(prop == null) { println("Error: no " + key + " in " + Paths.FileName_LocalProperties) }
        
        prop
      } 
      
      scalaHome = property(Key_ScalaHome)
      jarTool = property(Key_JarTool)
        
    } catch {
      case ioe:IOException => {
        Utils.fail { println("Error: no " + Paths.FileName_LocalProperties + " file detected at build root.") } 
      } 
    } 
  } 

}
