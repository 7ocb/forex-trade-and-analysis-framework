
import tas.utils.HelpTarget

class HelpImpl extends HelpTarget("previous day direction enter strategy",
                                  List("Probes - this is target for starting history-based trading simulation\nand parameter probing",
                                       "CollectStatistics - performs parameter probing, but instead of simulating, \nit collects statistics based on history data.",
                                       "RealtimeRunFinam - run realtime simulation, using ticks generated from \nonline finam history.",
                                       "RealtimeRunMetatrader - run realtime simulation, using ticks exported \nfrom metatrader."))

object Help extends HelpImpl
object help extends HelpImpl 
