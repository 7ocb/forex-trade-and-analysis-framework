#ifndef __9EA460711E4601B02FF255E7D9195508_TRADE_H_INCLUDED__
#define __9EA460711E4601B02FF255E7D9195508_TRADE_H_INCLUDED__

#include "common.h"
#include "types.h"

class Trade {
public:

   enum {
      InvalidId = -1
   };
   
   Trade(uint64 id,
         TradeType type,
         double value,
         const Option<Boundary> &delay,
         const Boundary &stop,
         const Option<Boundary> &takeProfit);

   TradeType getType() const;
   double getValue() const;

   Boundary getRequestedStop() const;
   void setRequestedStop(const Boundary& newStop);

   Boundary getBestSetStop() const;
   void setBestSetStop(const Boundary& newStop);

   Option<Boundary> getRequestedDelay() const;
   void setRequestedDelay(const Option<Boundary>& newDelay);

   Option<Boundary> getBestSetDelay() const;
   void setBestSetDelay(const Option<Boundary>& newDelay);

   Option<Boundary> getRequestedTp() const;
   void setRequestedTp(const Option<Boundary>& newTp);

   Option<Boundary> getBestSetTp() const;
   void setBestSetTp(const Option<Boundary>& newTp);

   bool getIsWantsClose() const;
   void setIsWantsClose();

   bool getIsOpened() const;
   void setIsOpened(bool isOpened);

   uint64 getId() const;

   int getMtId() const;
   void setMtId(int newId);

private:
   uint64 _id;
   int _mtId;

   TradeType _type;
   double _value;

   Option<Boundary> _requestedDelay;
   Option<Boundary> _bestSetDelay;

   Boundary _requestedStop;
   Boundary _bestSetStop;

   Option<Boundary> _requestedTp;
   Option<Boundary> _bestSetTp;

   bool _isWantsClose;
   bool _isOpened;
};


#endif 	// __9EA460711E4601B02FF255E7D9195508_TRADE_H_INCLUDED__
