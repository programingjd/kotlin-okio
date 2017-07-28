package oknio

import java.io.Closeable
import java.io.IOException

interface Source: Closeable {

  @Throws(IOException::class)
  fun read(sink: Buffer, byteCount: Long): Long

  @Throws(IOException::class)
  suspend fun aRead(sink: Buffer, byteCount: Long): Long

}
