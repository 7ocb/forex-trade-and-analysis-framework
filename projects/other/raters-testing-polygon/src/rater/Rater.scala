package tas.raters.polygon.rater

import java.io.File

trait Rater {
  def rate(files:List[File]):List[String]
}
