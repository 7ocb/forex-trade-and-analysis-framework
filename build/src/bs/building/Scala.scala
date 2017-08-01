package bs.building

import scala.annotation.tailrec

import bs.utils.FsTools
import bs.utils.Utils
import java.io.File
import bs.Settings

object Scala {

  def isScalaSource(file:String):Boolean = file.toLowerCase.endsWith(".scala")
  def isScalaSource(file:File):Boolean   = isScalaSource(file.toString)

  def isClassFile(file:String):Boolean = file.toLowerCase.endsWith(".class")
  def isClassFile(file:File):Boolean   = isClassFile(file.toString)

  def compile(sourceDirs:List[File], classPath:List[File], targetDir:File):Boolean = {
    Utils.timed("Building") {
      val filesToCompile = (sourceDirs
                            .map(dir => FsTools.findAll(dir))
                            .flatten
                            .filter(_.isFile)
                            .map(_.toString)
                            .filter(name => name.toLowerCase.endsWith(".scala")))

        Utils.failIf(filesToCompile.isEmpty) { println("Error: no source files to compile, check project paths") }


        val classPathArgs = if (classPath.isEmpty) List[String]()
                            else List("-cp", classPath.mkString(":"))

      targetDir.mkdirs()

      val compiler = if (Settings.fast) "fsc"
                     else "scalac"

      // success if 0 returned
      0 == Utils.runProcess(List(Settings.scalaHome + "/bin/" + compiler)
                            ++ classPathArgs
                            ++ List("-d", targetDir.toString)
                            ++ List("-deprecation")
                            ++ List("-feature")
                            ++ filesToCompile,
                            "[fsc] ")
    } 
  }

  def buildJar(outputFile:File, sourcePaths:List[File]) = {
    outputFile.getParentFile().mkdirs()

    @tailrec def pack(paths:List[File], first:Boolean):Unit = {
      if (paths.isEmpty) return
      val pathToPack = paths.head

      val archiveOperation = if (first) "c" // create
                             else "u"       // update
  
      Utils.runProcess(List(Settings.jarTool,
                            archiveOperation + "f",
                            outputFile.toString,
                            "-C", pathToPack.toString,
                            "."))
      pack(paths.tail, false)
    }

    pack(sourcePaths, true)

  } 
}   
