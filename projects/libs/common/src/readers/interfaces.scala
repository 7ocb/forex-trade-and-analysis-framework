package tas.readers

import scala.collection.mutable.HashMap

import tas.types.Time
import tas.types.Interval

import java.io.File
import java.io.InputStream
import tas.utils.IO
import tas.types.PeriodBid
import scala.collection.Traversable
import scala.collection.mutable.ListBuffer

import tas.input.Sequence
import tas.input.format.periods.text.PeriodsText
import tas.input.format.periods.bincache.PeriodsBinaryCache


class ShiftedPeriodsSequence(sequence:Sequence[PeriodBid],
                             shiftPeriodTime:(Time=>Time)) extends Sequence[PeriodBid] {

  def this(sequence:Sequence[PeriodBid],
           shiftInterval:Interval) = this(sequence,
                                          time => time + shiftInterval)
  
  def haveNext = sequence.haveNext
  def next = {
    val n = sequence.next
    new PeriodBid(n.bidOpen,
                  n.bidClose,
                  n.bidMin,
                  n.bidMax,
                  shiftPeriodTime(n.time))
  }  
} 

object PeriodsSequence {
  def fromString(str:String):Sequence[PeriodBid] = Sequence.fromString(str,
                                                                       PeriodsText)

  def fromFile(fileName:String):Sequence[PeriodBid] =
    Sequence.fromFile(new File(fileName),
                      PeriodsText,
                      PeriodsBinaryCache)

  def fromStream(stream:InputStream):Sequence[PeriodBid] = Sequence.fromStream(stream,
                                                                               PeriodsText)
}

 
