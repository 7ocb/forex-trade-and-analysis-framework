#ifndef __9EA460711E4601B02FF255E7D9195508_MTTICKSSINK_H_INCLUDED__
#define __9EA460711E4601B02FF255E7D9195508_MTTICKSSINK_H_INCLUDED__

#include "logger.h"
#include "HubInteraction.h"
#include "RunLoopUser.h"

class MTTicksSink : private RunLoopUser {
public:

   MTTicksSink(RunLoop &runLoop,
               const std::string &address,
               int port,
               const std::string &key);
   
   void sendTick(double bid, double ask);

   ~MTTicksSink();
private:

   void onStartedConnection();

private:

   Logger _logger;

   const std::string _key;

   HubInteraction _hubInteraction;
};

#endif 	// __9EA460711E4601B02FF255E7D9195508_MTTICKSSINK_H_INCLUDED__
