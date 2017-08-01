#ifndef __00DBA47363BD6C60F9371B85F61DDC11_STATEERROR_H_INCLUDED__
#define __00DBA47363BD6C60F9371B85F61DDC11_STATEERROR_H_INCLUDED__

#include <string>
#include "platform.h"
#include "ConnectionState.h"

class StateError : public ConnectionState {
public:
   StateError();

   void init();

   void onError();
   void sendData(const std::string &buffer);
   void onDataReceived(const std::string &data);
   void close();

   virtual ~StateError();

private:
};

#endif 	// __00DBA47363BD6C60F9371B85F61DDC11_STATEERROR_H_INCLUDED__
