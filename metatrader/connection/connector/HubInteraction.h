#ifndef __AE17FFA043F62C902EF2CF0C5B94CA1B_HUBINTERACTION_H_INCLUDED__
#define __AE17FFA043F62C902EF2CF0C5B94CA1B_HUBINTERACTION_H_INCLUDED__

#include <functional>
#include "logger.h"
#include "RunLoopUser.h"
#include "ConnectionHandle.h"
#include "ConnectionHandleListener.h"

class HubInteraction : protected ConnectionHandleListener
                     , private RunLoopUser {

   HubInteraction(const HubInteraction &referenceToCopyFrom);
   void operator=(const HubInteraction &referenceToCopyFrom);
   
public:

   typedef std::function<void()>                    EventReceiver;
   typedef std::function<void(const std::string &)> PacketReceiver;
   
   HubInteraction(RunLoop &runLoop,
                  Logger &logger,
                  const std::string &address,
                  int port,
                  const EventReceiver &onRestarted    = EventReceiver(),
                  const PacketReceiver &onPacket      = PacketReceiver(),
                  const EventReceiver &onDisconnected = EventReceiver());

   bool haveConnection();

   void sendRawData(const std::string &data);

   ~HubInteraction();
private:

   void freeConnection();
   void startConnecting();
   
   void onPacket(const std::string& buffer);
   void onConnectFailed();
   void onDisconnect();

   void handleConnectionFailure(bool isDisconnect);
   
private:
   
   Logger &_logger;
   const std::string _address;
   const int _port;
   
   ConnectionHandle *_connection;

   const EventReceiver  _onRestarted;
   const PacketReceiver _onPacket;
   const EventReceiver  _onDisconnected;
};

#endif 	// __AE17FFA043F62C902EF2CF0C5B94CA1B_HUBINTERACTION_H_INCLUDED__
