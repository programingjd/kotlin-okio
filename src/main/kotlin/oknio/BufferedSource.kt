package oknio

import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset


interface BufferedSource: Source {

  fun buffer(): Buffer

  @Throws(IOException::class)
  fun exhausted(): Boolean

  @Throws(IOException::class)
  suspend fun aExhausted(): Boolean

  @Throws(IOException::class)
  fun require(byteCount: Long)

  @Throws(IOException::class)
  suspend fun aRequire(byteCount: Long)

  @Throws(IOException::class)
  fun request(byteCount: Long): Boolean

  @Throws(IOException::class)
  suspend fun aRequest(byteCount: Long): Boolean

  @Throws(IOException::class)
  fun readByte(): Byte

  @Throws(IOException::class)
  suspend fun aReadByte(): Byte

  @Throws(IOException::class)
  fun skip(byteCount: Long)

  @Throws(IOException::class)
  suspend fun aSkip(byteCount: Long)

  @Throws(IOException::class)
  fun readByteString(): ByteString

  @Throws(IOException::class)
  suspend fun aReadByteString(): ByteString

  @Throws(IOException::class)
  fun readByteString(byteCount: Long): ByteString

  @Throws(IOException::class)
  suspend fun aReadByteString(byteCount: Long): ByteString

  @Throws(IOException::class)
  fun readByteArray(): ByteArray

  @Throws(IOException::class)
  suspend fun aReadByteArray(): ByteArray

  @Throws(IOException::class)
  fun readByteArray(byteCount: Long): ByteArray

  @Throws(IOException::class)
  suspend fun aReadByteArray(byteCount: Long): ByteArray

  @Throws(IOException::class)
  fun readUtf8(): String

  @Throws(IOException::class)
  suspend fun aReadUtf8(): String

  @Throws(IOException::class)
  fun readUtf8(byteCount: Long): String

  @Throws(IOException::class)
  suspend fun aReadUtf8(byteCount: Long): String

  @Throws(IOException::class)
  fun readString(charset: Charset): String

  @Throws(IOException::class)
  suspend fun aReadString(charset: Charset): String

  @Throws(IOException::class)
  fun readString(byteCount: Long, charset: Charset): String

  @Throws(IOException::class)
  suspend fun aReadString(byteCount: Long, charset: Charset): String

  @Throws(IOException::class)
  fun read(sink: ByteArray): Int

  @Throws(IOException::class)
  suspend fun aRead(sink: ByteArray): Int

  @Throws(IOException::class)
  fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  @Throws(IOException::class)
  suspend fun aRead(sink: ByteArray, offset: Int, byteCount: Int): Int

  @Throws(IOException::class)
  fun readFully(sink: ByteArray)

  @Throws(IOException::class)
  suspend fun aReadFully(sink: ByteArray)

  @Throws(IOException::class)
  fun readFully(sink: Buffer, byteCount: Long)

  @Throws(IOException::class)
  suspend fun aReadFully(sink: Buffer, byteCount: Long)

  @Throws(IOException::class)
  fun readAll(sink: Sink): Long

  @Throws(IOException::class)
  suspend fun aReadAll(sink: Sink): Long

  @Throws(IOException::class)
  fun indexOf(b: Byte): Long

  @Throws(IOException::class)
  suspend fun aIndexOf(b: Byte): Long

  @Throws(IOException::class)
  fun indexOf(b: Byte, from: Long): Long

  @Throws(IOException::class)
  suspend fun aIndexOf(b: Byte, from: Long): Long

  @Throws(IOException::class)
  fun indexOf(b: Byte, from: Long, to: Long): Long

  @Throws(IOException::class)
  suspend fun aIndexOf(b: Byte, from: Long, to: Long): Long

  fun inputStream(): InputStream

}
