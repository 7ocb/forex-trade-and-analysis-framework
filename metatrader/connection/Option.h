#ifndef __8DEB695359B2BBF4FFDAAD51CBA47164_OPTION_H_INCLUDED__
#define __8DEB695359B2BBF4FFDAAD51CBA47164_OPTION_H_INCLUDED__

#include <ostream>

template <class T> class Option {

   void copyFrom(const Option &referenceToCopyFrom) {
      if (referenceToCopyFrom.isDefined()) {
         _t = new T(referenceToCopyFrom.get());
      } else {
         _t = 0;
      } 
   } 
   
public:
   Option(const Option &referenceToCopyFrom) {
      copyFrom(referenceToCopyFrom);
   } 

   void operator=(const Option &referenceToCopyFrom) {
      if (&referenceToCopyFrom == this) return;

      if (_t != 0) delete _t;

      copyFrom(referenceToCopyFrom);
   } 

   Option(const T &t) : _t(new T(t)) { }
   
   Option() : _t(0) { } 
   
   const T &get() const { return *_t; } 

   bool isDefined() const { return _t != 0; }
private:
   T *_t;
};


template <class T> std::ostream &operator<<(std::ostream &str, const Option<T> &opt) {
   if (opt.isDefined()) {
      str << opt.get();
   } else {
      str << "None";
   }

   return str;
} 



#endif 	// __8DEB695359B2BBF4FFDAAD51CBA47164_OPTION_H_INCLUDED__
