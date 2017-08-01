#include "protocol.h"
#include "OutputDataBuffer.h"
#include <iostream>

using namespace Protocol;

RegisterTicksProvider::RegisterTicksProvider(const std::string &key) {
   _buffer = OutputDataBuffer()
      .putString("RegisterTicksProvider")
      .putString(key)
      .buffer();
}

RegisterTradeConnector::RegisterTradeConnector(const std::string &key, double balance, double equity) {
   _buffer = OutputDataBuffer()
      .putString("RegisterTradeConnector")
      .putString(key)
      .putDouble(balance)
      .putDouble(equity)
      .buffer();
}

CurrentBalance::CurrentBalance(double balance) {
   _buffer = OutputDataBuffer()
      .putString("CurrentBalance")
      .putDouble(balance)
      .buffer();
}

CurrentEquity::CurrentEquity(double equity) {
   _buffer = OutputDataBuffer()
      .putString("CurrentEquity")
      .putDouble(equity)
      .buffer();
}

OnTick::OnTick(double bid, double ack) {
   _buffer = OutputDataBuffer()
      .putString("OnTick")
      .putDouble(bid)
      .putDouble(ack)
      .buffer();
}

NewId::NewId(uint64 id) {
   _buffer = OutputDataBuffer()
      .putString("NewId")
      .putLong(id)
      .buffer();
}

const std::string Protocol::RequestNewId::NAME = "RequestNewId";

const std::string Protocol::OpenTrade::NAME = "OpenTrade";


Boundary readBoundary(InputDataBuffer& buffer) {
   // now supported only isEqual, so, if it is not, log warning for now
   if (!buffer.nextBool()) std::cout << "error, boundary with !isEqual not supported" << std::endl;

   // now only value parsed from boundary, not the direction or equality flag
   buffer.nextBool();
      
   return Boundary(buffer.nextDouble());
} 

Option<Boundary> readOptionaBoundary(InputDataBuffer& buffer) {
   const bool isDefined = buffer.nextBool();

   if (isDefined) {
      return Option<Boundary>(readBoundary(buffer));
   } else {
      return Option<Boundary>();
   } 
} 

OpenTrade::OpenTrade(InputDataBuffer buffer) {
   _id = buffer.nextLong();

   const double value = buffer.nextDouble();

   TradeType tradeType = TradeBuy;

   const std::string tradeTypeKey = buffer.nextString();
   
   if (tradeTypeKey == "Sell") tradeType = TradeSell;

   _tradeRequest = new TradeRequest(value,
                                    tradeType,
                                    readOptionaBoundary(buffer));

   _stopValue = readBoundary(buffer);

   _takeProfit = readOptionaBoundary(buffer);
}

void OpenTrade::dump(std::ostream& stream) const {
   stream << "OpenTrade"
          << ", id: " << _id
          << ", request: value: " << _tradeRequest->value()
          << ", type: " << _tradeRequest->tradeType()
          << ", delay: " << _tradeRequest->delay()
          << ", stop: " << _stopValue.value()
          << ", tp: " << _takeProfit;
} 

OpenTrade::~OpenTrade() {
   delete _tradeRequest;
} 

const std::string Protocol::CloseRequest::NAME = "CloseRequest";

CloseRequest::CloseRequest(InputDataBuffer buffer) {
   _id = buffer.nextLong();
}

void CloseRequest::dump(std::ostream& stream) const {
   stream << "CloseRequest"
          << ", id: " << _id;
} 

const std::string Protocol::UpdateStopRequest::NAME = "UpdateStopRequest";

UpdateStopRequest::UpdateStopRequest(InputDataBuffer buffer) {
   _id = buffer.nextLong();

   _stopValue = readBoundary(buffer);
}

void UpdateStopRequest::dump(std::ostream& stream) const {
   stream << "UpdateStopRequest"
          << ", id: " << _id
          << ", stop: " << _stopValue.value();
}


const std::string Protocol::UpdateTakeProfitRequest::NAME = "UpdateTakeProfitRequest";

UpdateTakeProfitRequest::UpdateTakeProfitRequest(InputDataBuffer buffer) {
   _id = buffer.nextLong();

   _takeProfitValue = readOptionaBoundary(buffer);
}

void UpdateTakeProfitRequest::dump(std::ostream& stream) const {
   stream << "UpdateTakeProfitRequest"
          << ", id: " << _id
          << ", tp: " << _takeProfitValue;
}


OpenedResponse::OpenedResponse(uint64 id) {
   _buffer = OutputDataBuffer()
      .putString("OpenedResponse")
      .putLong(id)
      .buffer();
}

ExternallyClosed::ExternallyClosed(uint64 id) {
   _buffer = OutputDataBuffer()
      .putString("ExternallyClosed")
      .putLong(id)
      .buffer();
}


MessageAboutTrade::MessageAboutTrade(uint64 id, const std::string &message) {
   _buffer = OutputDataBuffer()
      .putString("MessageAboutTrade")
      .putLong(id)
      .putString(message)
      .buffer();
}

FreeTrade::FreeTrade(uint64 id) {
   _buffer = OutputDataBuffer()
      .putString("FreeTrade")
      .putLong(id)
      .buffer();
}
