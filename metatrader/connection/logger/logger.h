#ifndef __2F2FA5BD71D942F0A1C3759F1DB9657D_LOGGER_H_INCLUDED__
#define __2F2FA5BD71D942F0A1C3759F1DB9657D_LOGGER_H_INCLUDED__

#include "platform.h"
#include <fstream>
#include <functional>
#include <ostream>

class Logger {
   Logger(const Logger &referenceToCopyFrom);
   void operator=(const Logger &referenceToCopyFrom);
public:
   Logger(const std::string &type, const std::string &address, int port, const std::string &key);

   void log(const std::string& line);
   void log(const std::function<void(std::ostream &)> logFunction);

   ~Logger();
private:
   std::string _prefix;
   Monitor *_monitor;
};

#endif 	// __2F2FA5BD71D942F0A1C3759F1DB9657D_LOGGER_H_INCLUDED__
