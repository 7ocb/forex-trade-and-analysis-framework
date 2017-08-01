#ifndef __2B5A6FE3DD5B95658C72A83EC7713EEE_THREAD_H_INCLUDED__
#define __2B5A6FE3DD5B95658C72A83EC7713EEE_THREAD_H_INCLUDED__

#include <string>
#include <functional>

class Thread {
public:
   typedef std::function<void()> Action;

   Thread(const Action &action) : _action(action) {}
   
   virtual ~Thread() {}

   static void joinAndDelete(Thread * &thread);

   static std::string threadSafeCopy(const std::string& original);

protected:
   static void callAction(Thread *self);
   
private:
   const Action _action;
};


#endif 	// __2B5A6FE3DD5B95658C72A83EC7713EEE_THREAD_H_INCLUDED__
