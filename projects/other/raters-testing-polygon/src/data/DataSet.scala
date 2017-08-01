package tas.raters.polygon.data

import tas.utils.files.lined.FileContents
import tas.types.Fraction

object DataSet {

  def fromFile(fileContents:FileContents) = 
    new DataSet(fileContents.significantLines.map(Fraction(_)))

}

class DataSet(val values:List[Fraction]) 
