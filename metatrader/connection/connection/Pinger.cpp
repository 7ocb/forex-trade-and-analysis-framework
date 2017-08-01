#include "Pinger.h"

#define PING_TIMEOUT_MS 7000
#define PING_INTERVAL_MS 1000

const std::string Pinger::PING_PACKET = "";

Pinger::Pinger(RunLoop &runLoop,
               PingerListener &listener)
   : _runLoop(runLoop)
   , _listener(listener)
   , _pingTask(nullptr)
   , _pingTimeoutTask(nullptr) {

   postSendPing();
}

bool Pinger::isPing(const std::string &packet) {
   if (packet.size() == 0) {

      if (_pingTimeoutTask) {
         _runLoop.cancel(_pingTimeoutTask);
      } 

      _pingTimeoutTask = _runLoop.postDelayed(PING_TIMEOUT_MS,
                                              std::bind(&Pinger::ctPingTimeout, this));
            
      return true;
   } 

   return false;
} 

void Pinger::stop() {
   if (_pingTask) {
      _runLoop.cancel(_pingTask);
      _pingTask = nullptr;
   }

   if (_pingTimeoutTask) {
      _runLoop.cancel(_pingTimeoutTask);
      _pingTimeoutTask = nullptr;
   }
} 

void Pinger::ctPingTimeout() {
   _pingTimeoutTask = nullptr;

   stop();
   
   _listener.onPingTimedOut();
}

void Pinger::ctSendPing() {
   postSendPing();
   _listener.sendPing();
}
   
void Pinger::postSendPing() {
   _pingTask = _runLoop.postDelayed(PING_INTERVAL_MS,
                                    std::bind(&Pinger::ctSendPing, this));
} 
