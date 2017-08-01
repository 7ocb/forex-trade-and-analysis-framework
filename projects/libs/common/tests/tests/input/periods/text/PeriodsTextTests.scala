package tests.readers

import org.scalatest.FlatSpec

import tas.types.Period
import tas.types.Time

import tas.input.format.Reader
import tas.readers.PeriodsSequence

import tas.input.format.periods.text.PeriodsText

class PeriodsTextTests extends StorageFormatTests {

  behavior of "PeriodsText"

  readWriteTestPeriods(PeriodsText)

} 
