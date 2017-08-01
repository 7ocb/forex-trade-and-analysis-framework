package bs.utils

import java.io.File

object FsTools {

  private val noFiles = List[File]()

  def delete(file:File):Unit = {
    Utils.verbose { println("Deleting directory: " + file) }

    val deleteOrder = findAll(file).reverse

    deleteOrder.foreach(_.delete)
  }
  
  def findAll(self:File):List[File] = {
    if (! self.exists) noFiles
    else if (self.isFile) List(self)
    else if (self.isDirectory) self :: self.listFiles.map(findAll).toList.flatten
    else throw new RuntimeException("Should never happen!")
  } 
} 
