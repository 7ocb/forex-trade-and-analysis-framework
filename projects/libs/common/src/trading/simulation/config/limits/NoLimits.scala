package tas.trading.simulation.config.limits

import tas.output.logger.Logger

import tas.trading.simulation.TradeSimulationExecutor

import tas.types.{
  Fraction,
  Boundary,
  Trade,
  Price
}


object NoLimits extends Limits {
  def newStopsController(logger:Logger,
                         trade:Trade) = new TradeSimulationExecutor.StopsController() {

      def isCanBeClosed:Boolean = true

      def actualStop:Boundary = requestedStop
      def actualTp:Option[Boundary] = requestedTp
      def actualDelay:Option[Boundary] = requestedDelay

      private var stop:Boundary = null
      private var tp:Option[Boundary] = null
      private var delay:Option[Boundary] = null
    }
}

