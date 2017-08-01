
import java.io.File


import bs.Settings
import bs.utils.Utils
import bs.building.Builder
import bs.building.TestsRunner
import bs.building.ProjectDetector
import bs.types.TestsSet

import bs.types.Project

import bs.utils.Arguments



object BuildSystem extends App {

  val projectRoots = new Arguments {
    flag ("bootstrap", { /* ignore this flag */ })
    flag ("fast",      Settings.fast = true)
    flag ("clean",     Settings.clean = true)
    flag ("tests",     Settings.tests = true)
    flag ("verbose",   Settings.verbose = true)
    value("root",      root => Settings.root = root)
    value("test",      testClass => {
      Settings.tests = true
      Settings.testClass = testClass
    } )    
  }.process(args.toList)


  Utils.verbose {
    println("=== BS Settings")
    println("fast: " + Settings.fast)
    println("clean: " + Settings.clean)
    println("tests: " + Settings.tests)
    println("verbose: " + Settings.verbose)
    println("root: " + Settings.root)
    println("scala home: " + Settings.scalaHome)
  }

  if (!Settings.fast) {
    println("Consider using 'fast' option for using fsc instead of scalac")
  }

  // the rest of args threated as project roots
  val buildOrder = ProjectDetector.buildOrderFromProjectRoots(projectRoots)
  
  Utils.verbose {
    println("Build order:")
    buildOrder.map(p => println("  " + p))
  }

  val builder = new Builder

  if (Settings.clean) {

    Utils.verbose {
      println("Cleaning up before building")
    } 
  
    buildOrder.foreach(builder.clean)
  } 

  var compiledTests = List[TestsSet]()

  buildOrder.foreach(project => {
                       val testsFromThisProject = builder.build(project,
                                                                compiledTests)

                       if (testsFromThisProject != None) {
                         compiledTests = compiledTests ++ List(testsFromThisProject.get)
                       }

                     } )

  if (Settings.tests && compiledTests.size > 0) {
    TestsRunner.run(compiledTests)
  } 
}
