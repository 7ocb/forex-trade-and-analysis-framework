package tas.multiperiod.strategy

sealed trait EnterDirection extends java.io.Serializable
object Direct extends EnterDirection {
  override def toString = "direct"
}

object Opposite extends EnterDirection {
  override def toString = "opposite"
}
