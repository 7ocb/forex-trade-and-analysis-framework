package tas.prediction.search.value

import tas.types.{
  Fraction
}

object Comparsions {

  type Result[FactoryArg] = ValueFactory[Boolean, FactoryArg]

  trait Comparator[T] {
    def apply[FactoryArg](left:ValueFactory[T, FactoryArg],
                          right:ValueFactory[T, FactoryArg]):List[Result[FactoryArg]]
  }

  implicit object FractionComparator extends Comparator[Fraction] {
    def apply[FactoryArg](left:ValueFactory[Fraction, FactoryArg],
                          right:ValueFactory[Fraction, FactoryArg]):List[Result[FactoryArg]] =
      List(new LeftGreaterThanRight(left, right),
           new LeftGreaterThanRight(right, left),
           new LeftEqualRight(left, right))
  }

  def intercompareAll[T, FactoryArg](values:List[ValueFactory[T, FactoryArg]])
                     (implicit comparator:Comparator[T]):List[Result[FactoryArg]] =
    values.combinations(2).flatMap(combination => comparator(combination(0),
                                                             combination(1))).toList

}

class LeftEqualRight[FactoryArg](leftArg:ValueFactory[Fraction, FactoryArg],
                                 rightArg:ValueFactory[Fraction, FactoryArg])
    extends BinaryExpression[Boolean,
                             FactoryArg,
                             Fraction,
                             Fraction](leftArg, rightArg) {
  def calculate(left:Fraction, right:Fraction) = left == right

  val name = "(" + leftArg.name + " == " + rightArg.name + ")"
}

class LeftGreaterThanRight[FactoryArg](leftArg:ValueFactory[Fraction, FactoryArg],
                                       rightArg:ValueFactory[Fraction, FactoryArg])
    extends BinaryExpression[Boolean,
                             FactoryArg,
                             Fraction,
                             Fraction](leftArg, rightArg) {
  def calculate(left:Fraction, right:Fraction) = left > right

  val name = "(" + leftArg.name + " > " + rightArg.name + ")"
}
