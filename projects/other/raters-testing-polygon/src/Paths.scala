package tas.raters.polygon

import java.io.File

class Paths(root:File) {

  lazy val testsPath = new File(root, "tests")
  lazy val dataPath = new File(root, "data")

}
