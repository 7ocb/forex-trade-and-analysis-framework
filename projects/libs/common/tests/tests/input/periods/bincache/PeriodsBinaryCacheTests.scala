package tests.readers

import org.scalatest.FlatSpec

import tas.types.Period
import tas.types.Time

import tas.input.format.Reader
import tas.readers.PeriodsSequence

import tas.input.format.periods.bincache.PeriodsBinaryCache

class PeriodsBinaryCacheTests extends StorageFormatTests {

  behavior of "PeriodsBinaryCache"

  readWriteTestPeriods(PeriodsBinaryCache)

} 
