package tas.prediction.search.value

import tas.events.{
  Subscription,
  SyncCallSubscription
}

import tas.sources.periods.PeriodSource

import tas.types.{
  Fraction,
  Period
}

import scala.collection.mutable.ListBuffer

import java.io.Serializable

trait ValueFactory[ValueType, FactoryArg] extends Serializable {
  def create(s:FactoryArg):Value[ValueType]

  val name:String
}

trait Value[ValueType] {

  def get:Option[ValueType]

  def onChanged:Subscription[()=>Unit]
}

class SetableValue[ValueType] extends Value[ValueType] {

  final def get:Option[ValueType] = stored

  private val _onChanged = new SyncCallSubscription()
  
  final val onChanged:Subscription[()=>Unit] = _onChanged

  final def set(v:Option[ValueType]) = {
    if (v != stored) {
      stored = v
      _onChanged()
    }
  }


  private var stored:Option[ValueType] = None
}

object ConstantValue {
  val onChanged = new Subscription[()=>Unit] {
      def +=(subscriber:()=>Unit):Unit = {}
      def -=(subscriber:()=>Unit):Unit = {}
    }
}

class ConstantValue[T, FactoryArg](value:T)
    extends Value[T] with ValueFactory[T, FactoryArg] {
  val get = Some(value)

  def create(fa:FactoryArg) = this

  def onChanged = ConstantValue.onChanged

  val name = value.toString
}

trait SetableValueFactory[ResultType, FactoryArg] extends ValueFactory[ResultType, FactoryArg] {
  final def create(fa:FactoryArg) = {
    val value = new SetableValue[ResultType]
    init(value, fa)
    value
  }

  protected def init(value:SetableValue[ResultType], fa:FactoryArg):Unit

}

abstract class BinaryExpression[ResultType,
                                FactoryArg,
                                LeftArgType,
                                RightArgType](leftArgFactory:ValueFactory[LeftArgType, FactoryArg],
                                              rightArgFactory:ValueFactory[RightArgType, FactoryArg])
    extends SetableValueFactory[ResultType, FactoryArg] {

  protected final def init(value:SetableValue[ResultType], fa:FactoryArg) = {

    val leftArg = leftArgFactory.create(fa)
    val rightArg = rightArgFactory.create(fa)

    def onChanged() = value.set(for (left <- leftArg.get;
                                     right <- rightArg.get)
                                yield calculate(left, right))

    leftArg.onChanged += onChanged
    rightArg.onChanged += onChanged

  }

  def calculate(left:LeftArgType, right:RightArgType):ResultType
}

abstract class UnaryExpression[ResultType,
                               FactoryArg,
                               ArgType](argFactory:ValueFactory[ArgType, FactoryArg])
    extends SetableValueFactory[ResultType, FactoryArg] {

  def init(value:SetableValue[ResultType], fa:FactoryArg) = {
    val arg = argFactory.create(fa)

    arg.onChanged += (() => value.set(for (v <- arg.get) yield calculate(v)))
  }

  def calculate(argValue:ArgType):ResultType
}

class PeriodField[T, FactoryArg](periodsSource:(FactoryArg=>PeriodSource),
                                 val name:String,
                                 getter:Period=>T) extends ValueFactory[T, FactoryArg] {

  def create(fa:FactoryArg) = {

    val value = new SetableValue[T]

    periodsSource(fa).periodCompleted += (period => value.set(Some(getter(period))))

    value
  }

}


class Multiply[FactoryArg](leftArg:ValueFactory[Fraction, FactoryArg],
                           rightArg:ValueFactory[Fraction, FactoryArg])
    extends BinaryExpression[Fraction,
                             FactoryArg,
                             Fraction,
                             Fraction](leftArg, rightArg) {

  def calculate(left:Fraction, right:Fraction) = left * right

  val name = "(" + leftArg.name + ") * (" + rightArg.name + ")"
}

class BoolAnd[FactoryArg](leftArg:ValueFactory[Boolean, FactoryArg],
                          rightArg:ValueFactory[Boolean, FactoryArg])
    extends BinaryExpression[Boolean,
                             FactoryArg,
                             Boolean,
                             Boolean](leftArg, rightArg) {

  def calculate(left:Boolean, right:Boolean) = left && right

  val name = "(" + leftArg.name + ") && (" + rightArg.name + ")"
}

class BoolOr[FactoryArg](leftArg:ValueFactory[Boolean, FactoryArg],
                         rightArg:ValueFactory[Boolean, FactoryArg])
    extends BinaryExpression[Boolean,
                             FactoryArg,
                             Boolean,
                             Boolean](leftArg, rightArg) {

  def calculate(left:Boolean, right:Boolean) = left || right

  val name = "(" + leftArg.name + ") || (" + rightArg.name + ")"
}


class BoolNot[FactoryArg](arg:ValueFactory[Boolean, FactoryArg]) extends UnaryExpression[Boolean, FactoryArg, Boolean](arg) {
  def calculate(v:Boolean):Boolean = !v

  val name = "!(" + arg.name + ")"
}

class NthValueFromPast[ResultType, FactoryArg](nth:Int,
                                               subvalue:ValueFactory[ResultType, FactoryArg])
    extends SetableValueFactory[ResultType, FactoryArg] {

  if (nth < 2) throw new IllegalArgumentException("nth expected to be > 1")
  if (subvalue == null) throw new IllegalArgumentException("subvalue can not be null")

  def init(value:SetableValue[ResultType], fa:FactoryArg) = {
    var keptValues = new ListBuffer[ResultType]()

    def store(v:Option[ResultType]) = {
      for (nextValue <- v) keptValues += nextValue

      if (keptValues.size > nth) keptValues = keptValues.drop(keptValues.size - nth)
    }

    val s = subvalue.create(fa)

    def onArgChanged() = {
      store(s.get)

      value.set(if (keptValues.size == nth) Some(keptValues.head)
                else None)
    }

    s.onChanged += onArgChanged

    store(s.get)
  }

  final val name = "previous(" + nth + ", " + subvalue.name + ")"
}

class NormalizedByMax[FactoryArg](baseValue:ValueFactory[Fraction, FactoryArg]) extends SetableValueFactory[Fraction, FactoryArg] {

  if (baseValue == null) throw new IllegalArgumentException("baseValue can not be null")

  def init(value:SetableValue[Fraction], fa:FactoryArg) = {
    var lastKnownMax:Option[Fraction] = value.get

    val bv = baseValue.create(fa)

    def update() = {
      val current = bv.get

      if (current.isDefined) {
        for (c <- current) {
          lastKnownMax = Some(if (lastKnownMax == None) c
                              else lastKnownMax.get.max(c))

          value.set(lastKnownMax.filter(_ > 0).map(c / _))
        }
      } else {
        value.set(None)
      }
    }

    bv.onChanged += update
  }

  final val name = "normToMax(" + baseValue.name + ")"
}

object ExtremumValue {
  sealed trait ExtremumType extends Serializable {
    def extremumValue[T](left:T, right:T)(implicit numeric:Numeric[T]):T
    val name:String
  }

  object Min extends ExtremumType {
    def extremumValue[T](left:T, right:T)(implicit numeric:Numeric[T]):T = numeric.min(left, right)
    val name:String = "min"
  }

  object Max extends ExtremumType {
    def extremumValue[T](left:T, right:T)(implicit numeric:Numeric[T]):T = numeric.max(left, right)
    val name:String = "max"
  }
}

class ExtremumValue[T, FactoryArg](baseValue:ValueFactory[T, FactoryArg],
                                   extremumType:ExtremumValue.ExtremumType)(implicit numeric:Numeric[T])
    extends SetableValueFactory[T, FactoryArg] {

  if (baseValue == null) throw new IllegalArgumentException("baseValue can not be null")

  def init(value:SetableValue[T], fa:FactoryArg) = {
    val bv = baseValue.create(fa)

    bv.onChanged += {() =>
      val prevValue = value.get
      val currentValue = bv.get

      if (currentValue.isDefined) {
        for (c <- currentValue) {

          value.set(Some(if (prevValue == None) c
                         else extremumType.extremumValue(prevValue.get,
                                                         c)))

          
        }
      } else {
        value.set(None)
      }
    }

  }

  final val name = extremumType.name + "(" + baseValue.name + ")"
}

class MinValue[T, FA](value:ValueFactory[T, FA])(implicit numeric:Numeric[T]) extends ExtremumValue[T, FA](value, ExtremumValue.Min)(numeric)
class MaxValue[T, FA](value:ValueFactory[T, FA])(implicit numeric:Numeric[T]) extends ExtremumValue[T, FA](value, ExtremumValue.Max)(numeric)

abstract class SlidingValue[T, FactoryArg](count:Int,
                                           baseValue:ValueFactory[T, FactoryArg])
    extends SetableValueFactory[T, FactoryArg] {

  if (baseValue == null) throw new IllegalArgumentException("slave value can not be null")

  def init(value:SetableValue[T], fa:FactoryArg) = {
    val bv = baseValue.create(fa)

    val sliding = new tas.utils.SlidingValue[Option[T]](count)

    def update() = {
      sliding += value.get

      value.set {
        if (!sliding.isFilled) None
        else {
          val asList = sliding.toList

          if (asList.map(_.isDefined).contains(false)) None
          else Some(calculate(asList.map(_.get)))
        }
      }
    }

  }

  protected def calculate(list:List[T]):T
}

class SlidingExtremum[T, FactoryArg](count:Int, value:ValueFactory[T, FactoryArg], extremum:ExtremumValue.ExtremumType)(implicit numeric:Numeric[T])
    extends SlidingValue[T, FactoryArg](count, value) {

  protected def calculate(list:List[T]):T = list.reduce(extremum.extremumValue(_, _)(numeric))

  final val name = "sliding(" + count + ", " + extremum.name + ", " + value.name + ")"
}

class SlidingMax[T, FactoryArg](count:Int, value:ValueFactory[T, FactoryArg])
                (implicit numeric:Numeric[T])
    extends SlidingExtremum[T, FactoryArg](count, value, ExtremumValue.Max)(numeric)

class SlidingMin[T, FactoryArg](count:Int, value:ValueFactory[T, FactoryArg])
                (implicit numeric:Numeric[T])
    extends SlidingExtremum[T, FactoryArg](count, value, ExtremumValue.Min)(numeric)


