package tas.trading.simulation.config

import tas.types.Fraction

trait OpenAndCloseBy {
  def takeClosePrice(current:Fraction,
                     takePrice:Fraction):Fraction

  def stopClosePrice(current:Fraction,
                     stopPrice:Fraction):Fraction

  def delayOpenPrice(current:Fraction,
                     delayPrice:Fraction):Fraction
}

object OpenAndCloseBy {
  object CurrentPrice extends OpenAndCloseBy {
    def takeClosePrice(current:Fraction,
                       takePrice:Fraction) = current

    def stopClosePrice(current:Fraction,
                       stopPrice:Fraction) = current

    def delayOpenPrice(current:Fraction,
                       delayPrice:Fraction) = current
    
  }

  object WorstPrice extends OpenAndCloseBy {
    def takeClosePrice(current:Fraction,
                       takePrice:Fraction) = takePrice

    def stopClosePrice(current:Fraction,
                       stopPrice:Fraction) = current

    def delayOpenPrice(current:Fraction,
                       delayPrice:Fraction) = delayPrice
  }
}
