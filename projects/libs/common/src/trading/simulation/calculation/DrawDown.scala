package tas.trading.simulation.calculation

import tas.output.logger.Logger
import tas.types.Fraction

class DrawDown(prefix:String, valueExtractor:(TradingStatus)=>Fraction) extends StatisticsCalculation.DependingOnHistory with StatisticsCalculation.StatisticsOutput {

  private var _knownMaximum:Option[Fraction] = None
  private var _knownMinimum:Option[Fraction] = None
  private var _maxDrawDown:Option[Fraction] = None
  
  def takeIntoAccount(in:TradingStatus):Unit = {
    val currentValue = valueExtractor(in)
    val maximumUpdated = _knownMaximum.getOrElse(currentValue) < currentValue

    if (maximumUpdated || _knownMaximum.isEmpty) {

      val newPotentialMaximalDrawDown = _knownMaximum.getOrElse(Fraction.ZERO) - _knownMinimum.getOrElse(Fraction.ZERO)
      
      val maxDrawDownUpdated = _maxDrawDown.getOrElse(Fraction.ZERO) < newPotentialMaximalDrawDown

      if (maxDrawDownUpdated) {
        _maxDrawDown = Some(newPotentialMaximalDrawDown)
      } 

      _knownMaximum = Some(currentValue)
      _knownMinimum = _knownMaximum
      
    } else {

      val minimumUpdated = _knownMinimum.getOrElse(currentValue) > currentValue

      if (minimumUpdated) {
        _knownMinimum = Some(currentValue)
      } 
    } 
  } 
  
  def dump(l:Logger) = {

    val max = maximumAbsolute
    
    if (max.isDefined) {
      l.log(prefix, " Max Draw Down: ", max.get)
      l.log(prefix, " Max Draw Down %: ", new StatisticsCalculation.Percents(max.get * Fraction(100) /_knownMaximum.get))
    } else {
      l.log(prefix, " Max Draw Down: N/A")
    }
  }

  def maximumRelative:Option[Fraction] = {
    val max = maximumAbsolute
    if (max.isDefined) Some(max.get / _knownMaximum.get)
    else None
  }

  def maximumAbsolute:Option[Fraction] = {
    if (_maxDrawDown.isDefined) Some(_maxDrawDown.get.max(_knownMaximum.get - _knownMinimum.get))
    else if (_knownMaximum.isDefined) Some(_knownMaximum.get - _knownMinimum.get)
    else None
  } 
} 
