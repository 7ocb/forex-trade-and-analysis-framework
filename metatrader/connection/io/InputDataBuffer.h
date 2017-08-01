#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_INPUTDATABUFFER_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_INPUTDATABUFFER_H_INCLUDED__

#include "common.h"
#include <string>

class InputDataBuffer {
public:
   InputDataBuffer(const std::string &data);

   std::string nextString();

   double nextDouble();

   int nextInt();

   bool nextBool();

   uint64 nextLong();

   ~InputDataBuffer() {}

private:
   int _offset;
   std::string _data;
};

#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_INPUTDATABUFFER_H_INCLUDED__
