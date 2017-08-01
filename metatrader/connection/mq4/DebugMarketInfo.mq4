 // -*- mode: c++ -*-
//+------------------------------------------------------------------+
//|                                                            D.mq4 |
//|                        Copyright 2013, MetaQuotes Software Corp. |
//|                                              http://www.mql5.com |
//+------------------------------------------------------------------+
#property copyright "Copyright 2013, MetaQuotes Software Corp."
#property link      "http://www.metaquotes.net"
#property version   "1.00"
#property strict


// without this comment it will not compile
void OnStart()
{

   string symbol = Symbol();
   double freezeLevel = MarketInfo(symbol, MODE_FREEZELEVEL);
   double stopLevel = MarketInfo(Symbol(), MODE_STOPLEVEL);

   Print("freeze level is: ", freezeLevel);
   Print("stop level is: ", stopLevel);
}

