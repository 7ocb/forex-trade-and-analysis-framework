#include "types.h"
#include <limits>

const double InvalidDoubleValue = std::numeric_limits<double>::min();

Boundary::Boundary()
   : _value(InvalidDoubleValue) {
      
}

Boundary::Boundary(const double value)
   : _value(value) {
      
} 

double Boundary::value() const {
   return _value;
} 
   
Boundary::~Boundary() {}


std::ostream &operator<<(std::ostream &str, const Boundary &boundary) {
   str << boundary.value();
   return str;
}

TradeRequest::TradeRequest(double value,
                           TradeType tradeType,
                           const Option<Boundary> &delay)
   : _value(value)
   , _tradeType(tradeType)
   , _delay(delay) {
      
} 

double TradeRequest::value() const {
   return _value;
}
   
TradeType TradeRequest::tradeType() const {
   return _tradeType;
}
   
const Option<Boundary> &TradeRequest::delay() const {
   return _delay;
}
   
   
TradeRequest::~TradeRequest() {}



