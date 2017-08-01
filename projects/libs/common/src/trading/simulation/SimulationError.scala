package tas.trading.simulation

case class SimulationError(val message:String) extends Exception(message)
