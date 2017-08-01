package testing.sources

import org.scalatest.FlatSpec

import tas.sources.finam.FinamUrl
import tas.sources.finam.Parameters

import tas.types.Time

class FinamDataFeedTests extends FlatSpec {

  "date" should "be created from time" in {
    
    assert(Parameters.dateFromTime(Time.fromCalendar(2013, 1, 13)) === new Parameters.Date(2013, 0, 13))
  } 
  
  "url formatter" should "format url" in {

    val expected = "http://195.128.78.52/EURUSD_130214_130216.txt?market=5&em=83&code=EURUSD&df=14&mf=1&yf=2013&dt=16&mt=1&yt=2013&p=2&f=EURUSD_130214_130216&e=.txt&cn=EURUSD&dtf=1&tmf=1&MSOR=0&mstimever=0&sep=5&sep2=1&datf=2&at=1"
  
    assert(expected === FinamUrl(Parameters.EUR_USD,
                                 Parameters.Min1,
                                 new Parameters.Date(2013, 1, 14),
                                 new Parameters.Date(2013, 1, 16)))
  } 
} 
