package tas.trading.simulation.config

import tas.types.Fraction

import tas.trading.simulation.config.limits.Limits

case class Config(leverage:Fraction,
                  initialBalance:Fraction,
                  openAndCloseBy:OpenAndCloseBy,
                  limits:Limits,
                  comission:Comission)
