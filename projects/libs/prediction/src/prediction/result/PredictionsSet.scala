package tas.prediction.result

import tas.types.Trade

case class PredictionsSet(operation:Trade,
                          results:Array[PredictionResult])
