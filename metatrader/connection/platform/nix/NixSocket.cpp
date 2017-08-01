#include "NixSocket.h"
#include <memory.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>

NixSocket::NixSocket() {
   _socket = socket(AF_INET, SOCK_STREAM, 0);
}

bool NixSocket::connect(const std::string &address,
                        int port) {

   struct addrinfo hints;
   addrinfo *info;

   memset(&hints, 0, sizeof(struct addrinfo));
   hints.ai_family = AF_INET;
   hints.ai_socktype = SOCK_STREAM;
   hints.ai_flags = AI_PASSIVE;
   hints.ai_protocol = 0;
   hints.ai_canonname = NULL;
   hints.ai_addr = NULL;
   hints.ai_next = NULL;

   int result = getaddrinfo(address.c_str(),
                            NULL,
                            &hints,
                            &info);

   if (result != 0) return false;

   sockaddr_in *target = (sockaddr_in *)info->ai_addr;
   target->sin_port = htons(port);

   result = ::connect(_socket,
                      info->ai_addr,
                      info->ai_addrlen);

   freeaddrinfo(info);

   return result == 0;
}

bool NixSocket::read(std::string &outputBuffer, int count) {
   char * const buffer = new char[count];

   bool success = true;
   
   int leftToRead = count;
   int offset = 0;

   while (leftToRead > 0) {
      const int received = recv(_socket,
                                buffer + offset,
                                leftToRead,
                                0);
      if (received <= 0) {
         success = false;
         break;
      }

      leftToRead -= received;
      offset += received;
   } 

   if (success) {

      outputBuffer.assign(buffer,
                          count);
   }

   delete[] buffer;

   return success;
}

bool NixSocket::write(const std::string& buffer) {

   int leftToSend = buffer.size();
   int offset = 0;

   while (leftToSend > 0) {
      const int sent = send(_socket,
                            buffer.data() + offset,
                            leftToSend,
                            MSG_NOSIGNAL);

      if (sent <= 0) return false;

      leftToSend -= sent;
   }

   return true;
}

void NixSocket::close() {
   ::close(_socket);
}

NixSocket::~NixSocket() {
   // should be cleaned up in close
}
