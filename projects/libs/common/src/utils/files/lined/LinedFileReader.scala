package tas.utils.files.lined

import java.io.InputStream

import tas.utils.IO

object LinedFileReader {
  def read(istream:InputStream):FileContents = {
    val data = IO.readAll(istream)

    val lines = for (splitByRN <- data.split("\r\n").toList;
                     splitByRAndRN <- splitByRN.split("\r").toList;
                     splitByAll <- splitByRAndRN.split("\n").toList)
                yield splitByAll

    new FileContents(lines)
  }
}

