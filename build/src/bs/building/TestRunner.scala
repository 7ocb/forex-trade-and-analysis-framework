package bs.building

import bs.types.TestsSet

import bs.utils.Utils
import bs.utils.FsTools
import bs.Settings

object TestsRunner  {
  def run(testsSet:List[TestsSet]) = {

    val allTestsDirs = (testsSet
                        // get all binary dirs
                        .map(_.binDir))

    val allTestsClassPaths = (testsSet
                              .map(_.classPath)
                              .flatten
                              .distinct)

    val testFilter = if (Settings.testClass == null) List[String]() 
                     else List("-s", Settings.testClass)
      
    Utils.runProcess(List(Settings.scalaHome + "/bin/scala",
                          "-cp", (allTestsClassPaths.map(_.toString)
                                  ++
                                  allTestsDirs.map(_.toString)).mkString(":"),
                          "org.scalatest.tools.Runner")
                     ++
                     testFilter
                     ++
                     List("-o",
                          "-R", allTestsDirs.map(_.toString.replace(" ", "\\ ")).mkString(" ")),
                   "[tests] ")
  } 
} 
