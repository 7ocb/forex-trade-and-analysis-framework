package tas.paralleling

import java.io.Serializable

private [paralleling] class ActionHandle[T <: Serializable](action:Action[T],
                                                            onResult:T=>Unit) {

  def actionToSend:Action[_ <: Serializable] = action

  def processResult(result:Serializable) = onResult(result.asInstanceOf[T])
}
