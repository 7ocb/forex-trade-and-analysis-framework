#ifndef __5B38EFCBEAF9BC48BC4BB89BC5A06F67_TYPES_H_INCLUDED__
#define __5B38EFCBEAF9BC48BC4BB89BC5A06F67_TYPES_H_INCLUDED__

#include "Option.h"

enum TradeType {
   TradeBuy = 1,
   TradeSell = 2
};

class Boundary {
public:
   Boundary();
   Boundary(const double value);

   double value() const;
   
   ~Boundary();
private:
   double _value;
};

std::ostream &operator<<(std::ostream &str, const Boundary &boundary); 
   
class TradeRequest {
public:
   TradeRequest(double value,
                TradeType tradeType,
                const Option<Boundary> &delay);

   double value() const;
   TradeType tradeType() const;
   const Option<Boundary> &delay() const;
   
   virtual ~TradeRequest();

private:

   const double _value;
   const TradeType _tradeType;
   const Option<Boundary> _delay;
};
   


#endif 	// __5B38EFCBEAF9BC48BC4BB89BC5A06F67_TYPES_H_INCLUDED__
