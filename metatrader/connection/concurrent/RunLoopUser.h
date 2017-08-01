#ifndef __EFE0CC5B67BB01DBAB1AC009E6FFE5C5_RUNLOOPUSER_H_INCLUDED__
#define __EFE0CC5B67BB01DBAB1AC009E6FFE5C5_RUNLOOPUSER_H_INCLUDED__

#include "RunLoop.h"
#include "platform.h"
#include <list>

class RunLoopUser {
public:
   RunLoopUser(RunLoop &loop);

   void post(const RunLoop::Action& action);

   void postDelayed(Platform::Milliseconds delay,
                    const RunLoop::Action &action);

   RunLoop &runLoop() { return _loop; }
   
   ~RunLoopUser();
private:
   RunLoop &_loop;
   std::list<RunLoop::Task *> _postedTasks;
   Monitor *_synchronization;
};

#endif 	// __EFE0CC5B67BB01DBAB1AC009E6FFE5C5_RUNLOOPUSER_H_INCLUDED__
