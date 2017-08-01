package tas.raters.polygon.rater

import java.io.File

class DumbRater extends Rater {
  def rate(files:List[File]):List[String] = files.map(_.getName())
}
