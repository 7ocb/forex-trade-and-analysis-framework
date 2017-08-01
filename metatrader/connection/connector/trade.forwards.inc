// -*- mode: c++ -*-

TRADE_FORWARD_CALL(StartNextTradesIteration)
TRADE_FORWARD_GET (bool, ShiftToNextTrade)

TRADE_FORWARD_SET (double, UpdateBalance)
TRADE_FORWARD_SET (double, UpdateEquity)

TRADE_FORWARD_CALL(FreeTrade)
TRADE_FORWARD_CALL(TradeNotifyOpened)
TRADE_FORWARD_CALL(TradeNotifyClosed)


TRADE_FORWARD_STRING(LogTradeConnectorMessage);
TRADE_FORWARD_STRING(TradeMessage);

