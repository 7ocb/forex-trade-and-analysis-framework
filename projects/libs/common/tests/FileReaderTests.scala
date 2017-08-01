package tests.raters.polygon.files

import java.io.ByteArrayInputStream

import tas.utils.files.lined.LinedFileReader
import org.scalatest.FlatSpec

class LinedFileReaderTests extends FlatSpec {
  behavior of "LinedFileReader"

  def stream(line:String) = new ByteArrayInputStream(line.getBytes())

  it should "Split lines by \\r\\n \\r and \\n" in {
    val contents = LinedFileReader.read(stream("asdf\r\nfdsa\raaaa\nffff"))

    assert(contents.lines === List("asdf",
                                   "fdsa",
                                   "aaaa",
                                   "ffff"))
  }

  it should "Ignore comments and empty lines in " in {
    val contents = LinedFileReader.read(stream("asdf # comment\r\n# fdsa\r\raaaa\nffff"))

    assert(contents.significantLines === List("asdf ",
                                              "aaaa",
                                              "ffff"))
  }
}
