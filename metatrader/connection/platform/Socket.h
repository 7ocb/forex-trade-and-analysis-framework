#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_SOCKET_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_SOCKET_H_INCLUDED__

#include <string>

class Socket {
public:

   virtual bool connect(const std::string &address,
                        int port) = 0;

   virtual bool read(std::string& buffer, int count) = 0;
   virtual bool write(const std::string& buffer) = 0;

   virtual void close() = 0;
   
   virtual ~Socket() {}
};

#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_SOCKET_H_INCLUDED__
