// -*- mode: c++ -*-
//+------------------------------------------------------------------+
//|                                                    TicksSink.mq4 |
//|                        Copyright 2013, MetaQuotes Software Corp. |
//|                                        http://www.metaquotes.net |
//+------------------------------------------------------------------+
#property copyright "Copyright 2013, MetaQuotes Software Corp."
#property link      "http://www.metaquotes.net"

//--- input parameters
extern string    hubAddress = "127.0.0.1";
extern int       hubPort    = 9101;
extern string    key;       // key should be specified, no default value

#import "metatrader-connector.dll"
void CalibrateStrings(string s);
int CreateTicksSink(string hubAddress, int hubPort, string key);
void SendTickToSink(int id, double bid, double ask);
void FreeTicksSink(int id);
#import

int idOfSink;
//+------------------------------------------------------------------+
//| expert initialization function                                   |
//+------------------------------------------------------------------+
int init()
{
   CalibrateStrings("string");
   //----
   idOfSink = CreateTicksSink(hubAddress, hubPort, key);
   //----
   return(0);
}
//+------------------------------------------------------------------+
//| expert deinitialization function                                 |
//+------------------------------------------------------------------+
int deinit()
{
   //----
   FreeTicksSink(idOfSink);
   //----
   return(0);
}
//+------------------------------------------------------------------+
//| expert start function                                            |
//+------------------------------------------------------------------+
int start()
{
   //----
   SendTickToSink(idOfSink, Bid, Ask);
   //----
   return(0);
}
//+------------------------------------------------------------------+
