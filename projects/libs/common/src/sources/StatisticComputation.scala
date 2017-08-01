package tas.sources


import scala.collection.mutable.ListBuffer
import tas.events.Event
import tas.timers.Timer

class StatisticComputation[SourceType, ResultType](timer:Timer,
                                                   length:Int,
                                                   calculation:List[SourceType]=>ResultType) {
  private var _buffer = new ListBuffer[SourceType]
  private var _result = Option.empty[ResultType]
  private val _updatedEvent = Event.newAsync[ResultType](timer)

  def bindTo(eventToBindTo:Event[SourceType]) = eventToBindTo += onInput _

  def onInput(input:SourceType) {
    _buffer += input

    val excess = _buffer.size - length

    if (excess > 0) {
      _buffer = _buffer.drop(excess)
    }

    if (_buffer.size == length) {
      _result = Option(calculation(_buffer.result))

      _updatedEvent << _result.get
    } 
  } 

  def updatedEvent:Event[ResultType] = _updatedEvent
  def result = _result
} 
