import tas.previousdaydirection.strategy.{
  AllowedTradeTypes,
  AllowedOnlyBuys,
  AllowedOnlySells,
  AllowedBoth
}


import tas.output.logger.Logger

import tas.probing.types.{
  Type,
  AlignWithSpaces
}


object AllowedTradeTypesType extends Type[AllowedTradeTypes]("allowed trades",
                                                             {
                                                               val oneValueParser =
                                                                 new Type.Parser[AllowedTradeTypes] {
                                                                   def name = "allowed trades"
                                                                   def example = "buys, sells or both"
                                                                   def parse(str:String, logger:Logger) = {
                                                                     str.trim.toLowerCase match {
                                                                       case "buys" => List(AllowedOnlyBuys)
                                                                       case "sells" => List(AllowedOnlySells)
                                                                       case "both" => List(AllowedBoth)
                                                                       case unparsed=> {
                                                                         logger.log("cant parse: " + unparsed + " as AllowedTradeTypes")
                                                                         List()
                                                                       }
                                                                     }
                                                                   }
                                                                 }

                                                               List(Type.listParser(oneValueParser),
                                                                    oneValueParser)
                                                             } ) with AlignWithSpaces
