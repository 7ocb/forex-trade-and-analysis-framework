#ifndef __CD021F925D4953D69758BEA97E0C4F76_WINSOCKET_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_WINSOCKET_H_INCLUDED__

#include "Socket.h"
#include <string>

class WinSocket : public Socket {
public:

   WinSocket();
   
   bool connect(const std::string &address,
                int port);

   bool read(std::string& buffer, int count);
   bool write(const std::string& buffer);

   void close();
   
   ~WinSocket();
private:
   int _socket;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_WINSOCKET_H_INCLUDED__
