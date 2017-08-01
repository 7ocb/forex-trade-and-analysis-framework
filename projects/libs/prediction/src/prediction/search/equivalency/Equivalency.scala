package tas.prediction.search.equivalency

import scala.collection.mutable.HashMap

object Equivalency {

  sealed trait Slot
  final case class And(left:Slot, right:Slot) extends Slot
  final case class Or(left:Slot, right:Slot) extends Slot
  final case class Not(sub:Slot) extends Slot
  final case class Value(index:Int) extends Slot


  trait ConvertSlots[T] {
    def convertAnd(left:Slot, right:Slot):T
    def convertOr(left:Slot, right:Slot):T
    def convertNot(sub:Slot):T
    def convertValue(index:Int):T

    def convert(slot:Slot) = {
      slot match {
        case And(left, right) => convertAnd(left, right)
        case Or(left, right) => convertOr(left, right)
        case Not(sub) => convertNot(sub)
        case Value(index) => convertValue(index)
      }
    }
  }

  class Calculate(values:List[Boolean]) extends ConvertSlots[Boolean] {
    def convertAnd(left:Slot, right:Slot):Boolean = convert(left) && convert(right)
    def convertOr(left:Slot, right:Slot):Boolean  = convert(left) || convert(right)
    def convertNot(sub:Slot):Boolean              = ! convert(sub)


    def convertValue(index:Int):Boolean = values(index)
  }

  class CalcOperations extends ConvertSlots[Int] {
    def convertAnd(left:Slot, right:Slot):Int = 1 + convert(left) + convert(right)
    def convertOr(left:Slot, right:Slot):Int  = 1 + convert(left) + convert(right)
    def convertNot(sub:Slot):Int              = 1 + convert(sub)

    def convertValue(index:Int):Int = 0
  }


  class Print extends ConvertSlots[String] {
    def convertAnd(left:Slot, right:Slot):String = "(" + convert(left) + " && " + convert(right) + ")"
    def convertOr(left:Slot, right:Slot):String  = "(" + convert(left) + " || " + convert(right) + ")"
    def convertNot(sub:Slot):String              = "!" + convert(sub)


    def convertValue(index:Int):String = "value(" + index + ")"
  }

  def permutations(values:List[Slot]):List[Slot] = {
    if (values.isEmpty) return List[Slot]()

    if (values.size == 1) return {
      val slot = values(0)
      List(slot, Not(slot))
    }

    values
      .permutations
      .flatMap(variant => {

                 val inits = variant.inits.toList
                 val tails = variant.tails.toList.reverse

                 val zipped = inits.zip(tails)

                 zipped
                   .slice(1,
                          zipped.size - 1)
                   .flatMap(m => {
                              val init = m._1
                              val tail = m._2

                              for (constructor <- List(And, Or);
                                   left <- permutations(init);
                                   right <- permutations(tail)) yield {

                                constructor(left, right)
                              }
                            })

               } )
      .flatMap(value => permutations(List(value)))
      .toList

  }

  final case class CalcResult(in:List[Boolean], out:Boolean)
  final case class Key(results:Set[CalcResult])

  def uniquePermutations(valuesCount:Int) = {
    val values:List[Slot] = (0 to (valuesCount - 1)).map(Value(_)).toList

    def key(expression:Slot):Key = {
      // val print = new Print
      // println("expression: " + print.convert(expression))

      def results(values:List[Boolean], left:Int):Set[CalcResult] = {
        if (left > 0) {
          val newLeft = left - 1
          results(true :: values, newLeft) ++ results(false :: values, newLeft)
        } else {
          val calc = new Calculate(values)


          val result = CalcResult(values, calc.convert(expression))

          // println("calc: " + result)

          Set(result)
        }
      }

      Key(results(List[Boolean](), valuesCount))
    }

    val map = new HashMap[Key, List[(Int, Slot)]]

    val toComplexity = new CalcOperations

    permutations(values)
      .foreach(expression => {
                 val k = key(expression)
                 map.put(k, (toComplexity.convert(expression),
                             expression) :: map.getOrElse(k, List[(Int, Slot)]()))
               })

    map.values.map(similarExpressions => {
                     similarExpressions.sortWith(_._1 < _._1).head._2
                   } )
  }

}
