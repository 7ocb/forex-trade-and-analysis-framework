#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_OUTPUTDATABUFFER_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_OUTPUTDATABUFFER_H_INCLUDED__

#include "common.h"
#include <string>

class OutputDataBuffer {
public:
   OutputDataBuffer() {}

   OutputDataBuffer &putString(const std::string& string);

   OutputDataBuffer &putDouble(double value);

   OutputDataBuffer &putInt(int value);

   OutputDataBuffer &putLong(uint64 value);

   std::string buffer() { return _data; }
   
   ~OutputDataBuffer() {}

private:
   std::string _data;
};

#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_OUTPUTDATABUFFER_H_INCLUDED__
