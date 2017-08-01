package tas.utils

class HelpTarget(val name:String, val targets:List[String]) extends App {

  println("This is jar with targets for '" + name + "'")
  println("It provides following targets:")
  println("")
  println("help/Help - display this help")

  targets.foreach(target => {
                    println("")
                    println(target)
                  } )

  println("")
  println("Each target may possibly provide it's own help.")
}
