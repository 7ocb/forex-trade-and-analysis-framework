package tests.prediction.search.value

import org.scalatest.FlatSpec

import tas.types.Fraction

import tas.prediction.search.value.{
  ValueFactory,
  ConstantValue
}

import tas.prediction.search.value.Comparsions
import tas.prediction.search.value.Comparsions.Comparator

class ComparsionsTests extends FlatSpec {

  behavior of "comparsions"

  type C = ConstantValue[Fraction, Int]

  val one = new C(Fraction("1.2"))
  val two = new C(Fraction("2.1"))
  val three = new C(Fraction("1.3"))

  it should "intercompare all" in {
    val results = Comparsions.intercompareAll(List(one, two, three))

    assert(results.map(_.name).sorted === List("(1.2 > 2.1)",
                                               "(2.1 > 1.2)",
                                               "(1.2 == 2.1)",
                                               "(1.3 > 2.1)",
                                               "(2.1 > 1.3)",
                                               "(2.1 == 1.3)",
                                               "(1.3 > 1.2)",
                                               "(1.2 > 1.3)",
                                               "(1.2 == 1.3)").sorted)

  }

}
