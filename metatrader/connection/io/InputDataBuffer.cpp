#include "InputDataBuffer.h"
#include "platform.h"
#include <stdio.h>


InputDataBuffer::InputDataBuffer(const std::string &data)
   : _offset(0)
   , _data(data) {
   
} 

std::string InputDataBuffer::nextString() {
   const int bytesInString = nextInt();

   std::string output(_data.data() + _offset,
                      bytesInString);

   _offset += bytesInString;

   return output;
} 

double InputDataBuffer::nextDouble() {
   std::string nextDoubleAsString = nextString();

   double output;

   sscanf(nextDoubleAsString.c_str(), "%lf", &output);

   return output;
}

int InputDataBuffer::nextInt() {
   int next = Platform::instance().ntohl(*((int *)(_data.data() + _offset)));
   _offset += sizeof(int);
   return next;
}

bool InputDataBuffer::nextBool() {
   const char c = *((char *)(_data.data() + _offset));
   ++_offset;
   return c;
}

uint64 InputDataBuffer::nextLong() {
   uint64 next = Platform::instance().ntohll(*((uint64 *)(_data.data() + _offset)));
   _offset += sizeof(uint64);
   return next;
}
