package tests.prediction.search.value

import org.scalatest.FlatSpec

import tas.prediction.search.modificator.Modificator

import org.scalamock.scalatest.MockFactory

import tas.types.Fraction

import java.io.{
  ByteArrayOutputStream,
  ByteArrayInputStream,
  ObjectOutputStream,
  ObjectInputStream
}

import tas.prediction.search.value.{
  Value,
  ValueFactory,
  SetableValue,
  ConstantValue,
  BinaryExpression,
  Multiply,
  NthValueFromPast,
  NormalizedByMax,
  MinValue,
  MaxValue,
  BoolAnd,
  BoolOr,
  BoolNot,
  SetableValueFactory
}
object ValuesTests {
  class Link[T](v: =>Value[T]) extends ValueFactory[T, Int] {
    def create(fa:Int) = v

    val name = "link"
  }
}

class ValuesTests extends FlatSpec with MockFactory {

  import ValuesTests.Link

  def testBinBoolValue(name:String,
                       create:(ValueFactory[Boolean, Int], ValueFactory[Boolean, Int])=>ValueFactory[Boolean, Int],
                       op:(Boolean, Boolean)=>Boolean) = {

    name should "correctly calculate" in {
      val left = new SetableValue[Boolean]
      val right = new SetableValue[Boolean]

      val bool = create(new Link(left),
                        new Link(right)).create(1)

      assert(bool.get === None)

      left.set(Some(true))
      right.set(Some(false))

      assert(left.get === Some(true))
      assert(right.get === Some(false))

      assert(bool.get === Some(op(true, false)))

      left.set(Some(false))
      right.set(Some(true))

      assert(bool.get === Some(op(false, true)))

      left.set(Some(true))
      right.set(Some(true))

      assert(bool.get === Some(op(true, true)))

    }

    testSerializability(name,
                        create(dummy[Boolean],
                               dummy[Boolean]))
  }

  testBinBoolValue("bool and", new BoolAnd(_, _), _ && _)
  testBinBoolValue("bool or", new BoolOr(_, _), _ || _)

  type C[T] = ConstantValue[T, Int]

  "constant" should "return value set to it" in {
    assert(new C(1).get === Some(1))
    assert(new C("s").get === Some("s"))
    assert(new C(None).get === Some(None))

    assert(new C(1).create(1).get === Some(1))
    assert(new C("s").create(1).get === Some("s"))
    assert(new C(None).create(1).get === Some(None))
  }

  "multiply" should "multiply it's values" in {
    val left = new SetableValue[Fraction]
    val right = new SetableValue[Fraction]

    val multiply = new Multiply(new Link(left), new Link(right)).create(1)

    assert(multiply.get === None)

    left.set(Some("10"))

    assert(multiply.get === None)

    right.set(Some("20"))

    assert(multiply.get === Some(Fraction("200")))
  }

  behavior of "binary expression"

  it should "recalculate if any of it's values changed" in {

    val left = new SetableValue[String]
    val right = new SetableValue[String]

    val testBinaryExpression = new BinaryExpression[String, Int, String, String](new Link(left),
                                                                                 new Link(right)) {
        def calculate(l:String, r:String) = (r + "|" + l)

        val name = "test"
      }.create(1)

    val expectedCall = mock[()=>Unit]

    (expectedCall.apply _).expects()

    testBinaryExpression.onChanged += expectedCall

    left.set(Some("1"))

    assert(testBinaryExpression.get === None)

    right.set(Some("2"))

    assert(testBinaryExpression.get === Some("2|1"))

  }

  behavior of "nth in past value"

  it should "throw IllegalArgumentException if nth < 2" in {
    val subvalue = new SetableValue[String]

    intercept[IllegalArgumentException] {
      new NthValueFromPast(1, new Link(subvalue))
    }

    intercept[IllegalArgumentException] {
      new NthValueFromPast(0, new Link(subvalue))
    }

    intercept[IllegalArgumentException] {
      new NthValueFromPast(-1, new Link(subvalue))
    }

    new NthValueFromPast(2, new Link(subvalue))
  }

  it should "throw IllegalArgumentException if subvalue == null" in {
    intercept[IllegalArgumentException] {
      new NthValueFromPast(2, null)
    }

  }

  it should "return value from past from empty value" in {
    val subvalue = new SetableValue[String]

    val fromPast = new NthValueFromPast(2, new Link(subvalue)).create(1)

    assert(fromPast.get === None)

    subvalue.set(Some("first"))

    assert(fromPast.get === None)

    subvalue.set(Some("second"))

    assert(fromPast.get === Some("first"))

    subvalue.set(Some("third"))

    assert(fromPast.get === Some("second"))

  }

  behavior of "normalized by max"
  it should "throw illegal argument exception if value is null" in {
    intercept[IllegalArgumentException] {
      new NormalizedByMax(null)
    }
  }

  it should "return value normalized by max" in {
    val subvalue = new SetableValue[Fraction]

    val normalized = new NormalizedByMax(new Link(subvalue)).create(1)

    assert(normalized.get === None)

    subvalue.set(Some(Fraction(2)))

    assert(normalized.get === Some(Fraction(1)))

    subvalue.set(Some(Fraction(4)))

    assert(normalized.get === Some(Fraction(1)))

    subvalue.set(Some(Fraction(2)))

    assert(normalized.get === Some(Fraction("0.5")))

    subvalue.set(Some(Fraction(1)))

    assert(normalized.get === Some(Fraction("0.25")))

  }

  type Min[T] = MinValue[T, Int]
  type Max[T] = MaxValue[T, Int]

  behavior of "extremum value"
  it should "throw IllegalArgumentException if value is null" in {
    intercept[IllegalArgumentException] {
      new Min[Int](null)
    }

    intercept[IllegalArgumentException] {
      new Max[Int](null)
    }
  }

  it should "caclualate min value for fraction" in {
    val subvalue = new SetableValue[Fraction]

    val min = new Min(new Link(subvalue)).create(1)

    assert(min.get === None)

    subvalue.set(Some(Fraction("1.2")))

    assert(min.get === Some(Fraction("1.2")))

    subvalue.set(Some(Fraction("2")))

    assert(min.get === Some(Fraction("1.2")))

    subvalue.set(Some(Fraction("1")))

    assert(min.get === Some(Fraction("1")))

  }

  it should "caclualate min value for int" in {
    val subvalue = new SetableValue[Int]

    val min = new Min(new Link(subvalue)).create(1)

    assert(min.get === None)

    subvalue.set(Some(12))

    assert(min.get === Some(12))

    subvalue.set(Some(20))

    assert(min.get === Some(12))

    subvalue.set(Some(1))

    assert(min.get === Some(1))

  }


  it should "caclualate max value for fraction" in {
    val subvalue = new SetableValue[Fraction]

    val max = new Max(new Link(subvalue)).create(1)

    assert(max.get === None)

    subvalue.set(Some(Fraction("1.2")))

    assert(max.get === Some(Fraction("1.2")))

    subvalue.set(Some(Fraction("2")))

    assert(max.get === Some(Fraction("2")))

    subvalue.set(Some(Fraction("1")))

    assert(max.get === Some(Fraction("2")))

  }

  it should "caclualate max value for int" in {
    val subvalue = new SetableValue[Int]

    val max = new Max(new Link(subvalue)).create(1)

    assert(max.get === None)

    subvalue.set(Some(12))

    assert(max.get === Some(12))

    subvalue.set(Some(20))

    assert(max.get === Some(20))

    subvalue.set(Some(1))

    assert(max.get === Some(20))

  }

  def testSerializability(t:String, o:Any) = {
    t should "be serializable" in {
      val output = new ByteArrayOutputStream()

      new ObjectOutputStream(output).writeObject(o)

      output.close()

      val input = new ByteArrayInputStream(output.toByteArray())

      val res = new ObjectInputStream(input).readObject
    }
  }

  def dummy[T] = new Link(new SetableValue[T])

  testSerializability("BoolAnd", new BoolAnd(dummy[Boolean], dummy[Boolean]))
  testSerializability("BoolOr", new BoolOr(dummy[Boolean], dummy[Boolean]))
  testSerializability("BoolNot", new BoolNot(dummy[Boolean]))

}
