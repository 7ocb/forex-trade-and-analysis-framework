
import tas.probing.types.{
  Type,
  AlignWithSpaces
}

import tas.output.logger.Logger

import tas.multiperiod.strategy.{
  EnterDirection,
  Direct,
  Opposite
}

object ListType {

  def apply[Slave](typeOfSlave:Type[Slave]):Type[List[Slave]] = {

    val oneValueParser =
      new Type.Parser[List[Slave]] {
        def name = "list"
        def example = "[value|value|...]"
        def parse(str:String, logger:Logger) = {

          val trimmed = str.trim

          if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val result = List(trimmed.substring(1,
                                                trimmed.length - 1)
                                .split("\\|")
                                .toList
                                .map(_.trim)
                                .flatMap(typeOfSlave.parse(_, logger))
                                .toList)

            if (result.size == 0) List[List[Slave]]()
            else result
          } else {
            List[List[Slave]]()
          } 
        }
      }

    val parsers = List(Type.listParser(oneValueParser),
                       oneValueParser)

    new Type[List[Slave]]("list",
                          parsers ) with AlignWithSpaces {
      override def format(t:List[Slave]) = "[" + t.map(typeOfSlave.format(_)).mkString(",") + "]"
    }
  }


}



object EnterDirectionType
    extends Type[EnterDirection]("enter direction",
                                 {
                                   val oneValueParser =
                                     new Type.Parser[EnterDirection] {
                                       def name = "direction"
                                       def example = "direct or opposite"
                                       def parse(str:String, logger:Logger) = {

                                         val trimmed = str.trim

                                         trimmed match {
                                           case "direct" => List(Direct)
                                           case "opposite" => List(Opposite)
                                           case s => {
                                             logger.log("can't parse \"" + s + "\" as enter direction")
                                             List()
                                           }
                                         }
                                       }
                                       
                                     }
                                   List(Type.listParser(oneValueParser),
                                        oneValueParser)
                                 }) with AlignWithSpaces
