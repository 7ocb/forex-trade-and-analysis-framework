#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_NIXPLATFORM_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_NIXPLATFORM_H_INCLUDED__

#include "platform.h"

class NixPlatform : public Platform {
   NixPlatform(const NixPlatform &referenceToCopyFrom);
   void operator=(const NixPlatform &referenceToCopyFrom);
public:
   NixPlatform();

   virtual Socket *createSocket();
   
   virtual Monitor *createMonitor();

   virtual Thread *createThread(const Thread::Action &action);

   virtual Milliseconds currentTime() override;

   virtual void sleep(const Milliseconds time) override;

   virtual int htonl(int );
   virtual uint64 htonll(uint64 );

   virtual int ntohl(int );
   virtual uint64 ntohll(uint64 );

   virtual ~NixPlatform();

private:
   
};

#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_NIXPLATFORM_H_INCLUDED__
