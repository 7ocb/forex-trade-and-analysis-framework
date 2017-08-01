#ifndef __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTING_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTING_H_INCLUDED__

#include <string>
#include <list>
#include "RunLoop.h"
#include "platform.h"
#include "ConnectionState.h"

class StateConnecting : virtual public ConnectionState {
public:
   StateConnecting(const Context &context,
                   const std::string& addressString,
                   const int port);

   void initState();

   void sendData(const std::string &buffer);

   bool shouldDeliverEvents() { return false; }
   
   virtual ~StateConnecting();

private:

   bool closeStateIfClosed();
   
   void connectThreadMethod();
   void onConnected();
   void onError();

private:
   const std::string _address;
   const int _port;
   Socket *_socket;   
   Thread *_thread;

   std::list<std::string> _delayedData;

   bool _closed;
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_STATECONNECTING_H_INCLUDED__
