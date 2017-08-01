package tests.readers

import org.scalatest.FlatSpec

import tas.types.Period
import tas.types.Time

import tas.input.format.Reader

import tas.input.format.ticks.bincache.TicksBinaryCache

class TicksBinaryCacheTests extends StorageFormatTests {

  behavior of "TicksBinaryCache"

  readWriteTestTicks(TicksBinaryCache)

} 
