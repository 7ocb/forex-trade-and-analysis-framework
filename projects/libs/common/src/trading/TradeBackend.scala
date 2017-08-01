package tas.trading
import tas.types.Fraction

import tas.Bound

import tas.events.{
  Subscription,
  SyncCallSubscription
}

trait AccountInformationKeeper {
  protected val _balanceMayBeChanged = new SyncCallSubscription
  protected val _equityMayBeChanged  = new SyncCallSubscription

  def balanceMayBeChanged:Subscription[()=>Unit] = _balanceMayBeChanged
  def balance:Fraction

  def equityMayBeChanged:Subscription[()=>Unit] = _equityMayBeChanged
  def equity:Fraction
}

trait TradeBackend extends AccountInformationKeeper {
  def newTradeExecutor(request:TradeRequest):(TradeExecutor with Bound)
} 
