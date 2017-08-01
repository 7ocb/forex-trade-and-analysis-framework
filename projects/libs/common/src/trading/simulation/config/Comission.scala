package tas.trading.simulation.config

import tas.types.Fraction
import tas.trading.OpenedTradeValues

trait Comission {
  def calculateComission(tradeStatus:OpenedTradeValues):Fraction
}

class ComissionFactor(factor:Fraction) extends Comission {
  def calculateComission(tradeStatus:OpenedTradeValues) = tradeStatus.value * factor
}

object NoComission extends Comission {
  def calculateComission(tradeStatus:OpenedTradeValues) = Fraction.ZERO
}
