package oknio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset


internal class RealBufferedSource(val source: Source) : BufferedSource {

  val buffer = Buffer()
  var closed: Boolean = false

  override fun buffer(): Buffer {
    return buffer
  }

  override fun read(sink: Buffer, byteCount: Long): Long {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (closed) throw IllegalStateException("closed")
    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1L
    }
    val toRead = Math.min(byteCount, buffer.size)
    return buffer.read(sink, toRead)
  }

  suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (closed) throw IllegalStateException("closed")
    if (buffer.size == 0L) {
      val read = source.aRead(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1L
    }
    val toRead = Math.min(byteCount, buffer.size)
    return buffer.aRead(sink, toRead)
  }

  override fun exhausted(): Boolean {
    if (closed) throw IllegalStateException("closed")
    return buffer.exhausted() && source.read(buffer, Segment.SIZE.toLong()) == -1L
  }

  suspend override fun aExhausted(): Boolean {
    if (closed) throw IllegalStateException("closed")
    return buffer.aExhausted() && source.aRead(buffer, Segment.SIZE.toLong()) == -1L
  }

  override fun require(byteCount: Long) {
    if (!request(byteCount)) throw EOFException()
  }

  suspend override fun aRequire(byteCount: Long) {
    if (!aRequest(byteCount)) throw EOFException()
  }

  override fun request(byteCount: Long): Boolean {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (closed) throw IllegalStateException("closed")
    while (buffer.size < byteCount) {
      if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return false
    }
    return true
  }

  suspend override fun aRequest(byteCount: Long): Boolean {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (closed) throw IllegalStateException("closed")
    while (buffer.size < byteCount) {
      if (source.aRead(buffer, Segment.SIZE.toLong()) == -1L) return false
    }
    return true
  }

  override fun readByte(): Byte {
    require(1L)
    return buffer.readByte()
  }

  suspend override fun aReadByte(): Byte {
    aRequire(1L)
    return buffer.aReadByte()
  }

  override fun readByteString(): ByteString {
    buffer.writeAll(source)
    return buffer.readByteString()
  }

  suspend override fun aReadByteString(): ByteString {
    buffer.aWriteAll(source)
    return buffer.aReadByteString()
  }

  override fun readByteString(byteCount: Long): ByteString {
    require(byteCount)
    return buffer.readByteString(byteCount)
  }

  suspend override fun aReadByteString(byteCount: Long): ByteString {
    aRequire(byteCount)
    return buffer.aReadByteString(byteCount)
  }

  override fun readByteArray(): ByteArray {
    buffer.writeAll(source)
    return buffer.readByteArray()
  }

  suspend override fun aReadByteArray(): ByteArray {
    buffer.aWriteAll(source)
    return buffer.aReadByteArray()
  }

  override fun readByteArray(byteCount: Long): ByteArray {
    require(byteCount)
    return buffer.readByteArray(byteCount)
  }

  suspend override fun aReadByteArray(byteCount: Long): ByteArray {
    aRequire(byteCount)
    return buffer.aReadByteArray(byteCount)
  }

  override fun read(sink: ByteArray): Int {
    return read(sink, 0, sink.size)
  }

  suspend override fun aRead(sink: ByteArray): Int {
    return aRead(sink, 0, sink.size)
  }

  override fun readFully(sink: ByteArray) {
    try {
      require(sink.size.toLong())
    }
    catch (e: EOFException) {
      var offset = 0
      while (buffer.size > 0L) {
        val read = buffer.read(sink, offset, buffer.size.toInt())
        if (read == -1) throw AssertionError()
        offset += read
      }
      throw e
    }
    buffer.readFully(sink)
  }

  suspend override fun aReadFully(sink: ByteArray) {
    try {
      aRequire(sink.size.toLong())
    }
    catch (e: EOFException) {
      var offset = 0
      while (buffer.size > 0L) {
        val read = buffer.aRead(sink, offset, buffer.size.toInt())
        if (read == -1) throw AssertionError()
        offset += read
      }
      throw e
    }
    buffer.aReadFully(sink)
  }

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
    checkOffsetAndCount(sink.size, offset, byteCount)
    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1
    }
    val toRead = Math.min(byteCount.toLong(), buffer.size).toInt()
    return buffer.read(sink, offset, toRead)
  }

  suspend override fun aRead(sink: ByteArray, offset: Int, byteCount: Int): Int {
    checkOffsetAndCount(sink.size, offset, byteCount)
    if (buffer.size == 0L) {
      val read = source.aRead(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1
    }
    val toRead = Math.min(byteCount.toLong(), buffer.size).toInt()
    return buffer.aRead(sink, offset, toRead)
  }

  override fun readFully(sink: Buffer, byteCount: Long) {
    try {
      require(byteCount)
    }
    catch (e: EOFException) {
      sink.writeAll(buffer)
      throw e
    }
    buffer.readFully(sink, byteCount)
  }

  suspend override fun aReadFully(sink: Buffer, byteCount: Long) {
    try {
      aRequire(byteCount)
    }
    catch (e: EOFException) {
      sink.aWriteAll(buffer)
      throw e
    }
    buffer.aReadFully(sink, byteCount)
  }

  override fun readAll(sink: Sink): Long {
    var totalBytesWritten: Long = 0L
    while (source.read(buffer, Segment.SIZE.toLong()) != -1L) {
      val emitByteCount = buffer.completeSegmentByteCount()
      if (emitByteCount > 0L) {
        totalBytesWritten += emitByteCount
        sink.write(buffer, emitByteCount)
      }
    }
    if (buffer.size() > 0L) {
      totalBytesWritten += buffer.size()
      sink.write(buffer, buffer.size())
    }
    return totalBytesWritten
  }

  suspend override fun aReadAll(sink: Sink): Long {
    var totalBytesWritten: Long = 0L
    while (source.aRead(buffer, Segment.SIZE.toLong()) != -1L) {
      val emitByteCount = buffer.completeSegmentByteCount()
      if (emitByteCount > 0L) {
        totalBytesWritten += emitByteCount
        sink.aWrite(buffer, emitByteCount)
      }
    }
    if (buffer.size() > 0L) {
      totalBytesWritten += buffer.size()
      sink.aWrite(buffer, buffer.size())
    }
    return totalBytesWritten
  }

  override fun readUtf8(): String {
    buffer.writeAll(source)
    return buffer.readUtf8()
  }

  suspend override fun aReadUtf8(): String {
    buffer.aWriteAll(source)
    return buffer.aReadUtf8()
  }

  override fun readUtf8(byteCount: Long): String {
    require(byteCount)
    return buffer.readUtf8(byteCount)
  }

  suspend override fun aReadUtf8(byteCount: Long): String {
    aRequire(byteCount)
    return buffer.aReadUtf8(byteCount)
  }

  override fun readString(charset: Charset): String {
    buffer.writeAll(source)
    return buffer.readString(charset)
  }

  suspend override fun aReadString(charset: Charset): String {
    buffer.aWriteAll(source)
    return buffer.aReadString(charset)
  }

  override fun readString(byteCount: Long, charset: Charset): String {
    require(byteCount)
    return buffer.readString(byteCount, charset)
  }

  suspend override fun aReadString(byteCount: Long, charset: Charset): String {
    aRequire(byteCount)
    return buffer.aReadString(byteCount, charset)
  }

  override fun skip(byteCount: Long) {
    var count = byteCount
    if (closed) throw IllegalStateException("closed")
    while (count > 0L) {
      if (buffer.size == 0L && source.read(buffer, Segment.SIZE.toLong()) == -1L) throw EOFException()
      val toSkip = Math.min(count, buffer.size())
      buffer.skip(toSkip)
      count -= toSkip
    }
  }

  suspend override fun aSkip(byteCount: Long) {
    var count = byteCount
    if (closed) throw IllegalStateException("closed")
    while (count > 0L) {
      if (buffer.size == 0L && source.aRead(buffer, Segment.SIZE.toLong()) == -1L) throw EOFException()
      val toSkip = Math.min(count, buffer.size())
      buffer.aSkip(toSkip)
      count -= toSkip
    }
  }

  override fun indexOf(b: Byte): Long {
    return indexOf(b, 0L, Long.MAX_VALUE)
  }

  suspend override fun aIndexOf(b: Byte): Long {
    return aIndexOf(b, 0L, Long.MAX_VALUE)
  }

  override fun indexOf(b: Byte, from: Long): Long {
    return indexOf(b, from, Long.MAX_VALUE)
  }

  suspend override fun aIndexOf(b: Byte, from: Long): Long {
    return aIndexOf(b, from, Long.MAX_VALUE)
  }

  override fun indexOf(b: Byte, from: Long, to: Long): Long {
    if (closed) throw IllegalStateException("closed")
    if (from < 0L || to < from) {
      throw IllegalArgumentException("fromIndex=${from} to=${to}")
    }
    var fromIndex = from
    while (fromIndex < to) {
      val result = buffer.indexOf(b, fromIndex, to)
      if (result != -1L) return result
      val lastBufferSize = buffer.size
      if (lastBufferSize >= to || source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L
      fromIndex = Math.max(fromIndex, lastBufferSize)
    }
    return -1L
  }

  suspend override fun aIndexOf(b: Byte, from: Long, to: Long): Long {
    if (closed) throw IllegalStateException("closed")
    if (from < 0L || to < from) {
      throw IllegalArgumentException("fromIndex=${from} to=${to}")
    }
    var fromIndex = from
    while (fromIndex < to) {
      val result = buffer.aIndexOf(b, fromIndex, to)
      if (result != -1L) return result
      val lastBufferSize = buffer.size
      if (lastBufferSize >= to || source.aRead(buffer, Segment.SIZE.toLong()) == -1L) return -1L
      fromIndex = Math.max(fromIndex, lastBufferSize)
    }
    return -1L
  }

  override fun indexOf(b: ByteString) = indexOf(b, 0L)

  suspend override fun aIndexOf(b: ByteString) = aIndexOf(b, 0L)

  override fun indexOf(b: ByteString, from: Long): Long {
    if (closed) throw IllegalStateException("closed")
    var fromIndex = from
    while (true) {
      val result = buffer.indexOf(b, fromIndex)
      if (result != -1L) return result
      val lastBufferSize = buffer.size
      if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L
      fromIndex = Math.max(fromIndex, lastBufferSize - b.size() + 1L)
    }
  }

  suspend override fun aIndexOf(b: ByteString, from: Long): Long {
    if (closed) throw IllegalStateException("closed")
    var fromIndex = from
    while (true) {
      val result = buffer.aIndexOf(b, fromIndex)
      if (result != -1L) return result
      val lastBufferSize = buffer.size
      if (source.aRead(buffer, Segment.SIZE.toLong()) == -1L) return -1L
      fromIndex = Math.max(fromIndex, lastBufferSize - b.size() + 1L)
    }
  }

  override fun rangeEquals(offset: Long, b: ByteString) = rangeEquals(offset, b, 0, b.size())

  suspend override fun aRangeEquals(offset: Long, b: ByteString) = aRangeEquals(offset, b, 0, b.size())

  override fun rangeEquals(offset: Long, b: ByteString, bOffset: Int, byteCount: Int): Boolean {
    if (closed) throw IllegalStateException("closed")
    if (offset < 0L || bOffset < 0L || byteCount < 0L || b.size() - bOffset < byteCount) return false
    for (i in 0 until byteCount) {
      val bufferOffset = offset + i
      if (!request(bufferOffset + 1L)) return false
      if (buffer.getByte(bufferOffset) != b.getByte(bOffset + i)) return false
    }
    return true
  }

  suspend override fun aRangeEquals(offset: Long, b: ByteString, bOffset: Int, byteCount: Int): Boolean {
    if (closed) throw IllegalStateException("closed")
    if (offset < 0L || bOffset < 0L || byteCount < 0L || b.size() - bOffset < byteCount) return false
    for (i in 0 until byteCount) {
      val bufferOffset = offset + i
      if (!aRequest(bufferOffset + 1L)) return false
      if (buffer.getByte(bufferOffset) != b.getByte(bOffset + i)) return false
    }
    return true
  }

  override fun inputStream(): InputStream {
    return object : InputStream() {
      override fun read(): Int {
        if (closed) throw IOException("closed")
        if (buffer.size == 0L) {
          val count = source.read(buffer, Segment.SIZE.toLong())
          if (count == -1L) return -1
        }
        return buffer.readByte().toInt().and(0xff)
      }
      override fun read(data: ByteArray, offset: Int, byteCount: Int): Int {
        if (closed) throw IOException("closed")
        checkOffsetAndCount(data.size, offset, byteCount)
        if (buffer.size == 0L) {
          val count = source.read(buffer, Segment.SIZE.toLong())
          if (count == -1L) return -1
        }
        return buffer.read(data, offset, byteCount)
      }
      override fun available(): Int {
        if (closed) throw IOException("closed")
        return Math.min(buffer.size, Integer.MAX_VALUE.toLong()).toInt()
      }
      override fun close() {
        this@RealBufferedSource.close()
      }
      override fun toString(): String {
        return this@RealBufferedSource.toString() + ".inputStream()"
      }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    source.close()
    buffer.clear()
  }

  override fun timeout() = source.timeout()

  override fun toString(): String {
    return "buffer(${source})"
  }

}
