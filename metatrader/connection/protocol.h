#ifndef __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PROTOCOL_H_INCLUDED__
#define __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PROTOCOL_H_INCLUDED__

#include "Option.h"
#include "InputDataBuffer.h"
#include "types.h"
#include <string>
#include <iostream>

namespace Protocol {

   class RegisterTicksProvider {

   public:
      RegisterTicksProvider(const std::string &key);

      std::string buffer() { return _buffer; };

      virtual ~RegisterTicksProvider() {}
   private:
      std::string _buffer;
   };

   class RegisterTradeConnector {

   public:
      RegisterTradeConnector(const std::string &key, double balance, double equity);

      std::string buffer() { return _buffer; };

      virtual ~RegisterTradeConnector() {}
   private:
      std::string _buffer;
   };

   class CurrentBalance {

   public:
      CurrentBalance(double balance);

      std::string buffer() { return _buffer; };

      virtual ~CurrentBalance() {}
   private:
      std::string _buffer;
   };

   class CurrentEquity {

   public:
      CurrentEquity(double equity);

      std::string buffer() { return _buffer; };

      virtual ~CurrentEquity() {}
   private:
      std::string _buffer;
   };   

   class OnTick {
   public:
      OnTick(double bid, double ack);

      std::string buffer() { return _buffer; };

      virtual ~OnTick() {}
   private:
      std::string _buffer;
   };

   class NewId {
   public:
      NewId(uint64 id);

      std::string buffer() { return _buffer; };

      virtual ~NewId() {}
   private:
      std::string _buffer;
   };

   class RequestNewId {
   public:
      static const std::string NAME;
   };

   class OpenTrade {
   public:
      static const std::string NAME;

      OpenTrade(InputDataBuffer buffer);

      uint64 id() { return _id; }

      TradeRequest tradeRequest() const { return *_tradeRequest; }
      
      Boundary stopValue() const { return _stopValue; }

      const Option<Boundary> &takeProfit() const { return _takeProfit; }

      void dump(std::ostream& stream) const;
      
      virtual ~OpenTrade();
      
   private:
      uint64 _id;
      TradeRequest *_tradeRequest;
      Boundary _stopValue;
      Option<Boundary> _takeProfit;
   };

   inline std::ostream &operator<<(std::ostream &str, const OpenTrade& packet) {
      packet.dump(str);
      return str;
   } 

   class CloseRequest {
   public:
      static const std::string NAME;

      CloseRequest(InputDataBuffer buffer);

      uint64 id() { return _id; }

      void dump(std::ostream& stream) const;
      
      virtual ~CloseRequest() {}
      
   private:
      uint64 _id;
   };

   inline std::ostream &operator<<(std::ostream &str, const CloseRequest& packet) {
      packet.dump(str);
      return str;
   } 
   
   class UpdateStopRequest {
   public:
      static const std::string NAME;

      UpdateStopRequest(InputDataBuffer buffer);

      uint64 id() { return _id; }

      Boundary stopValue() const { return _stopValue; }

      void dump(std::ostream& stream) const;
      
      virtual ~UpdateStopRequest() {}
      
   private:
      uint64 _id;
      Boundary _stopValue;
   };

   inline std::ostream &operator<<(std::ostream &str, const UpdateStopRequest& packet) {
      packet.dump(str);
      return str;
   }

   class UpdateTakeProfitRequest {
   public:
      static const std::string NAME;

      UpdateTakeProfitRequest(InputDataBuffer buffer);

      uint64 id() { return _id; }

      const Option<Boundary> &takeProfitValue() const { return _takeProfitValue; }

      void dump(std::ostream& stream) const;
      
      virtual ~UpdateTakeProfitRequest() {}
      
   private:
      uint64 _id;
      Option<Boundary> _takeProfitValue;
   };

   inline std::ostream &operator<<(std::ostream &str, const UpdateTakeProfitRequest& packet) {
      packet.dump(str);
      return str;
   }

   class OpenedResponse {
   public:
      OpenedResponse(uint64 id);

      std::string buffer() { return _buffer; };

      virtual ~OpenedResponse() {}
   private:
      std::string _buffer;
   };

   class ExternallyClosed {
   public:
      ExternallyClosed(uint64 id);

      std::string buffer() { return _buffer; };

      virtual ~ExternallyClosed() {}
   private:
      std::string _buffer;
   };

   class MessageAboutTrade {
   public:
      MessageAboutTrade(uint64 id, const std::string &message);

      std::string buffer() { return _buffer; };

      virtual ~MessageAboutTrade() {}
   private:
      std::string _buffer;
   };

   class FreeTrade {
   public:
      FreeTrade(uint64 id);

      std::string buffer() { return _buffer; };

      virtual ~FreeTrade() {}
   private:
      std::string _buffer;
   };
}

#endif 	// __5B38EFCBEAF9BC48BC4BB89BC5A06F67_PROTOCOL_H_INCLUDED__
