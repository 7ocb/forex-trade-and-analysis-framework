package tas.ppdesimple.strategy

import tas.sources.PeriodDirection.Direction
import tas.sources.PeriodDirection.Up
import tas.sources.PeriodDirection.Down
import tas.sources.PeriodDirection.NoDirection

import scala.collection.mutable.ListBuffer

// serie lenght - how many consecutive same directions must be located to
// threat it as serie.
class SerieSearcher(periodsToDetectSerie:Int) {

  private val _directions = new ListBuffer[Direction]
  
  def onPeriodEnded(direction:Direction):Unit = {
    _directions += direction

    if (_directions.size > periodsToDetectSerie) {
      _directions.remove(0,
                         _directions.size - periodsToDetectSerie)
    }
  }
  
  private def resetSerie() = {
    _directions.clear()
  }

  def serieDirection:Direction = {
    if (_directions.size < periodsToDetectSerie) return NoDirection

    val differentDirections = _directions.distinct

    if (differentDirections.size == 1) differentDirections.head
    else NoDirection
  }
 
  
} 
