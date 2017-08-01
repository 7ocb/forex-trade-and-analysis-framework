package tas.input

import scala.collection.mutable.ListBuffer

import tas.input.format.{
  Reader,
  StorageFormat,
  Writer
}


import tas.utils.IO

import java.io.{
  ByteArrayInputStream,
  File,
  InputStream
}

trait Sequence[Type] extends Traversable[Type] {
  def haveNext:Boolean
  def next:Type

  override def foreach[U](func: Type => U) = {
    while (haveNext) {
      func(next)
    }
  }

  def all():List[Type] = {
    val buffer = new ListBuffer[Type]

    foreach(buffer += _)

    buffer.result
  }
}

class ReaderSequence[Type](reader:Reader[Type]) extends Sequence[Type] {
  private var _next = reader.read

  def haveNext = _next != None
  def next = {
    if (_next == None) {
      throw new RuntimeException("Can't return next, as no next!")
      reader.close
    }

    val toReturn = _next.get

    try {
    _next = reader.read
    } catch {
      case exception:Throwable => {

        reader.close()

        throw exception
      }
    }
    
    toReturn
  }
}

class CachingSequence[Type](slave:Sequence[Type],
                            writer:Writer[Type],
                            finalizeCache:()=>Unit) extends Sequence[Type] {
  def haveNext = {
    val have = slave.haveNext

    if (!have) {
      writer.close()
      finalizeCache()
    }

    have
  }

  def next = {
    val data = slave.next
    writer.write(data)
    data
  }
}


object Sequence {
  def fromString[Type](string:String,
                       format:StorageFormat[Type]) = fromStream(new ByteArrayInputStream(string.getBytes()),
                                                                format)

  def fromFile[Type](originalFile:File,
                     format:StorageFormat[Type],
                     cacheFormat:StorageFormat[Type]):Sequence[Type] = {

    val cacheFileName = originalFile.getPath() + "." + cacheFormat.name
    val tempCacheFileName = cacheFileName + ".temp"

    val cacheFile = new File(cacheFileName)

    if (! originalFile.isFile ) throw new Error("No such file: " + originalFile)

    if (cacheFile.isFile) {
      if (cacheFile.lastModified > originalFile.lastModified) {

        return fromFileNoCache(cacheFile,
                               cacheFormat)

      } else {
        println("Note: cache file found for file " + originalFile + " but it is outdated and will be deleted." )
        cacheFile.delete()
      }
    }

    val tempFile = new File(tempCacheFileName)

    if (tempFile.createNewFile()) {
      println("Note: creating binary cache for file: " + originalFile)
      tempFile.deleteOnExit()

      val cacheStream = IO.fileOutputStream(tempFile)

      return new CachingSequence(fromFileNoCache(originalFile,
                                                 format),
                                 cacheFormat.writer(cacheStream),
                                 () => {
                                   tempFile.renameTo(cacheFile)
                                 })
    }

    println("Warning: cache file not available, but failed to create temporary cache file.")
    return fromFileNoCache(originalFile,
                           format)
  }

  def fromFileNoCache[Type](file:File,
                            format:StorageFormat[Type]) = fromStream(IO.fileInputStream(file),
                                                                     format)



  def fromStream[Type](stream:InputStream,
                       format:StorageFormat[Type]) = {
    new ReaderSequence(format.reader(stream))
  }

}
