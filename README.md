# Forex Trade and Analysis system

Some time ago I was interested in analysis of Forex trading, so I developed this framework.

I'm no more interested in trading myself, so I'm publishing this framework just for case it can be useful to someone.

Provided as is, no guarantees. If you need support, please contact me, via iussues, for example.

## Main features

### Quick simulations

For simple strategy, simulation of year of trading will take several seconds. This allows to perform a lot of simlations to figure out better strategy parameters.

### Probing engine

Probing engine allows to run simulation for different parameter combinations that are specified via command line or configuration file.

### Parallel simulations

This framework was designed with paralleling in mind, so simulation can be simply paralleled to run several probes on several OS processes, or even via network, so even cluster can be used.

### Multipair strategies

As strategy is just state machine taking event sources, this makes it not limited to the one pair. It can operate multiple pairs or any other imaginable event sources in any combinations.

### Trading backends

Simulation is performed by just swapping trading backend - this can be backend pointing to real trading platform, or _simulator_. This means that same strategy code can be used for simulation, tests and trading. 

This allows, for example, next cycle:

1. develop strategy
2. confirm it works as designed by unit-tests
3. confirm that it really works by simulating it on almost instant simulation on data from local storage
4. run it in realtime simulation to see if it behaves as expected in real life
5. put it into actual trading

### Strategy tests

As written above, same code as trades, can be put into tests or simulation. 

### Highlevel JVM language (Scala)

Developed in Scala, can interact with Java and Kotlin code, so wide variety of the libraries can be used, also Java is fairly simple and rich language, comparing, for example, with Metatrader language

### Metatrader integration 

There is Metatrader plugin that allows Metatrader to be used as trading backend, i.e. any tranding platform Metatrader can trade on, this system can trade on



