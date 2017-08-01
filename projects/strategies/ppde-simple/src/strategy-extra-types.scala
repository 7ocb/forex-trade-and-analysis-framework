import tas.ppdesimple.strategy.{
  AllowedTrades,
  AllowedOnlyBuys,
  AllowedOnlySells,
  AllowedBoth,
  IfNoDirection,
  SameIfNoDirection,
  OppositeIfNoDirection
}


import tas.output.logger.Logger

import tas.probing.types.{
  Type,
  AlignWithSpaces
}


object AllowedTradesType
    extends Type[AllowedTrades]("allowed trades",
                                {
                                  val oneValueParser =
                                    new Type.Parser[AllowedTrades] {
                                      def name = "allowed trades"
                                      def example = "buys, sells or both"
                                      def parse(str:String, logger:Logger) = {
                                        str.trim.toLowerCase match {
                                          case "buys" => List(AllowedOnlyBuys)
                                          case "sells" => List(AllowedOnlySells)
                                          case "both" => List(AllowedBoth)
                                          case unparsed=> {
                                            logger.log("cant parse: " + unparsed + " as AllowedTrades")
                                            List()
                                          }
                                        }
                                      }
                                    }

                                  List(Type.listParser(oneValueParser),
                                       oneValueParser)
                                } ) with AlignWithSpaces

object IfNoDirectionType
    extends Type[IfNoDirection]("if same direction",
                                {
                                  val oneValueParser = new Type.Parser[IfNoDirection] {
                                      def name = "allowed trades"
                                      def example = ""
                                      def parse(str:String, logger:Logger) = {
                                        str.trim.toLowerCase match {
                                          case "opposite" => List(OppositeIfNoDirection)
                                          case "same" => List(SameIfNoDirection)
                                          case unparsed=> {
                                            logger.log("cant parse: " + unparsed + " as IfNoDirection")
                                            List()
                                          }
                                        }
                                      }
                                    }

                                  List(Type.listParser(oneValueParser),
                                       oneValueParser)
                                } ) with AlignWithSpaces
