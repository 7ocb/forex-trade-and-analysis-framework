package tas.trading.simulation.config.limits

import tas.output.logger.Logger

import tas.trading.simulation.TradeSimulationExecutor

import tas.types.{
  Fraction,
  Boundary,
  Trade,
  Price
}

trait Limits {
  def newStopsController(logger:Logger,
                         trade:Trade):TradeSimulationExecutor.StopsController
}




