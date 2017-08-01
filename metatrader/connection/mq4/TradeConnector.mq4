// -*- mode: c++ -*-
//+------------------------------------------------------------------+
//                                                TradeConnector.mq4 |
//|                        Copyright 2013, MetaQuotes Software Corp. |
//|                                        http://www.metaquotes.net |
//+------------------------------------------------------------------+
#property copyright "Copyright 2013, MetaQuotes Software Corp."
#property link      "http://www.metaquotes.net"

#include <stdlib.mqh>

// =============================================================================
#define ERR_NO_CHANGE           1
#define ERR_INVALID_STOPS	130
// =============================================================================


//--- input parameters
extern string    hubAddress = "127.0.0.1";
extern int       hubPort    = 9101;
extern string    key;       // key should be specified, no default value

#import "metatrader-connector.dll"
void CalibrateStrings(string s);

int CreateTradeConnector(string &hubAddress, int hubPort, string key, double balance, double equity);
void UpdateBalance(int connectorId, double balance);
void UpdateEquity(int connectorId, double balance);
void FreeTradeConnector(int connectorId);

void StartNextTradesIteration(int connectorId);
bool ShiftToNextTrade(int connectorId);

void LogTradeConnectorMessage(int connectorId, string message);
void TradeMessage(int connectorId, string message);

void FreeTrade(int connectorId);

int TradeGetMtId(int connectorId);
void TradeSetMtId(int connectorId, int id);

bool TradeGetIsWantsClose(int connectorId);

int TradeGetType(int connectorId);
double TradeGetValue(int connectorId);

double TradeGetRequestedDelay(int connectorId);
double TradeGetRequestedStop(int connectorId);
double TradeGetRequestedTp(int connectorId);

double TradeGetBestSetDelay(int connectorId);
double TradeGetBestSetStop(int connectorId);
double TradeGetBestSetTp(int connectorId);

void TradeSetBestSetDelay(int connectorId, double bestSetDelay);
void TradeSetBestSetStop(int connectorId, double bestSetStop);
void TradeSetBestSetTp(int connectorId, double bestSetTp);

bool TradeGetIsOpened(int connectorId);
void TradeSetIsOpened(int connectorId, bool isOpened);

void TradeNotifyOpened(int connectorId);
void TradeNotifyClosed(int connectorId);

#import

#define MAX_RETRY_COUNT 5

#define NoBoundary 0

#define InvalidId -1

#define TradeBuy  1
#define TradeSell 2

#define TpMaxMultiplier 4

double StopsShiftInitial() {
   return (0);
}

double StopsShiftIncrement() {
   return (2 * Point);
}

double StopsShiftMax() {
   return (6 * Point);
}

double Slippage() {
   return (4 * Point);
}

int idOfConnector;

void Warning(string message) {
   TradeMessage(idOfConnector, StringConcatenate("Warning: ", message));
}

void Log(string message) {
   LogTradeConnectorMessage(idOfConnector, message);
}


bool cmp(double number1, double number2) {
   if (NormalizeDouble(number1 - number2, Digits) == 0) return(true);
   else return(false);
}

//+------------------------------------------------------------------+
//| expert initialization function                                   |
//+------------------------------------------------------------------+
int init()
{
   CalibrateStrings("string");
   //----
   idOfConnector = CreateTradeConnector(hubAddress,
                                        hubPort,
                                        key,
                                        AccountBalance(),
                                        AccountEquity());

   Log("initialized");

   EventSetTimer(1);
   
   //----
   return(0);
}
//+------------------------------------------------------------------+
//| expert deinitialization function                                 |
//+------------------------------------------------------------------+
int deinit()
{
   //----
   Log("freeing");
   FreeTradeConnector(idOfConnector);
   //----
   return(0);
}

double lotsOfCurrentTrade() {
   string symbol = Symbol();

   double minimalLotsCount     = MarketInfo(symbol, MODE_MINLOT);
   double lotsCountGranularity = MarketInfo(symbol, MODE_LOTSTEP);
   double lotSize              = MarketInfo(symbol, MODE_LOTSIZE);

   double requestedValue = TradeGetValue(idOfConnector);

   double lotsCount = requestedValue / lotSize;

   if (lotsCount < minimalLotsCount) return(0);

   return (MathFloor(lotsCount / lotsCountGranularity) * lotsCountGranularity);
}

bool IsCurrentTradeInitiallyDelayed() {
   return (TradeGetRequestedDelay(idOfConnector) != NoBoundary);
}

int TypeToMtOpType(int type, bool isDelayed) {
   if (isDelayed) {
      if (type == TradeBuy) return (OP_BUYLIMIT);
      else return (OP_SELLLIMIT);
   } else {
      if (type == TradeBuy) return (OP_BUY);
      else return (OP_SELL);
   }
}

double calculateRequestedTp(int type) {
   double takeProfit = TradeGetRequestedTp(idOfConnector);

   if (cmp(takeProfit, 0)) {
      if (type == TradeBuy) {
         takeProfit = (openingPrice(type) * TpMaxMultiplier);
      } else {
         takeProfit = (openingPrice(type) / TpMaxMultiplier);
      }
   }

   return (takeProfit);
}

double shftDir(int type, double base, double shift) {
   if (type == TradeBuy) {
      return (base + shift);
   } else {
      return (base - shift);
   }
}

double shftContr(int type, double base, double shift) {
   return (shftDir(oppositeType(type), base, shift));
}

int oppositeType(int type) {
   if (type == TradeBuy) {
      return (TradeSell);
   } else {
      return (TradeBuy);
   }
}

double openingPrice(int type) {
   if (type == TradeBuy) {
      return (Ask);
   } else {
      return (Bid);
   }
}

double closingPrice(int type) {
   return (openingPrice(oppositeType(type)));
}

double closestStopOrDelay(int type, double basePrice) {
   string symbol = Symbol();
   double stopLevel = MarketInfo(symbol, MODE_STOPLEVEL);
   double stopMinimalDistance = stopLevel * Point;

   return (shftContr(type, basePrice, stopMinimalDistance));
}

double closestTp(int type, double basePrice) {
   return (closestStopOrDelay(oppositeType(type),
                             basePrice));
}

double norm(double dbl) {
   return (NormalizeDouble(dbl, Digits));
}

string d2s(double dbl) {
   return (DoubleToStr(dbl, Digits));
}

double farStop(int type, double one, double two) {
   if (type == TradeBuy) {
      return (MathMin(one, two));
   } else {
      return (MathMax(one, two));
   }
}

double farTp(int type, double one, double two) {
   // reversed as farStop
   return (farStop(oppositeType(type), one, two));
}

void printWillTryShift(string name,
                       double wanted,
                       double current,
                       double closestPossible) {

   Log(StringConcatenate("setting ",
                         name,
                         " to: ",
                         d2s(closestPossible),
                         ", requested: ",
                         d2s(wanted),
                         ", now: ",
                         d2s(current)));
}

int ctOpType;

double MarkerValue_NoTp;
double ctOpenPrice;
double ctStop;
double ctTake;

void calculateCurrentTradeBoundaries(double additionalShift) {
   int type = TradeGetType(idOfConnector);

   double stopsBasePrice = 0;
   double stopsShift = additionalShift;

   ctOpenPrice = TradeGetRequestedDelay(idOfConnector);
   bool isDelayed = ctOpenPrice != NoBoundary;

   ctOpType = TypeToMtOpType(type, isDelayed);

   if (isDelayed && !TradeGetIsOpened(idOfConnector)) {

      ctOpenPrice = norm(shftContr(type,
                                   farStop(type,
                                           ctOpenPrice,
                                           closestStopOrDelay(type,
                                                              openingPrice(type))),
                                   additionalShift));

      stopsBasePrice = ctOpenPrice;

      // for delayed trade no sense to shift limit prices
      stopsShift = 0;
   } else {
      ctOpenPrice = openingPrice(type);
      stopsBasePrice = closingPrice(type);
   }

   double requestedStop = TradeGetRequestedStop(idOfConnector);
   ctStop = norm(shftContr(type,
                           farStop(type,
                                   requestedStop,
                                   closestStopOrDelay(type,
                                                      stopsBasePrice)),
                           stopsShift));


   double requestedTp = calculateRequestedTp(type);

   MarkerValue_NoTp = 0;

   ctTake = norm(shftDir(type,
                         farTp(type,
                               requestedTp,
                               closestTp(type,
                                         stopsBasePrice)),
                         stopsShift));

   if (cmp(TradeGetRequestedTp(idOfConnector), 0)) {
      MarkerValue_NoTp = ctTake;
   }
}

bool isInOpenFreezeZone(int type, double price) {
   string symbol = Symbol();
   double freezeLevel = MarketInfo(symbol, MODE_FREEZELEVEL);
   double freezeDistance = freezeLevel * Point;

   if (cmp(freezeLevel, 0)) return (false);

   return (MathAbs(openingPrice(type) - price) < freezeDistance);
}

bool isInCloseFreezeZone(int type, double price) {
   return (isInOpenFreezeZone(oppositeType(type), price));
}

bool isStopCloser(int type, double newStop, double oldStop) {
   if (type == TradeBuy) {
      return (newStop > oldStop);
   } else {
      return (oldStop > newStop);
   }
}

bool isStopCloserOrSame(int type, double newStop, double oldStop) {
   return (cmp(newStop, oldStop) || isStopCloser(type, newStop, oldStop));
}

bool isTakeCloser(int type, double newTake, double oldTake) {
   return (isStopCloser(oppositeType(type), newTake, oldTake));
} 

bool isTakeCloserOrSame(int type, double newTake, double oldTake) {
   return (isStopCloserOrSame(oppositeType(type), newTake, oldTake));
}


void tradeUpdateBestSetValues() {
   TradeSetBestSetDelay(idOfConnector,
                        ctOpenPrice);

   TradeSetBestSetStop(idOfConnector, ctStop);

   if (cmp(ctTake, MarkerValue_NoTp)) {
      TradeSetBestSetTp(idOfConnector, 0);
   } else {
      TradeSetBestSetTp(idOfConnector, ctTake);
   }
}

void MessageFreeTrade(string how) {
               
   TradeMessage(idOfConnector,
                StringConcatenate(how,
                                  " closed on price ",
                                  d2s(OrderClosePrice()),
                                  ", profit: ",
                                  d2s(OrderProfit())));
}

void SendBalanceUpdate() {
   UpdateBalance(idOfConnector, AccountBalance());
} 

void processTradesCycle() {
   double stopMinimalDistance = MarketInfo(Symbol(), MODE_STOPLEVEL) * Point;

   StartNextTradesIteration(idOfConnector);
   while (ShiftToNextTrade(idOfConnector)) {

      int tradeType = TradeGetType(idOfConnector);
      int tradeMtId = TradeGetMtId(idOfConnector);
      int error;

      bool isNewRequest = tradeMtId == InvalidId;

      if (isNewRequest) {
         double stopsShift = StopsShiftInitial();

         int openRetryCount = 0;

         while (true) {
            RefreshRates();

            if (openRetryCount >= MAX_RETRY_COUNT) {
               Warning("Too many retries for opening trade, breaking.");
               break;
            }

            if (stopsShift > StopsShiftMax()) {
               Warning("Too big stops shift, breaking.");
               break;
            }

            openRetryCount = openRetryCount + 1;

            double lots = lotsOfCurrentTrade();

            if (lots == 0) {
               Warning("Trade requested with 0 lots volume.");
               FreeTrade(idOfConnector);
               break;
            }


            calculateCurrentTradeBoundaries(stopsShift);

            Log(StringConcatenate(" lots: ",            lots));
            Log(StringConcatenate("op type: ",          ctOpType));
            Log(StringConcatenate("trade is delayed: ", IsCurrentTradeInitiallyDelayed()));
            Log(StringConcatenate("requested delay: ",  d2s(TradeGetRequestedDelay(idOfConnector))));
            Log(StringConcatenate("open price: ",       d2s(ctOpenPrice)));
            Log(StringConcatenate("stopLoss: ",         d2s(ctStop)));
            Log(StringConcatenate("takeProfit: ",       d2s(ctTake)));

            int requestId = OrderSend(Symbol(),
                                      ctOpType,
                                      lots,
                                      ctOpenPrice,
                                      NormalizeDouble(Slippage(), Digits),
                                      ctStop,
                                      ctTake);


            Log(StringConcatenate("result of opening request: ", requestId));
            if (requestId != -1) {
               
               Log("operation successfully completed");
               TradeSetMtId(idOfConnector, requestId);

               if (! IsCurrentTradeInitiallyDelayed()) {

                  TradeMessage(idOfConnector,
                               StringConcatenate("opened on price ", d2s(openingPrice(tradeType))));

                  TradeSetIsOpened(idOfConnector, true);
                  TradeNotifyOpened(idOfConnector);
               } else {
                  TradeMessage(idOfConnector,
                               StringConcatenate("delayed request is placed"));
               } 

               tradeUpdateBestSetValues();

               break;
            } else {
               error = GetLastError();
               Warning(StringConcatenate("opening error is: ", error));
               switch(error) {
                  case ERR_INVALID_STOPS:
                     stopsShift = stopsShift + StopsShiftIncrement();
                     continue;
               }

               Warning("don't know how to handle this error, closing request");
               FreeTrade(idOfConnector);
               break;
            }
         }

      } else {

         if ( ! OrderSelect(tradeMtId, SELECT_BY_TICKET) ) {
            Warning(StringConcatenate("Failed to select order ", tradeMtId, " closing"));
            FreeTrade(idOfConnector);
            continue;
         }

         if ( ! TradeGetIsOpened(idOfConnector) ) {
            // if trade was no opened, we need to check if it was opened

            int currentOpType = OrderType();

            bool wasOpened = currentOpType == OP_BUY || currentOpType == OP_SELL;

            if (wasOpened) {
               TradeMessage(idOfConnector,
                            StringConcatenate("opened on price ", d2s(OrderOpenPrice())));
               
               TradeSetIsOpened(idOfConnector, wasOpened);
               TradeNotifyOpened(idOfConnector);
            }
         }

         if (OrderCloseTime() > 0) {

            if ( TradeGetIsOpened(idOfConnector) ) {
               MessageFreeTrade("externally");

               TradeNotifyClosed(idOfConnector);
               SendBalanceUpdate();
            } else {
               TradeMessage(idOfConnector,
                            "request externally cancelled");
            } 

            FreeTrade(idOfConnector);
            continue;
         }

         // check if trade freezed and can't be modified

         if (IsCurrentTradeInitiallyDelayed() && ! TradeGetIsOpened(idOfConnector)) {
            if (isInOpenFreezeZone(tradeType,
                                   TradeGetBestSetDelay(idOfConnector))) {
               continue;
            }
         } else {
            if (isInCloseFreezeZone(tradeType,
                                    TradeGetBestSetStop(idOfConnector))
                || isInCloseFreezeZone(tradeType,
                                       TradeGetBestSetTp(idOfConnector))) {
               continue;
            }
         }

         // check if trade wants to be closed

         if (TradeGetIsWantsClose(idOfConnector)) {
            // perform closing
            if (TradeGetIsOpened(idOfConnector)) {

               if (OrderClose(tradeMtId,
                              OrderLots(),
                              closingPrice(tradeType),
                              Slippage())) {

                  MessageFreeTrade("");
                     
                  SendBalanceUpdate();
                  
                  FreeTrade(idOfConnector);
               } else {
                  error = GetLastError();
                  Warning(StringConcatenate("error closing trade (", tradeMtId, "): ", error));
               }

            } else {
               if (OrderDelete(tradeMtId)) {
                  TradeMessage(idOfConnector, "order cancelled");
                  
                  FreeTrade(idOfConnector);
               } else {
                  error = GetLastError();
                  Warning(StringConcatenate("error deleting pending order (", tradeMtId, "): ", error));
               }
            }

            continue;
         }

         // if trade not wants to be closed, check if we can tune it's
         // parameters

         calculateCurrentTradeBoundaries(StopsShiftInitial());

         double requestedStop = TradeGetRequestedStop(idOfConnector);
         double currentStop = TradeGetBestSetStop(idOfConnector);

         double requestedDelay = TradeGetRequestedDelay(idOfConnector);
         double currentDelay = TradeGetBestSetDelay(idOfConnector);

         double requestedTake = TradeGetRequestedTp(idOfConnector);
         double currentTake = TradeGetBestSetTp(idOfConnector);

         if (TradeGetIsOpened(idOfConnector)) {

            bool stopSatisfied = cmp(currentStop, requestedStop);
            bool tpSatisfied = cmp(currentTake, requestedTake);

            if (stopSatisfied && tpSatisfied) continue;

            bool willNotBreakStop = isStopCloserOrSame(tradeType,
                                                       ctStop,
                                                       currentStop);

            bool willNotBreakTake = isTakeCloserOrSame(tradeType,
                                                       ctTake,
                                                       currentTake);

            if (willNotBreakTake && willNotBreakStop) {
               if (!stopSatisfied) {
                  printWillTryShift("stop", requestedStop, currentStop, ctStop);
               }

               if (!tpSatisfied) {
                  printWillTryShift("take", requestedTake, currentTake, ctTake);
               }

               if (OrderModify(tradeMtId,
                               OrderOpenPrice(),
                               ctStop,
                               ctTake,
                               0)) {

                  tradeUpdateBestSetValues();
               } else {
                  error = GetLastError();
                     
                  Warning(StringConcatenate("Error modifying opened order: ", error));
               }
            } 
            
         } else {
            bool delaySatisfied = cmp(requestedDelay, 0)
               || TradeGetIsOpened(idOfConnector)
               || cmp(currentDelay, requestedDelay);

            if (delaySatisfied) continue;

            bool newDelayCloser = isStopCloser(tradeType,
                                               ctOpenPrice,
                                               currentDelay);

            if (!newDelayCloser) continue;

            printWillTryShift("delay", requestedDelay, currentDelay, ctOpenPrice);

            bool willChangeStop = currentStop != ctStop;

            if (willChangeStop) {
               printWillTryShift("stop", requestedStop, currentStop, ctStop);               
            } 

            bool willChangeTake = (currentTake != ctTake) && (! ((cmp(ctTake, MarkerValue_NoTp))
                                                                 && (cmp(currentTake, 0))));

            if (willChangeTake) {
               printWillTryShift("take", requestedTake, currentTake, ctTake);               
            }

            if (OrderModify(tradeMtId,
                            ctOpenPrice,
                            ctStop,
                            ctTake,
                            0)) {

               tradeUpdateBestSetValues();
            } else {
               error = GetLastError();

               Warning(StringConcatenate("Error modifying pending order: ", error));
            }

         } 
      }

   }
} 


//+------------------------------------------------------------------+
//| expert start function                                            |
//+------------------------------------------------------------------+
int start()
{
   Log(StringConcatenate("tick: ", d2s(Bid), "|", d2s(Ask)));

   UpdateEquity(idOfConnector, AccountEquity());

   processTradesCycle();

   //----
   return(0);
}

void OnTimer() {
   Log("timer event");
   
   processTradesCycle();
} 
//+------------------------------------------------------------------+
