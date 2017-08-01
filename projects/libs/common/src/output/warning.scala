package tas.output.warning

import tas.output.logger.ScreenLogger

object Warning {

  val _warningLogger = ScreenLogger
  val _warningPrefix = "Warning: "
  
  def apply(o:Any*) = _warningLogger.log( (_warningPrefix::o.toList):_*)
} 
