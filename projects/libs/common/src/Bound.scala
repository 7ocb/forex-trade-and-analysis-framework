package tas

trait Bound {
  def unbindAll
}

trait NotBound extends Bound {
  override def unbindAll = {}
} 
