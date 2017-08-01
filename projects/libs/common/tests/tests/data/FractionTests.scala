package tests.data

import org.scalatest.FlatSpec
import tas.types.Fraction

class FractionTests extends FlatSpec {
  behavior of "Fraction"

  it should "correctly parse negative value" in {
    assert(Fraction("-10") === Fraction(-10))
    assert(Fraction("-10.2") === Fraction(-102)/10)
  }

  def formatTest(result:String) = {
    it should ("format " + result) in {
      assert(Fraction(result).toString === result)
    }
  }

  formatTest("0")
  formatTest("-10")
  formatTest("-10.000002")

  formatTest("0.0006")
  formatTest("0.00006")
  formatTest("0.000006")
  formatTest("0.0000006")

  formatTest("50781648.705168")
  formatTest("53442204.3679225")

  it should "parse 1.4828" in {
    assert(Fraction("1.4828") === Fraction(14828) / 10000)
  }

  def returnFraction(fraction:Fraction) = fraction

  it should "correctly perform implicit conversions" in {
    assert(Fraction("1000") === returnFraction("1000"))
    assert(Fraction("1000.2") === returnFraction("1000.2"))

    assert(Fraction("1000") === returnFraction(1000))
  }

  it should "perform basic arithmetics" in {

    assert((Fraction("0.1") + Fraction("0.1") + Fraction("0.1"))
             === Fraction("0.3"))

    assert((Fraction(0.1) + Fraction(0.1) + Fraction(0.1))
             === Fraction(0.3))

    assert(Fraction(0.1) / Fraction(-0.01) === Fraction(-10))

    assert((Fraction("0.2") + Fraction("0.00002"))
             === Fraction("0.20002"))

    assert((Fraction("0.2") - Fraction("0.00002"))
             === Fraction("0.19998"))

    assert((Fraction("0.2") * Fraction("2"))
             === Fraction("0.4"))

    assert((Fraction("0.1") / Fraction("2"))
             === Fraction("0.05"))

    assert((Fraction("0.1") / 2)
             === Fraction("0.05"))

    assert((Fraction("0.05") * 2)
             === Fraction("0.1"))

    assert((Fraction("0.1") + 2)
             === Fraction("2.1"))

    assert((Fraction("0.05") - 2)
             === Fraction("-1.95"))
  }

  it should "correctly floor" in {
    assert((Fraction("1.2").floorToInt === 1))
  }

  it should "perform comparsions" in {
    assert((Fraction("0.1") > Fraction("0.2")) === false)
    assert((Fraction("0.1") < Fraction("0.2")) === true)
    assert(Fraction("0.0") === Fraction("0"))
  }

  it should "round down to 10 numbers after dot" in {
    assert(Fraction("10.1234567891") === Fraction("10.12345678911231345"))
    assert(Fraction("10.1234567891") === Fraction("10.12345678919"))
  }
}
