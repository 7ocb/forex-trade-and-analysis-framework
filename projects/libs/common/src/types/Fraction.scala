package tas.types

import java.io.{
  ObjectInputStream,
  ObjectOutputStream,
  DataInputStream,
  DataOutputStream,

  Serializable
}

import scala.math.Numeric

import tas.output.format.Formatting
import tas.utils.format.StringComplementor


object Fraction {

  import scala.language.implicitConversions

  val ZERO = new Fraction(0)

  def apply(l:Long) = new Fraction((ValueMutiplier * l).toLong)

  def apply(d:Double) = {
    new Fraction((BigDecimal(d) * ValueMutiplier).toLong)
  }

  def apply(bi:BigInt) = new Fraction(bi.longValue)

  def apply(str:String) = {

    val trimmed = str.trim
    val isNegative = trimmed(0) == '-'

    val noSign = if (isNegative) trimmed.substring(1)
                 else trimmed



    val parts = noSign.split("\\.")

    val integralPart = BigInt(parts(0)) * ValueMutiplierBigInt

    val decimalPart = if (parts.size > 1) {
        val afterPoint = parts(1)

        if (afterPoint.size > ScaleOfValue) BigInt(afterPoint.substring(0, ScaleOfValue))
        else BigInt(afterPoint) * BigInt(BigInt(10).pow(ScaleOfValue - afterPoint.size)
                                           .toLong)
      } else BigInt(0)

    def withSign(int:BigInt) = if (isNegative) -int
                               else int

    new Fraction(withSign(integralPart + decimalPart).longValue)
  }

  def max(l:Fraction, r:Fraction) = new Fraction(l.value.max(r.value))
  def min(l:Fraction, r:Fraction) = new Fraction(l.value.min(r.value))


  implicit object FractionNumeric extends Numeric[Fraction] {
    def fromInt(x: Int): tas.types.Fraction = new Fraction(x)
    def minus(x: tas.types.Fraction,y: tas.types.Fraction) = x - y
    def negate(x: tas.types.Fraction): tas.types.Fraction = - x
    def plus(x: tas.types.Fraction,y: tas.types.Fraction) = x + y
    def times(x: tas.types.Fraction,y: tas.types.Fraction) = x * y 

    def toDouble(x: tas.types.Fraction) = throw new RuntimeException("should not be used!")
    def toFloat(x: tas.types.Fraction) = throw new RuntimeException("should not be used!")
    def toInt(x: tas.types.Fraction) = throw new RuntimeException("should not be used!")
    def toLong(x: tas.types.Fraction) = throw new RuntimeException("should not be used!")
      
    // Members declared in scala.math.Ordering
    def compare(x: tas.types.Fraction,y: tas.types.Fraction): Int = {
      if (x > y) 1
      else if (y > x) -1
      else 0
    }
  }

  implicit def int2Fraction(value:Int) = new Fraction((ValueMutiplierBigInt * value).longValue)
  implicit def string2Fraction(value:String) = Fraction(value)

  private val ScaleOfValue = 10
  private val ValueMutiplierBigInt = BigInt(10).pow(ScaleOfValue)
  private val ValueMutiplier = ValueMutiplierBigInt.toLong

  object IO {
    class FractionWriter[StreamType] private [Fraction] (stream:StreamType,
                                                         writeLong:(StreamType,Long)=>Unit) {
      def writeFraction(inValue:Fraction) = {
        writeLong(stream, inValue.value)
      }
    }

    class FractionReader[StreamType] private [Fraction] (stream:StreamType, readLong:StreamType=>Long) {
      def readFraction():Fraction = new Fraction(readLong(stream))
    }


    implicit def objectOutputStream2FractionWriter(stream:ObjectOutputStream) =
      new FractionWriter[ObjectOutputStream](stream, _.writeLong(_))

    implicit def dataOutputStream2FractionWriter(stream:DataOutputStream) =
      new FractionWriter[DataOutputStream](stream, _.writeLong(_))

    implicit def objectInputStream2FractionReader(stream:ObjectInputStream) =
      new FractionReader[ObjectInputStream](stream, _.readLong)

    implicit def dataInputStream2FractionReader(stream:DataInputStream) =
      new FractionReader[DataInputStream](stream, _.readLong)

    
  }
}

final class Fraction private (private val value:Long) extends Serializable {

  override def toString:String = {

    if (value == 0) {
      "0"
    } else if (value % Fraction.ValueMutiplierBigInt == 0){
      (value / Fraction.ValueMutiplierBigInt).toString
    } else {

      val isNegative = value < 0
      val absolute = value.abs

      val absoluteAsString = new StringComplementor(Fraction.ScaleOfValue + 1,
                                                    "0")(absolute.toString)

      val length = absoluteAsString.length

      val beforeZero = length - Fraction.ScaleOfValue

      val unstripped = absoluteAsString.substring(0, beforeZero) + "." + absoluteAsString.substring(beforeZero, length)

      val lastNonZero = unstripped.lastIndexWhere(_ != '0')

      val sign = if (isNegative) "-"
                 else ""

      sign + unstripped.substring(0, lastNonZero + 1)
    }
  }

  private def this() = this(0)

  private lazy val asBigInt = BigInt(value)

  override def equals(that:Any) = {
    if (that.isInstanceOf[Fraction]) {
      that.asInstanceOf[Fraction].value.compare(value) == 0
    } else {
      throw new RuntimeException("Comparing Fraction with non-Fraction")
    }
  }

  def unary_- = new Fraction(-value)
  def unary_+ = new Fraction(value)

  def /(divider:Long) = new Fraction(value / divider)
  def /(divider:Fraction) = Fraction((asBigInt * Fraction.ValueMutiplierBigInt) / divider.value)

  def *(multiplier:Long) = new Fraction(value * multiplier)
  def *(multiplier:Fraction) = Fraction((asBigInt * multiplier.value) / Fraction.ValueMutiplierBigInt)

  def -(substracted:Fraction):Fraction = new Fraction(value - substracted.value)
  def +(added:Fraction):Fraction = new Fraction(value + added.value)

  def <(that:Fraction) = value < that.value
  def >(that:Fraction) = value > that.value

  def <=(that:Fraction) = value <= that.value
  def >=(that:Fraction) = value >= that.value

  def max(that:Fraction) = new Fraction(that.value.max(value))
  def min(that:Fraction) = new Fraction(that.value.min(value))

  def abs = new Fraction(value.abs)

  
  def range(end:Fraction, step:Fraction):List[Fraction] = {
    (value to end.value by step.value).toList.map(new Fraction(_))
  }

  def floorToInt = (value / Fraction.ValueMutiplierBigInt).intValue
}
