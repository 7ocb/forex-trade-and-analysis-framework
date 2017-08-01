package tas.utils

object Runtime {
  lazy val classPathString = {

    val urls = java.lang.Thread.currentThread.getContextClassLoader match {
        case cl: java.net.URLClassLoader => cl.getURLs.toList
        case _ => throw new RuntimeException("classloader is not a URLClassLoader")
      }

    val separator = System.getProperty("path.separator")

    urls.map(_.getFile)
      .distinct
      .mkString(separator)
  }

}
