package tas.prediction.search.modificator

import tas.prediction.search.value.ValueFactory

object Modificator {

  type ModificatorsApplication[T, FA] = List[Modificator[T, FA]]
  type M[T, FA] = Modificator[T, FA]

  def combineModificators[T, FA](modificators:List[M[T, FA]]):List[ModificatorsApplication[T, FA]] = {
    def combineWithEvery(base:List[List[M[T, FA]]], stepsLeft:Int) = {
      if (stepsLeft == 0) base
      else {
        val result = for (modificator <- modificators;
                          fromBase <- base) yield {
            if ( modificator.isDeniedAfter(fromBase.last)) fromBase
            else fromBase :+ modificator
          }

        base ++ result.toList
      }
    }

    combineWithEvery(modificators.map(List(_)),
                     modificators.size).distinct
  }

  def applyModifications[T, FA](value:ValueFactory[T, FA],
                                        combination:List[M[T, FA]]):ValueFactory[T, FA] = {
    if (combination.isEmpty) value
    else {
      val first = combination.head
      applyModifications(first(value),
                         combination.tail)
    }
  }

  def applySet[T, FA](modificators:List[M[T, FA]],
                  values:List[ValueFactory[T, FA]]):List[ValueFactory[T, FA]] = {
    val combined = for (modificationCombination <- combineModificators(modificators);
                        value <- values) yield applyModifications(value,
                                                                  modificationCombination)

    values ++ combined.toList
  }


}

trait Modificator[T, FA] {

  type V = ValueFactory[T, FA]
  type M = Modificator[T, FA]

  def apply(value:V):V

  def isDeniedAfter(modificator:M):Boolean
}
