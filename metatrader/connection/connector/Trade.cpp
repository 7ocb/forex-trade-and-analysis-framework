#include "Trade.h"

Trade::Trade(uint64 id,
             TradeType type,
             double value,
             const Option<Boundary> &delay,
             const Boundary &stop,
             const Option<Boundary> &takeProfit)
   : _id(id)
   , _mtId(InvalidId)
   , _type(type)
   , _value(value)
   , _requestedDelay(delay)
   , _requestedStop(stop)
   , _requestedTp(takeProfit)
   , _isWantsClose(false)
   , _isOpened(false) {
      
} 

TradeType Trade::getType() const {
   return _type;
}

double Trade::getValue() const {
   return _value;
}

Boundary Trade::getRequestedStop() const {
   return _requestedStop;
}

void Trade::setRequestedStop(const Boundary& newStop) {
   _requestedStop = newStop;
}

Boundary Trade::getBestSetStop() const {
   return _bestSetStop;
}

void Trade::setBestSetStop(const Boundary& newStop) {
   _bestSetStop = newStop;
}

Option<Boundary> Trade::getRequestedDelay() const {
   return _requestedDelay;
}

void Trade::setRequestedDelay(const Option<Boundary>& newDelay) {
   _requestedDelay = newDelay;
}

Option<Boundary> Trade::getBestSetDelay() const {
   return _bestSetDelay;
}

void Trade::setBestSetDelay(const Option<Boundary>& newDelay) {
   _bestSetDelay = newDelay;
}

Option<Boundary> Trade::getRequestedTp() const {
   return _requestedTp;
}

void Trade::setRequestedTp(const Option<Boundary>& newTp) {
   _requestedTp = newTp;
}

Option<Boundary> Trade::getBestSetTp() const {
   return _bestSetTp;

}
void Trade::setBestSetTp(const Option<Boundary>& newTp) {
   _bestSetTp = newTp;
}

bool Trade::getIsWantsClose() const {
   return _isWantsClose;
}

void Trade::setIsWantsClose() {
   _isWantsClose = true;
}

bool Trade::getIsOpened() const {
   return _isOpened;
}

void Trade::setIsOpened(bool value) {
   _isOpened = value;
}


uint64 Trade::getId() const {
   return _id;
}

int Trade::getMtId() const {
   return _mtId;
}

void Trade::setMtId(int newId) {
   _mtId = newId;
}
