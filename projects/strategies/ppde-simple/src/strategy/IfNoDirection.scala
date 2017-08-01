package tas.ppdesimple.strategy

sealed trait IfNoDirection extends java.io.Serializable

object OppositeIfNoDirection extends IfNoDirection {
  override def toString = "opposite"
}

object SameIfNoDirection extends IfNoDirection {
  override def toString = "same"
}
