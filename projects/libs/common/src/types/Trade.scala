package tas.types

sealed trait Trade {
  def *(value:Fraction):Fraction

  def boundaryInDirection(basePrice:Fraction,
                          offset:Fraction = Fraction.ZERO):Boundary

  def boundaryInDirectionEq(basePrice:Fraction,
                            offset:Fraction = Fraction.ZERO):Boundary

  def boundaryCounterDirection(basePrice:Fraction, offset:Fraction = 0):Boundary
  def boundaryCounterDirectionEq(basePrice:Fraction, offset:Fraction = 0):Boundary


  def stop(basePrice:Fraction, offset:Fraction):Boundary = {
    boundaryCounterDirectionEq(basePrice, offset)
  }

  def stop(basePrice:Price, offset:Fraction):Boundary = stop(closePrice(basePrice), offset)
  def stop(price:Fraction):Boundary = stop(price, Fraction.ZERO)

  def takeProfit(basePrice:Fraction, offset:Fraction):Boundary = {
    boundaryInDirectionEq(basePrice, offset)
  }

  // def takeProfit(basePrice:Price, offset:Fraction):Boundary = takeProfit(closePrice(basePrice), offset)
  def takeProfit(price:Fraction):Boundary = takeProfit(price, Fraction.ZERO)

  def delay(price:Fraction, offset:Fraction):Boundary = {
    boundaryCounterDirectionEq(price, offset)
  }

  def delay(price:Price, offset:Fraction):Boundary = delay(openPrice(price), offset)
  def delay(price:Fraction):Boundary = delay(price, Fraction.ZERO)

  def expectedOpenPrice(price:Price, offset:Fraction) = {
    openPrice(price) - (this * offset)
  }

  def openPrice(price:Price):Fraction
  def closePrice(price:Price):Fraction
}

object Sell extends Trade {
  override def toString:String = "sell"
  def *(value:Fraction):Fraction = -value

  def boundaryInDirection(price:Fraction,
                          offset:Fraction) = Boundary < (price - offset)

  def boundaryInDirectionEq(price:Fraction,
                            offset:Fraction) = Boundary <= (price - offset)


  def boundaryCounterDirection(price:Fraction,
                               offset:Fraction) = Boundary > (price + offset)

  def boundaryCounterDirectionEq(price:Fraction,
                                 offset:Fraction) = Boundary >= (price + offset)

  def openPrice(price:Price) = price.bid
  def closePrice(price:Price) = price.ask
}

object Buy extends Trade {
  override def toString:String = "buy"
  def *(value:Fraction):Fraction = +value

  def boundaryInDirection(price:Fraction,
                          offset:Fraction) = Boundary > (price + offset)

  def boundaryInDirectionEq(price:Fraction,
                            offset:Fraction) = Boundary >= (price + offset)

  
  def boundaryCounterDirection(price:Fraction,
                               offset:Fraction) = Boundary < (price - offset)

  def boundaryCounterDirectionEq(price:Fraction,
                                 offset:Fraction) = Boundary <= (price - offset)

  def openPrice(price:Price) = price.ask
  def closePrice(price:Price) = price.bid
}
