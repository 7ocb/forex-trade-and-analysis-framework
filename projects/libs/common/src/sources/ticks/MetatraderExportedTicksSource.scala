package tas.sources.ticks

import tas.types.{
  Fraction,
  Time,
  Interval,
  TimedTick,
  Price
}

import tas.concurrency.RunLoop
import tas.timers.Timer

import tas.output.logger.Logger

import tas.utils.files.lined.LinedFileReader

import tas.utils.IO

import tas.events.Event

import tas.input.format.ticks.metatrader.MetatraderExportedTicks

import tas.sources.ticks.utils.{
  SimulatedTicksDispatcher
}

import java.io.{
  File,
  FileInputStream
}


object MetatraderExportedTicksSource {
  private val NoTicks = List[TimedTick]()
  private val EofMarker = "EOF"
}

class MetatraderExportedTicksSource(runLoop:RunLoop,
                                    timer:Timer,
                                    logger:Logger,
                                    stepInterval:Interval,
                                    spread:Fraction,
                                    exportDirectory:File) extends TickSource {

  if (!exportDirectory.isDirectory) throw new IllegalArgumentException("export directory is not a directory")

  private val _processedStorage = new File(exportDirectory,
                                           "processed")

  private val _tickEvent = Event.newSync[Price]

  loadTicksFromExportDirectory()

  private def performStep():Unit = {

    val ticks = loadTicksFromExportDirectory()

    if ( ! ticks.isEmpty ) {
      new SimulatedTicksDispatcher(timer,
                                   simulatedTicks(ticks),
                                   tick => _tickEvent << tick)
    }

    postStep()

  }

  def postStep() = runLoop.postDelayed(stepInterval,
                                       performStep _)

  postStep()

  private def loadTicksFromExportDirectory() =
    exportDirectory
      .listFiles()
      .sortWith(_.getName < _.getName)
      .flatMap(extractAndMoveIfCompleted)
      .toList

  private def simulatedTicks(ticks:List[TimedTick]):List[TimedTick] = {
    var time = timer.currentTime
    val increment = stepInterval / (ticks.size + 1)

    ticks.map(tick => {
                time += increment
                new TimedTick(time, tick.price)
              } )
  }

  private def extractAndMoveIfCompleted(file:File):List[TimedTick] = {

    import MetatraderExportedTicksSource.NoTicks
    import MetatraderExportedTicksSource.EofMarker

    if (! file.isFile) return NoTicks

    val contents = IO.withStream(new FileInputStream(file),
                                 LinedFileReader.read)

    val lines = contents.significantLines

    if (lines.size == 0) return NoTicks

    val completed = lines.last.trim == EofMarker

    if (! completed) return NoTicks

    def extractTick(line:String) = {
      val tick = MetatraderExportedTicks.parseLine(line)

      if (tick == None) {
        logger.log("Warning, " + file + " contained bad price line: " + line)
      }

      tick
    }

    val resultTicks = lines.dropRight(1).map(extractTick).filterNot(_ == None).map(_.get)

    moveToProcessed(file)

    return resultTicks.map(_.tick(spread))
  }

  private def moveToProcessed(file:File) = {
    if (! _processedStorage.isDirectory) {
      val created = _processedStorage.mkdirs()

      if (!created) {
        throw new RuntimeException("failed to create processed storage: " + _processedStorage)
      }
    }

    val targetFile = new File(_processedStorage,
                              file.getName())

    if (targetFile.exists) throw new RuntimeException("can't copy processed file to processed storage, file exists already: " + targetFile)

    val renamed = file.renameTo(targetFile)

    if (!renamed) throw new RuntimeException("failed to move file to processed storage: " + targetFile)
  }

  
  def tickEvent:Event[Price] = _tickEvent

  override def toString:String = "metatrader ticks from: " + exportDirectory
}
