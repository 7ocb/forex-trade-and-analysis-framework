#include <windows.h>

#include "MTConnector.h"
#include "WinPlatform.h"
#include <stdio.h>

bool isUnicode;
MTConnector *mtConnector;

extern "C" BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {

   switch (fdwReason) {
      case DLL_PROCESS_ATTACH:
         Platform::init(new WinPlatform());
         mtConnector = new MTConnector();
         break;

      case DLL_PROCESS_DETACH:
         delete mtConnector;
         Platform::cleanup();
         break;
   } 

   return TRUE;
} 

extern "C" void CalibrateStrings(const char *key) {
   isUnicode = strcmp(key, "string") != 0;
   printf("CalibrateStrings: is unicode: %d\n", isUnicode);
} 

std::string ensureUtf8(const char *string) {
   if (isUnicode) {
      // convert from unicode-16 to utf-8

      const int count = WideCharToMultiByte(CP_UTF8,
                                            0,
                                            (const wchar_t*)string,
                                            -1,
                                            nullptr,
                                            0,
                                            0,
                                            0);


      char *buffer = new char[count];

      WideCharToMultiByte(CP_UTF8,
                          0,
                          (const wchar_t*)string,
                          -1,
                          buffer,
                          count,
                          0,
                          0);

      const std::string output = std::string(buffer);

      delete buffer;
      
      return output;
   } else return std::string(string);
}

extern "C" void SendTickToSink(int id, double bid, double ask) {
   mtConnector->sendTick(id, bid, ask);
} 

extern "C" int CreateTicksSink(const char *address, int port, const char *key) {
   if (address == NULL || key == NULL) return -1;
   
   return mtConnector->createTicksSink(ensureUtf8(address),
                                       port,
                                       ensureUtf8(key));
}

extern "C" void FreeTicksSink(int id) {
   mtConnector->freeTicksSink(id);
}

extern "C" int CreateTradeConnector(const char *address,
                                    int port,
                                    const char *key,
                                    double balance,
                                    double equity) {
   if (address == NULL || key == NULL) return -1;

   return mtConnector->createTradeConnector(ensureUtf8(address),
                                            port,
                                            ensureUtf8(key),
                                            balance,
                                            equity);
}

extern "C" void FreeTradeConnector(int id) {
   mtConnector->freeTradeConnector(id);
}

 

#define TRADE_FORWARD_CALL(name)                \
   extern "C" void name(int id) {               \
      mtConnector->name(id);                    \
   }

#define TRADE_FORWARD_GET(type, name)           \
   extern "C" type name(int id) {               \
      return mtConnector->name(id);             \
   }

#define TRADE_FORWARD_SET(type, name)           \
   extern "C" void name(int id, type value) {   \
      return mtConnector->name(id, value);      \
   }

#define TRADE_FORWARD_STRING(name)                              \
   extern "C" void name(int id, const char *value) {   \
      return mtConnector->name(id, ensureUtf8(value));          \
   }

#define FORWARD_CURRENT_TRADE_GET(type, name)   \
   extern "C" type TradeGet##name(int id) {     \
      return mtConnector->TradeGet##name(id);   \
   }

#define FORWARD_CURRENT_TRADE_SET(type, name)           \
   extern "C" void TradeSet##name(int id, type value) { \
      return mtConnector->TradeSet##name(id, value);    \
   }

#define FORWARD_CURRENT_TRADE_GET_BOUNDARY(name)        \
   extern "C" double TradeGet##name(int id) {           \
      return mtConnector->TradeGet##name(id).value();   \
   }

#define FORWARD_CURRENT_TRADE_SET_BOUNDARY(name)                \
   extern "C" void TradeSet##name(int id, double value) {       \
      mtConnector->TradeSet##name(id, Boundary(value));         \
   }


#define FORWARD_CURRENT_TRADE_GET_OPT_BOUNDARY(name)            \
   extern "C" double TradeGet##name(int id) {                   \
      auto opt = mtConnector->TradeGet##name(id);               \
         if (opt.isDefined()) return opt.get().value();         \
         else return 0;                                         \
   }

#define FORWARD_CURRENT_TRADE_SET_OPT_BOUNDARY(name)                    \
   extern "C" void TradeSet##name(int id, double value) {               \
      if (value == 0) mtConnector->TradeSet##name(id, Option<Boundary>()); \
         else            mtConnector->TradeSet##name(id, Option<Boundary>(value)); \
   }

#include "trade.forwards.inc"
#include "current.trade.access.inc"

