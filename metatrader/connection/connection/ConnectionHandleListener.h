#ifndef __E88FD3818043A0B8F3E70E4C30082CC3_CONNECTIONHANDLELISTENER_H_INCLUDED__
#define __E88FD3818043A0B8F3E70E4C30082CC3_CONNECTIONHANDLELISTENER_H_INCLUDED__

#include <string>

class ConnectionHandleListener {
public:
   virtual void onPacket(const std::string& buffer) = 0;
   virtual void onConnectFailed() = 0;
   virtual void onDisconnect() = 0;

   virtual ~ConnectionHandleListener() {}
};

#endif 	// __E88FD3818043A0B8F3E70E4C30082CC3_CONNECTIONHANDLELISTENER_H_INCLUDED__
