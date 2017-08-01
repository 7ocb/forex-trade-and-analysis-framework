#include "logger.h"
#include <sstream>
#include "platform.h"
#include <iostream>

#define ENABLE_LOGGER

Logger::Logger(const std::string &type, const std::string &address, int port, const std::string &key)
   : _monitor(Platform::instance().createMonitor()) {
   std::ostringstream prefix;
   prefix << type << "-" << address << ":" << port << "-" << key;

   _prefix = prefix.str();
} 

void Logger::log(const std::string& line) {
#ifdef ENABLE_LOGGER
   _monitor->lock();

   std::ostringstream buffer;
   
   buffer << Platform::instance().currentTime()
          << "|[" << _prefix << "]: " << line << std::endl;

   printf("%s", buffer.str().c_str());

   _monitor->unlock();
#endif
} 

void Logger::log(const std::function<void(std::ostream &)> logFunction) {
#ifdef ENABLE_LOGGER
   std::ostringstream line;

   logFunction(line);

   log(line.str());
#endif
} 

Logger::~Logger() {
   delete _monitor;
} 
