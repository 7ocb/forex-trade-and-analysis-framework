package tas.prediction

import tas.prediction.search.value.{
  Value
}

object InZoneWhileExpressionTrue {
  type CloseFunction = ()=>Unit
  type OpenFunction  = ()=>CloseFunction
}

class InZoneWhileExpressionTrue(openFunction:InZoneWhileExpressionTrue.OpenFunction,
                                expression:Value[Boolean]) {
  expression.onChanged += enterOrLeaveAccordingToValue

  import InZoneWhileExpressionTrue.CloseFunction

  private var _zone:Option[CloseFunction] = None

  private def enterOrLeaveAccordingToValue():Unit = {
    for (inZone <- expression.get) {
      if (inZone) {
        if (_zone == None) _zone = Some(openFunction())
      } else {
        _zone.foreach(_())
        _zone = None
      }
    }
  }
}

