#include "MTTicksSink.h"
#include "protocol.h"
#include <stdio.h>
#include <sstream>

MTTicksSink::MTTicksSink(RunLoop &runLoop,
                         const std::string &address,
                         int port,
                         const std::string &key)
   : RunLoopUser(runLoop)
   , _logger("ticks", address, port, key)
   , _key(key)
   , _hubInteraction(runLoop,
                     _logger,
                     address,
                     port,
                     std::bind(&MTTicksSink::onStartedConnection, this)) {

} 

void MTTicksSink::sendTick(double bid, double ask) {
   post([=]() -> void {
         if (_hubInteraction.haveConnection()) {

            _logger.log([bid, ask](std::ostream &str) -> void {
                  str << "sending tick: " << bid << "|" << ask;
               } );
   
            _hubInteraction.sendRawData(Protocol::OnTick(bid, ask).buffer());
         }
      });
} 

MTTicksSink::~MTTicksSink() {
   
}

void MTTicksSink::onStartedConnection() {
   _hubInteraction.sendRawData(Protocol::RegisterTicksProvider(_key).buffer());
} 
