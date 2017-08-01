package bs

import bs.types.Project
import java.io.File


object Paths {

  val FileName_ProjectDescription = "project.xml"
  val FileName_LocalProperties = "local.properties"
  
  
  def projectBinPath(project:Project) = new File(project.root, "bin")
  def projectSourcePath(project:Project) = new File(project.root, "src")
  def projectLibPath(project:Project) = new File(project.root, "libs")

  def distPath(project:Project) = new File(project.root, "dist")
  def distFile(project:Project) = new File(distPath(project),
                                           project.root.getName() + ".jar")
  
  def testsBinPath(project:Project) = new File(project.root, "bin-tests")
  def testsSourcePath(project:Project) = new File(project.root, "tests")


} 
