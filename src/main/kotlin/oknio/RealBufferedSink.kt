package oknio

import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset

internal class RealBufferedSink(val sink: Sink) : BufferedSink {

  val buffer = Buffer()
  var closed: Boolean = false

  override fun buffer() = buffer

  override fun write(source: Buffer, byteCount: Long) {
    if (closed) throw IllegalStateException("closed")
    buffer.write(source, byteCount)
    emitCompleteSegments()
  }

  suspend override fun aWrite(source: Buffer, byteCount: Long) {
    if (closed) throw IllegalStateException("closed")
    buffer.aWrite(source, byteCount)
    aEmitCompleteSegments()
  }

  override fun write(byteString: ByteString): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.write(byteString)
    return emitCompleteSegments()
  }

  suspend override fun aWrite(byteString: ByteString): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWrite(byteString)
    return aEmitCompleteSegments()
  }

  override fun writeUtf8(string: String): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeUtf8(string)
    return emitCompleteSegments()
  }

  suspend override fun aWriteUtf8(string: String): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteUtf8(string)
    return aEmitCompleteSegments()
  }

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeUtf8(string, beginIndex, endIndex)
    return emitCompleteSegments()
  }

  suspend override fun aWriteUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteUtf8(string, beginIndex, endIndex)
    return aEmitCompleteSegments()
  }

  override fun writeUtf8CodePoint(codePoint: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeUtf8CodePoint(codePoint)
    return emitCompleteSegments()
  }

  suspend override fun aWriteUtf8CodePoint(codePoint: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteUtf8CodePoint(codePoint)
    return aEmitCompleteSegments()
  }

  override fun writeString(string: String, charset: Charset): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeString(string, charset)
    return emitCompleteSegments()
  }

  suspend override fun aWriteString(string: String, charset: Charset): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteString(string, charset)
    return aEmitCompleteSegments()
  }

  override fun writeString(string: String, beginIndex: Int, endIndex: Int,
                           charset: Charset): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeString(string, beginIndex, endIndex, charset)
    return emitCompleteSegments()
  }

  suspend override fun aWriteString(string: String, beginIndex: Int, endIndex: Int,
                                    charset: Charset): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteString(string, beginIndex, endIndex, charset)
    return aEmitCompleteSegments()
  }

  override fun write(source: ByteArray): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.write(source)
    return emitCompleteSegments()
  }

  suspend override fun aWrite(source: ByteArray): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWrite(source)
    return aEmitCompleteSegments()
  }

  override fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.write(source, offset, byteCount)
    return emitCompleteSegments()
  }

  suspend override fun aWrite(source: ByteArray, offset: Int, byteCount: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWrite(source, offset, byteCount)
    return aEmitCompleteSegments()
  }

  override fun writeAll(source: Source): Long {
    var totalBytesRead: Long = 0L
    var readCount: Long
    while (true) {
      readCount = source.read(buffer, Segment.SIZE.toLong())
      if (readCount == -1L) return totalBytesRead
      totalBytesRead += readCount
      emitCompleteSegments()
    }
  }

  suspend override fun aWriteAll(source: Source): Long {
    var totalBytesRead: Long = 0L
    var readCount: Long
    while (true) {
      readCount = source.aRead(buffer, Segment.SIZE.toLong())
      if (readCount == -1L) return totalBytesRead
      totalBytesRead += readCount
      aEmitCompleteSegments()
    }
  }

  override fun write(source: Source, byteCount: Long): BufferedSink {
    var count = byteCount
    while (count > 0L) {
      val read = source.read(buffer, count)
      if (read == -1L) throw EOFException()
      count -= read
      emitCompleteSegments()
    }
    return this
  }

  suspend override fun aWrite(source: Source, byteCount: Long): BufferedSink {
    var count = byteCount
    while (count > 0L) {
      val read = source.aRead(buffer, count)
      if (read == -1L) throw EOFException()
      count -= read
      aEmitCompleteSegments()
    }
    return this
  }

  override fun writeByte(b: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.writeByte(b)
    return emitCompleteSegments()
  }

  suspend override fun aWriteByte(b: Int): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    buffer.aWriteByte(b)
    return aEmitCompleteSegments()
  }

  override fun emitCompleteSegments(): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    val byteCount = buffer.completeSegmentByteCount()
    if (byteCount > 0L) sink.write(buffer, byteCount)
    return this
  }

  suspend override fun aEmitCompleteSegments(): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    val byteCount = buffer.completeSegmentByteCount()
    if (byteCount > 0L) sink.aWrite(buffer, byteCount)
    return this
  }

  override fun emit(): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    val byteCount = buffer.size()
    if (byteCount > 0L) sink.write(buffer, byteCount)
    return this
  }

  suspend override fun aEmit(): BufferedSink {
    if (closed) throw IllegalStateException("closed")
    val byteCount = buffer.size()
    if (byteCount > 0L) sink.aWrite(buffer, byteCount)
    return this
  }

  override fun outputStream(): OutputStream {
    return object : OutputStream() {
      override fun write(b: Int) {
        if (closed) throw IOException("closed")
        buffer.writeByte(b.toByte().toInt())
        emitCompleteSegments()
      }
      override fun write(data: ByteArray, offset: Int, byteCount: Int) {
        if (closed) throw IOException("closed")
        buffer.write(data, offset, byteCount)
        emitCompleteSegments()
      }
      override fun flush() {
        if (!closed) this@RealBufferedSink.flush()
      }
      override fun close() {
        this@RealBufferedSink.close()
      }
      override fun toString(): String {
        return this@RealBufferedSink.toString() + ".outputStream()"
      }
    }
  }

  override fun flush() {
    if (closed) throw IllegalStateException("closed")
    if (buffer.size > 0L) {
      sink.write(buffer, buffer.size)
    }
    sink.flush()
  }

  suspend override fun aFlush() {
    if (closed) throw IllegalStateException("closed")
    if (buffer.size > 0L) {
      sink.aWrite(buffer, buffer.size)
    }
    sink.aFlush()
  }

  override fun close() {
    if (closed) return
    var thrown: Throwable? = null
    try {
      if (buffer.size > 0L) {
        sink.write(buffer, buffer.size)
      }
    }
    catch (e: Throwable) {
      thrown = e
    }
    try {
      sink.close()
    }
    catch (e: Throwable) {
      throw (thrown ?: e)
    }
    finally {
      closed = true
    }
    if (thrown != null) throw thrown
  }

  suspend override fun aClose() {
    if (closed) return
    var thrown: Throwable? = null
    try {
      if (buffer.size > 0L) {
        sink.aWrite(buffer, buffer.size)
      }
    }
    catch (e: Throwable) {
      thrown = e
    }
    try {
      sink.aClose()
    }
    catch (e: Throwable) {
      throw (thrown ?: e)
    }
    finally {
      closed = true
    }
    if (thrown != null) throw thrown
  }

  override fun timeout(): Timeout {
    return sink.timeout()
  }

  override fun toString(): String {
    return "buffer($sink)"
  }

}
