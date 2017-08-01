#include "OutputDataBuffer.h"
#include "platform.h"
#include <stdio.h>


OutputDataBuffer &OutputDataBuffer::putString(const std::string& string) {
   putInt(string.size());
   _data.append(string.data(),
                string.size());
   return *this;
} 

OutputDataBuffer &OutputDataBuffer::putDouble(double value) {
   char buffer[1024];

   sprintf(buffer, "%lf", value);

   return putString(buffer);
   
   return *this;
} 

OutputDataBuffer &OutputDataBuffer::putInt(int value) {

   int convertedValue = Platform::instance().htonl(value);
   
   _data.append((char *)&convertedValue, 4);
   
   return *this;
}

OutputDataBuffer &OutputDataBuffer::putLong(uint64 value) {

   uint64 convertedValue = Platform::instance().htonll(value);
   
   _data.append((char *)&convertedValue, 8);
   
   return *this;
}

