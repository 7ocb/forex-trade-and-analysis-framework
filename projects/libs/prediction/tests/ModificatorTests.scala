package tests.prediction.search.modificator

import org.scalatest.FlatSpec


import tas.prediction.search.modificator.Modificator

import tas.prediction.search.value.{
  ValueFactory,
  ConstantValue
}

class ModificatorTests extends FlatSpec {

  type V = ValueFactory[String, Int]
  type M = Modificator[String, Int]
  type C = ConstantValue[String, Int]

  class Prepend(prepend:String,
                slave:V) extends V {
    def create(fa:Int) = throw new RuntimeException("not to call")
    
    val name = prepend + slave.name
  }

  object ModA extends M {
    def apply(value:V):V = new Prepend("A", value)

    def isDeniedAfter(modificator:M) = false

    override def toString = "A"
  }

  object ModB extends M {
    def apply(value:V):V = new Prepend("B", value)

    def isDeniedAfter(modificator:M) = false

    override def toString = "B"
  }

  object ModC extends M {
    def apply(value:V):V = new Prepend("C", value)

    def isDeniedAfter(modificator:M) = modificator == ModA

    override def toString = "C"
  }

  behavior of "modificator"

  it should "combine A and B to 6 combinations if no denies" in {
    val result = Modificator.combineModificators(List(ModA, ModB))

    assert(result === List(List(ModA),
                           List(ModB),
                           List(ModA, ModA),
                           List(ModB, ModA),
                           List(ModA, ModB),
                           List(ModB, ModB)))
  }

  it should "combine A and C to 5 combinations if C denied after A" in {
    val result = Modificator.combineModificators(List(ModA, ModC))

    assert(result === List(List(ModA),
                           List(ModC),
                           List(ModA, ModA),
                           List(ModC, ModA),
                           List(ModC, ModC)))
  }

  it should "apply modifications in correct order" in {
    val constant = new C("x")
    val applied = Modificator.applyModifications(constant,
                                                 List(ModA, ModB, ModC))

    assert(applied.name === "CBAx")
  }

  it should "create all combinations of modificators" in {
    val constants = List(new C("x"),
                         new C("y"))

    val applied = Modificator.applySet(List(ModA, ModB),
                                       constants)

    assert(applied.map(_.name) === List("x",
                                        "y",
                                        "Ax",
                                        "Ay",
                                        "Bx",
                                        "By",
                                        "AAx",
                                        "AAy",
                                        "ABx",
                                        "ABy",
                                        "BAx",
                                        "BAy",
                                        "BBx",
                                        "BBy"))
  }
}
