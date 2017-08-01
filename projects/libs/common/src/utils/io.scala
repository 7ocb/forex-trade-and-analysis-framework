package tas.utils

import java.net.URL
import java.io.{
  FileWriter,
  BufferedWriter,
  InputStream,
  OutputStream,
  FileInputStream,
  FileOutputStream,
  BufferedInputStream,
  BufferedOutputStream,
  File,
  Closeable
}

import tas.types.Interval

import scala.annotation.tailrec

import scala.annotation.tailrec
  
object IO {
  val ReadingTimeout = Interval.seconds(30)

  def fileInputStream(file:File):InputStream = {
    if ( ! file.isFile ) throw new Error("Trying to open \"" + file + "\" as file, while it is not file!")

    new BufferedInputStream(new FileInputStream(file))
  }

  def fileInputStream(filePath:String):InputStream = 
    fileInputStream(new File(filePath))

  def urlInputStream(url:String):InputStream = {
    val connection = new URL(url).openConnection()
    connection.setReadTimeout(ReadingTimeout.milliseconds.asInstanceOf[Int])
    connection.getInputStream()
  }

  def fileOutputStream(file:File):OutputStream = 
    new BufferedOutputStream(new FileOutputStream(file))


  def withStream[ReturnType, Stream <: Closeable](stream:Stream, code:Stream=>ReturnType):ReturnType = {
    try {
      code(stream)
    } finally {
      stream.close()
    } 
  }

  def fileWriter(filePath:String):BufferedWriter = {
    new BufferedWriter(new FileWriter(filePath))
  }

  def readAll(stream:InputStream):String = {
    var output = Array[Byte]()

    val reader = new java.io.InputStreamReader(stream)
    val builder = new StringBuilder()

    val bufferSize = 10000
    val buffer = new Array[Char](bufferSize)

    @tailrec def readAll:Unit = {
      val readCount = reader.read(buffer, 0, bufferSize)
      if (readCount >= 0) {
        builder.append(new String(buffer, 0, readCount))
        readAll
      }
    }

    readAll

    builder.toString
  }

  def readAllBuffer(input:InputStream,
                    buffer:Array[Byte]):Unit = {
    var offset = 0
    var leftToRead = buffer.length

    @tailrec def readLoop:Unit = {

      if (leftToRead > 0) {
        val read = input.read(buffer,
                              offset,
                              leftToRead)

        if (read > 0) {
          offset += read
          leftToRead -= read

          readLoop
        }
      }
    }

    readLoop
  }

}   
