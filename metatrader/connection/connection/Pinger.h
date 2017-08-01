#ifndef __E88FD3818043A0B8F3E70E4C30082CC3_PINGER_H_INCLUDED__
#define __E88FD3818043A0B8F3E70E4C30082CC3_PINGER_H_INCLUDED__

#include "RunLoop.h"

class PingerListener {
public:
   virtual void onPingTimedOut() = 0;
   virtual void sendPing() = 0;
   virtual ~PingerListener() {}
};

class Pinger {
public:

   const static std::string PING_PACKET;
   
   Pinger(RunLoop &runLoop,
          PingerListener &listener);

   bool isPing(const std::string &packet);

   void stop();

   virtual ~Pinger() { stop(); }
   
private:

   void ctPingTimeout();
   void ctSendPing();
   void postSendPing();

private:
   
   RunLoop &_runLoop;
   PingerListener &_listener;

   RunLoop::Task *_pingTask;
   RunLoop::Task *_pingTimeoutTask;
};

#endif 	// __E88FD3818043A0B8F3E70E4C30082CC3_PINGER_H_INCLUDED__
