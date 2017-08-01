#include "RunLoopUser.h"

class RunLoopUserTask {
public:
   RunLoopUserTask(std::list<RunLoop::Task *> &postedTasks,
                   Monitor &synchronization,
                   const RunLoop::Action& action)
      : _postedTasks(postedTasks)
      , _synchronization(synchronization)
      , _action(action)
      , _task(nullptr) {
      
   }

   void setCancelHandle(RunLoop::Task *task) {
      _task = task;
   } 

   void operator()() {

      _synchronization.lock();
      _postedTasks.remove(_task);
      _synchronization.unlock();

      _action();
   } 
   
private:
   std::list<RunLoop::Task *> &_postedTasks;
   Monitor &_synchronization;
   const RunLoop::Action _action;
   RunLoop::Task *_task;
};

RunLoopUser::RunLoopUser(RunLoop &loop)
   : _loop(loop)
   , _synchronization(Platform::instance().createMonitor()) {
   
} 

void RunLoopUser::post(const RunLoop::Action& action) {
   RunLoopUserTask task(_postedTasks,
                        *_synchronization,
                        action);
   
   RunLoop::Task *cancelHandle
      = _loop.post(task);

   _synchronization->lock();
   _postedTasks.push_back(cancelHandle);

   task.setCancelHandle(cancelHandle);
   _synchronization->unlock();
} 

void RunLoopUser::postDelayed(Platform::Milliseconds delay,
                              const RunLoop::Action &action) {
   RunLoopUserTask task(_postedTasks,
                        *_synchronization,
                        action);
   
   RunLoop::Task *cancelHandle
      = _loop.postDelayed(delay,
                          task);

   _synchronization->lock();
   _postedTasks.push_back(cancelHandle);

   task.setCancelHandle(cancelHandle);
   _synchronization->unlock();
} 
   
RunLoopUser::~RunLoopUser() {
   for (RunLoop::Task *task : _postedTasks) {
      _loop.cancel(task);
   }

   _postedTasks.clear();

   delete _synchronization;
}

