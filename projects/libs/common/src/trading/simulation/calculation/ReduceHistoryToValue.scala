package tas.trading.simulation.calculation

class ReduceHistoryToValue[Out](extractFunction:(TradingStatus)=>Option[Out], reduceFunction:(Out, Out)=>Out) extends StatisticsCalculation.ParameterCalculator[Out] {

  private var value:Option[Out] = None
  
  def takeIntoAccount(in:TradingStatus):Unit = {

    val extracted = extractFunction(in)

    if (extracted.isDefined) {
      
      if (value == None) {
        value = Some(extracted.get)
      } else {
        value = Some(reduceFunction(value.get,
                                    extracted.get))
      } 
    } 
  }
                                  
  def result = value
} 
