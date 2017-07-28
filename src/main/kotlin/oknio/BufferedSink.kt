package oknio

import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset


interface BufferedSink: Sink {

  fun buffer(): Buffer

  @Throws(IOException::class)
  fun write(byteString: ByteString): BufferedSink

  @Throws(IOException::class)
  suspend fun aWrite(byteString: ByteString): BufferedSink

  @Throws(IOException::class)
  fun write(source: ByteArray): BufferedSink

  @Throws(IOException::class)
  suspend fun aWrite(source: ByteArray): BufferedSink

  @Throws(IOException::class)
  fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  @Throws(IOException::class)
  suspend fun aWrite(source: ByteArray, offset: Int, byteCount: Int): BufferedSink

  @Throws(IOException::class)
  fun writeAll(source: Source): Long

  @Throws(IOException::class)
  suspend fun aWriteAll(source: Source): Long

  @Throws(IOException::class)
  fun write(source: Source, byteCount: Long): BufferedSink

  @Throws(IOException::class)
  suspend fun aWrite(source: Source, byteCount: Long): BufferedSink

  @Throws(IOException::class)
  fun writeByte(b: Int): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteByte(b: Int): BufferedSink

  @Throws(IOException::class)
  fun writeUtf8(string: String): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteUtf8(string: String): BufferedSink

  @Throws(IOException::class)
  fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink

  @Throws(IOException::class)
  fun writeUtf8CodePoint(codePoint: Int): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteUtf8CodePoint(codePoint: Int): BufferedSink

  @Throws(IOException::class)
  fun writeString(string: String, charset: Charset): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteString(string: String, charset: Charset): BufferedSink

  @Throws(IOException::class)
  fun writeString(string: String, beginIndex: Int, endIndex: Int, charset: Charset): BufferedSink

  @Throws(IOException::class)
  suspend fun aWriteString(string: String, beginIndex: Int, endIndex: Int, charset: Charset): BufferedSink


  @Throws(IOException::class)
  fun emit(): BufferedSink

  @Throws(IOException::class)
  suspend fun aEmit(): BufferedSink

  @Throws(IOException::class)
  fun emitCompleteSegments(): BufferedSink

  @Throws(IOException::class)
  suspend fun aEmitCompleteSegments(): BufferedSink

  @Throws(IOException::class)
  fun outputStream(): OutputStream

}
