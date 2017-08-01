package prediction.search.executor;

import tas.prediction.search.value.Value

object InZoneWhileExpressionTrue {
  type CloseFunction = () => Unit
  type OpenFunction = () => CloseFunction
}


class InZoneWhileExpressionTrue(priceZoneTracker:InZoneWhileExpressionTrue.OpenFunction,
                                expression:Value[Boolean]) {

  import InZoneWhileExpressionTrue._

  expression.onChanged += enterOrLeaveAccordingToValue

  private var _zone:Option[CloseFunction] = None

  private def enterOrLeaveAccordingToValue():Unit = {
    for (inZone <- expression.get) {
      if (inZone) {
        if (_zone == None) _zone = Some(priceZoneTracker())
      } else {
        _zone.foreach(_())
        _zone = None
      }
    }
  }
}
