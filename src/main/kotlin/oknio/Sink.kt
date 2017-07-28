package oknio

import java.io.Closeable
import java.io.Flushable
import java.io.IOException


interface Sink: Closeable, Flushable {

  @Throws(IOException::class)
  fun write(source: Buffer, byteCount: Long)

  @Throws(IOException::class)
  suspend fun aWrite(source: Buffer, byteCount: Long)

  @Throws(IOException::class)
  suspend fun aFlush()

  @Throws(IOException::class)
  suspend fun aClose()

  fun timeout(): Timeout

}
