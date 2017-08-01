package tas.paralleling

import java.io.Serializable

trait Action[Result <: Serializable] extends Serializable {
  def run():Result
}
