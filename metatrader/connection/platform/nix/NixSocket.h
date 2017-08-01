#ifndef __CD021F925D4953D69758BEA97E0C4F76_NIXSOCKET_H_INCLUDED__
#define __CD021F925D4953D69758BEA97E0C4F76_NIXSOCKET_H_INCLUDED__

#include "Socket.h"
#include <string>

class NixSocket : public Socket {
public:

   NixSocket();
   
   bool connect(const std::string &address,
                int port);

   bool read(std::string& buffer, int count);
   bool write(const std::string& buffer);

   void close();
   
   ~NixSocket();
private:
   int _socket;
};

#endif 	// __CD021F925D4953D69758BEA97E0C4F76_NIXSOCKET_H_INCLUDED__
