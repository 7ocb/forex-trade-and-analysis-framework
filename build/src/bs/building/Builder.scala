package bs.building

import bs.Settings
import bs.utils.FsTools
import bs.utils.Utils

import bs.types.Project
import bs.types.TestsSet
import bs.Paths

import java.io.File

class Builder {

  private def rebuild(sourceDirs:List[File],
                      classPathDirs:List[File],
                      targetDir:File):Boolean = {
    Utils.verbose {
      println("Rebuilding: " + sourceDirs)
      println("To: " + targetDir)
      println("With classpath: " + classPathDirs)
    } 

    Scala.compile(sourceDirs, classPathDirs, targetDir)
  }

  def clean(project:Project) = {
    List(Paths.projectBinPath _,
         Paths.testsBinPath _,
         Paths.distPath _)
    .map(getPathToClean => FsTools.delete(getPathToClean(project)))
  } 

  def libPaths(project:Project) = {
    val libDir = Paths.projectLibPath(project)
    if (libDir.isDirectory) libDir.listFiles().filter(_.isDirectory).toList
    else List[File]()
  }

  def build(project:Project, alreadyCompiledTests:List[TestsSet]):Option[TestsSet] = {
    println("Building project: " + project)

    val allDependencies = ProjectDetector.flattenProjects(project.dependencies)




    val classPaths = (allDependencies.map(Paths.projectBinPath(_))
                        ++ libPaths(project)
                        ++ allDependencies.flatMap(libPaths(_)))


    val projectBinDir = Paths.projectBinPath(project)

    val success = rebuild(List(Paths.projectSourcePath(project)),
                          classPaths,
                          projectBinDir)

    Utils.failIf(!success) { println("Building of " + project + " failed, aborting") }

    if (project.packJar) {
      Scala.buildJar(Paths.distFile(project),
                     projectBinDir::classPaths.filter(_.isDirectory))
    } 
    
    val testsSourceDir = Paths.testsSourcePath(project)
    
    if (Settings.tests && testsSourceDir.isDirectory) {
      println("Building tests for project: " + project)

      val testClassPath = (projectBinDir
                             :: classPaths
                             ++ scalatestSupportLibs
                             ++ alreadyCompiledTests.map(_.binDir))

      val testBin = Paths.testsBinPath(project)
  
      val success = rebuild(List(testsSourceDir),
                            testClassPath,
                            testBin)

      Utils.failIf(!success) { println("Building of tests for " + project + " failed, aborting") }

      Some(new TestsSet(testBin,
                        testClassPath))
    } else {
      None
    }  
  }

  private def scalatestSupportLibs:List[File] = {
    (List("scala-library.jar",
          "scala-actors.jar").map(Settings.scalaHome + "/lib/" + _)
     ++
     List("scalatest_2.10-2.0.M5b.jar",
          "scalamock-scalatest-support_2.10-3.0.1.jar",
          "scalamock-core_2.10-3.0.1.jar").map(Settings.root + "/lib/" + _)
     )
    .map(new File(_))
  } 
}
