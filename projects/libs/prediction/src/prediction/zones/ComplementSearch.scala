package tas.prediction.zones

import tas.types.Fraction

object ComplementSearch {
  private final case class FootprintQualified[T](val footprint:Footprint,
                                                 val qualified:Qualified[T])

  final case class Qualified[T](val zones:ZonesSet,
                                val value:T)
}

class ComplementSearch[T](val level:Int,
                          val input:List[ComplementSearch.Qualified[T]]) {
  import ComplementSearch._

  private val etalon = Footprint.etalonFor(level)

  private var grouped = input
    .map(a => new FootprintQualified(a.zones.footprint(level), a))
    .groupBy(_.footprint.intKey) - 0

  private val maxGroup = ((1 << level) - 1)

  // println("grouped: " + grouped)

  val complements = {
    (0 to maxGroup).reverse.flatMap(testingLevel => {
                                       val group = grouped.getOrElse(testingLevel,
                                                                     List())

                                       // this group is no more needed, as
                                       // we will not check group with
                                       // itself and values from group will
                                       // be checked against all other
                                       // values, so in further checks this
                                       // group can be ignored
                                       grouped = grouped - testingLevel

                                       val testGroups = groupsToTestAgainst(testingLevel)

                                       // println("grouped: " + grouped)
                                       // println("testing group for level " + testingLevel)
                                       // println("corresponding groups: " + testGroups)

                                       for (elt <- group;
                                            testGroup <- testGroups;
                                            complement <- findComplements(elt, testGroup)) yield (elt.qualified, complement.qualified)
                                     })
  }.toList

  private def findComplements(value:FootprintQualified[T],
                              group:List[FootprintQualified[T]]) = {

    group.filter(q => ((q.qualified.zones.change + value.qualified.zones.change) > Fraction.ZERO)
                   && !(q.footprint + value.footprint).haveFails)
  }

  private def groupsToTestAgainst(intKey:Int) = 
    (1 to maxGroup)
      .filter((keyToTest:Int) => (keyToTest | intKey) == etalon)
      .map(grouped.get(_))
      .filter(_.isDefined)
      .map(_.get)

}
