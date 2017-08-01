package bs.building

import java.io.File
import bs.Settings
import bs.utils.Utils
import bs.types.Project
import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec
import bs.Paths

object ProjectDetector {

  private def detectWithDependencies(specifiedPath:File, dependencyLine:List[File] = List[File]()):Project = {
    val path = specifiedPath.getCanonicalFile()
    
    def fail(message:String) {
      Utils.fail {
        println("Error: " + message)
        println("Dependency line: " + dependencyLine.reverse)
        println("Detecting project in: " + path)
      } 
    }
    
    if (dependencyLine.contains(path)) fail("Circular dependency!")
    if (! path.isDirectory) fail(path + " is not a directory!")

    Utils.verbose { println("Detecting project in " + path) } 

    val projectDescriptionXmlFile = new File(path,
                                             Paths.FileName_ProjectDescription)

    if (projectDescriptionXmlFile.isFile) {

      val descriptionXml = scala.xml.XML.loadFile(projectDescriptionXmlFile)

      val deplist = ((descriptionXml \ "dependency")
                     // as there is relative path, we need to add base
                     .map(node => new File(path, node.text))
                     .map(dependencyPath => {
                       detectWithDependencies(dependencyPath,
                                              path :: dependencyLine)
                     }))

      val buildJar = (descriptionXml \ "jar").headOption.map(node => (node.text
                                                                      .trim
                                                                      .toLowerCase == "true")).getOrElse(false)
      
      new Project(path,
                  deplist.toList,
                  buildJar)
    } else {
      new Project(path,
                  List[Project](),
                  false)
    }
  }
  
  private def detectProjectsFromRoots(paths:List[String]):List[Project] = {
    if (paths.isEmpty) {

      if (Settings.verbose) {
        println("Detecting project in current work directory")
      }

      List(detectWithDependencies(new File(".")))
    } else {

      if (Settings.verbose) {
        println("Detecting projects from paths:")
        paths.map(path => println("  " + path))
      }

      paths.map(path => detectWithDependencies(new File(path)))
    }
  }

  
  def flattenProjects(projects:List[Project]):List[Project] = {
    projects.map(project => project :: flattenProjects(project.dependencies)).flatten
  } 
  
  def buildOrderFromProjectRoots(roots:List[String]):List[Project] = {
    val projects = detectProjectsFromRoots(roots)

    var allProjects = flattenProjects(projects).distinct

    if (Settings.verbose) {
      println("all projects: " + allProjects.size)
      allProjects.map(project => println("  " + project))
    }

    val buildOrder = new ListBuffer[Project]

    @tailrec def calculateBuildOrder:Unit= {
      if (allProjects.isEmpty) return

      def canBeBuilt(project:Project):Boolean = {
        // if all dependencies of project already in buildOrder, project is buildable
        project.dependencies.find(dep => !buildOrder.contains(dep)).isEmpty
      } 
      
      val partitioned = allProjects.partition(canBeBuilt)
      val canBeBuiltNow = partitioned._1
      val canNotBeBuiltNow = partitioned._2

      Utils.failIf(canBeBuiltNow.isEmpty) {
        println("Build order:")
        buildOrder.map(p => println("  " + p))

        println("Left to order:")
        allProjects.map(p => println("  " + p))

        println("No projects can be built at this point")
      }

      buildOrder ++= canBeBuiltNow
      allProjects = canNotBeBuiltNow

      calculateBuildOrder
    }  

    calculateBuildOrder

    buildOrder.toList
  }
} 
