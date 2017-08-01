#ifndef __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__

#include <string>
#include <list>
#include "RunLoopUser.h"
#include "platform.h"
#include "ConnectionState.h"
#include "Pinger.h"

class StateConnected : virtual public ConnectionState
                     , private PingerListener
                     , private RunLoopUser {
public:
   StateConnected(const Context &context,
                  Socket *socket,
                  const std::list<std::string> &delayedData);

   void initState();

   void sendData(const std::string &buffer);

   bool shouldDeliverEvents() { return true; }

   virtual ~StateConnected();

private:
   void close();

   void onPingTimedOut();
   void sendPing();

   void wtReadThreadMethod();
   void wtWriteThreadMethod();

   void switchToErrorIfNotClosed();

   static bool sendPacket(Socket *socket, const std::string &data);
   static bool receivePacket(Socket *socket, std::string &data);
   
private:
   
   RunLoop _sendRunLoop;
   
   Thread *_readThread;
   Thread *_writeThread;

   bool _closed;
   
   Pinger _pinger;
   Socket *_socket;
   const std::list<std::string> _delayedData;
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTED_H_INCLUDED__
