package tests.readers

import org.scalatest.FlatSpec

import tas.types.Period
import tas.types.Time

import tas.input.format.Reader

import tas.input.format.ticks.metatrader.MetatraderExportedTicks

class MetatraderExportedTicksTests extends StorageFormatTests {

  behavior of "MetatraderExportedTicks"

  readWriteTestTicks(MetatraderExportedTicks)

} 
