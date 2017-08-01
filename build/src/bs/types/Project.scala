package bs.types

import bs.Paths
import java.io.File

class Project(val root:File,
              val dependencies:List[Project],
              val packJar:Boolean) {

  override def equals(o:Any):Boolean = {
    if (o.isInstanceOf[Project]) {

      val that = o.asInstanceOf[Project]
      root.equals(that.root)
    } else {
      false
    } 
  }

  override def hashCode = root.hashCode

  override def toString:String = {
    "project: " + root
  } 
}
