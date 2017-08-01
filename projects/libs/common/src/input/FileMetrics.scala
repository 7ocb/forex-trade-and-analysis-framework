package tas.input

import tas.types.{
  Time,
  Interval,
  Period
}

import scala.collection.mutable.HashMap

import java.io.{
  File,
  InputStream
}

import tas.input.format.StorageFormat


private class FileMetricsCache[MetricsType](val lastModified:Long,
                                            val metrics:MetricsType)

abstract class FileMetrics[DataType, MetricsType] {

  private val cachedMetrics = new HashMap[String, FileMetricsCache[MetricsType]]

  def format:StorageFormat[DataType]
  def cacheFormat:StorageFormat[DataType]
  
  def fromString(str:String):MetricsType =
    fromSequence(Sequence.fromString(str, format))

  def fromFile(fileName:String):MetricsType = {

    val cached = cachedMetrics.get(fileName)

    if (cached.isEmpty) forceFromFile(fileName)
    else {
      val metrics = cached.get
      val file = new File(fileName)
      if (metrics.lastModified < file.lastModified()) forceFromFile(fileName)
      else metrics.metrics
    }
  }

  def fromFile(file:File):MetricsType = fromFile(file.getPath())

  private def forceFromFile(fileName:String) = {

    val file = new File(fileName)

    val metrics = fromSequence(Sequence.fromFile(file, format, cacheFormat))

    cachedMetrics.put(fileName, new FileMetricsCache(file.lastModified(),
                                                     metrics))

    metrics
  }
  
  def fromStream(stream:InputStream):MetricsType =
    fromSequence(Sequence.fromStream(stream,
                                     format))

  def fromSequence(seq:Sequence[DataType]):MetricsType

}
