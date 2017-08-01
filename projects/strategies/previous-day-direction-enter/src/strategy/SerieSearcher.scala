package tas.previousdaydirection.strategy

import tas.sources.PeriodDirection.Direction
import tas.sources.PeriodDirection.Up
import tas.sources.PeriodDirection.Down
import tas.sources.PeriodDirection.NoDirection

// serie lenght - how many consecutive same directions must be located to
// threat it as serie.
class SerieSearcher(val periodsToDetectSerie:Int, onSerieFound:(Direction)=>Unit) {

  private var _currentSerie = 0
  private var _currentSerieDirection:Option[Direction] = None
  
  def onPeriodEnded(direction:Direction):Unit = {
    if (isStopped) return

    if (_currentSerieDirection == None) {
      if (direction == NoDirection) return
      else {
        _currentSerieDirection = Some(direction)
        proceedInSerie()
      } 
    } else {
      if (direction == _currentSerieDirection.get
          || direction == NoDirection) {
        proceedInSerie()
      } else {
        resetSerie()
        // start tracking new
        onPeriodEnded(direction)
      } 
    } 
  }

  def isStopped = _currentSerie < 0
  
  private def resetSerie() = {
    _currentSerie = 0
    _currentSerieDirection = None
  }

  private def proceedInSerie() = {
    _currentSerie += 1
    if (_currentSerie >= periodsToDetectSerie) {
      _currentSerie = -1
      onSerieFound(_currentSerieDirection.get)
    } 
  } 
  
} 
