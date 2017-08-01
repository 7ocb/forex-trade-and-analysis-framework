#ifndef __00DBA47363BD6C60F9371B85F61DDC11_STATEDISCONNECTED_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_STATEDISCONNECTED_H_INCLUDED__

#include <string>
#include <list>
#include "RunLoop.h"
#include "platform.h"
#include "ConnectionState.h"

class StateDisconnected : virtual public ConnectionState {
public:
   StateDisconnected(const Context &context);

   void initState();

   void sendData(const std::string &buffer) { /* no data can be sent in connect failed state */ }

   bool shouldDeliverEvents() { return false; }

   virtual ~StateDisconnected();
   
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_STATEDISCONNECTED_H_INCLUDED__
