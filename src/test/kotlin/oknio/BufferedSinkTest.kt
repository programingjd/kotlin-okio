package oknio

import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.EOFException
import java.nio.charset.Charset

class BufferedSinkTest {

  internal interface Factory {
    fun create(data: Buffer): BufferedSink

    companion object {
      val BUFFER = object: Factory {
        override fun create(data: Buffer) = data
      }
      val REAL_BUFFERED_SINK = object: Factory {
        override fun create(data: Buffer) = RealBufferedSink(data)
      }
    }
  }

  internal val factories = listOf(Factory.BUFFER, Factory.REAL_BUFFERED_SINK)

  @Test
  fun writeNothing() {
    forEachFactory { data, sink ->
      sink.writeUtf8("")
      sink.flush()
      assertEquals(0L, data.size())
    }
  }

  @Test
  fun aWriteNothing() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteUtf8("")
        sink.aFlush()
        assertEquals(0L, data.size())
      }
    }
  }

  @Test
  fun writeBytes() {
    forEachFactory { data, sink ->
      sink.writeByte(0xab)
      sink.writeByte(0xcd)
      sink.flush()
      assertEquals("[hex=abcd]", data.toString())
    }
  }

  @Test
  fun aWriteBytes() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteByte(0xab)
        sink.aWriteByte(0xcd)
        sink.aFlush()
        assertEquals("[hex=abcd]", data.toString())
      }
    }
  }

  @Test
  fun writeLastByteInSegment() {
    forEachFactory { data, sink ->
      sink.writeUtf8(repeat('a', Segment.SIZE - 1))
      sink.writeByte(0x20)
      sink.writeByte(0x21)
      sink.flush()
      assertEquals(listOf(Segment.SIZE, 1), data.segmentSizes())
      assertEquals(repeat('a', Segment.SIZE - 1), data.readUtf8(Segment.SIZE - 1L))
      assertEquals("[text= !]", data.toString())
    }
  }

  @Test
  fun aWriteLastByteInSegment() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteUtf8(repeat('a', Segment.SIZE - 1))
        sink.aWriteByte(0x20)
        sink.aWriteByte(0x21)
        sink.aFlush()
        assertEquals(listOf(Segment.SIZE, 1), data.segmentSizes())
        assertEquals(repeat('a', Segment.SIZE - 1), data.aReadUtf8(Segment.SIZE - 1L))
        assertEquals("[text= !]", data.toString())
      }
    }
  }

  @Test
  fun writeStringUtf8() {
    forEachFactory { data, sink ->
      sink.writeUtf8("təˈranəˌsôr")
      sink.flush()
      assertEquals(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"), data.readByteString())
    }
  }

  @Test
  fun aWriteStringUtf8() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteUtf8("təˈranəˌsôr")
        sink.aFlush()
        assertEquals(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"), data.aReadByteString())
      }
    }
  }

  @Test
  fun writeSubstringUtf8() {
    forEachFactory { data, sink ->
      sink.writeUtf8("təˈranəˌsôr", 3, 7)
      sink.flush()
      assertEquals(ByteString.decodeHex("72616ec999"), data.readByteString())
    }
  }

  @Test
  fun aWriteSubstringUtf8() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteUtf8("təˈranəˌsôr", 3, 7)
        sink.aFlush()
        assertEquals(ByteString.decodeHex("72616ec999"), data.aReadByteString())
      }
    }
  }

  @Test
  fun writeStringWithCharset() {
    forEachFactory { data, sink ->
      sink.writeString("təˈranəˌsôr", Charset.forName("utf-32be"))
      sink.flush()
      assertEquals(
        ByteString.decodeHex(
          "0000007400000259000002c800000072000000610000006e00000259000002cc00000073000000f400000072"
        ),
        data.readByteString()
      )
    }
  }

  @Test
  fun aWriteStringWithCharset() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteString("təˈranəˌsôr", Charset.forName("utf-32be"))
        sink.aFlush()
        assertEquals(
          ByteString.decodeHex(
            "0000007400000259000002c800000072000000610000006e00000259000002cc00000073000000f400000072"
          ),
          data.aReadByteString()
        )
      }
    }
  }

  @Test
  fun writeSubstringWithCharset() {
    forEachFactory { data, sink ->
      sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-32be"))
      sink.flush()
      assertEquals(ByteString.decodeHex("00000072000000610000006e00000259"), data.readByteString())
    }
  }

  @Test
  fun aWriteSubstringWithCharset() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteString("təˈranəˌsôr", 3, 7, Charset.forName("utf-32be"))
        sink.aFlush()
        assertEquals(ByteString.decodeHex("00000072000000610000006e00000259"), data.aReadByteString())
      }
    }
  }

  @Test
  fun writeUtf8SubstringWithCharset() {
    forEachFactory { data, sink ->
      sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-8"))
      sink.flush()
      assertEquals(ByteString.encodeUtf8("ranə"), data.readByteString())
    }
  }

  @Test
  fun aWriteUtf8SubstringWithCharset() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteString("təˈranəˌsôr", 3, 7, Charset.forName("utf-8"))
        sink.aFlush()
        assertEquals(ByteString.encodeUtf8("ranə"), data.aReadByteString())
      }
    }
  }

  @Test
  fun writeAll() {
    forEachFactory { data, sink ->
      val source = Buffer().writeUtf8("abcdef")
      assertEquals(6L, sink.writeAll(source))
      assertEquals(0L, source.size())
      sink.flush()
      assertEquals("abcdef", data.readUtf8())
    }
  }

  @Test
  fun aWriteAll() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = Buffer().aWriteUtf8("abcdef")
        assertEquals(6L, sink.aWriteAll(source))
        assertEquals(0L, source.size())
        sink.aFlush()
        assertEquals("abcdef", data.aReadUtf8())
      }
    }
  }

  @Test
  fun writeSource() {
    forEachFactory { data, sink ->
      val source = Buffer().writeUtf8("abcdef")
      sink.write(source, 4L)
      sink.flush()
      assertEquals("abcd", data.readUtf8())
      assertEquals("ef", source.readUtf8())
    }
  }

  @Test
  fun aWriteSource() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = Buffer().aWriteUtf8("abcdef")
        sink.aWrite(source, 4L)
        sink.aFlush()
        assertEquals("abcd", data.aReadUtf8())
        assertEquals("ef", source.aReadUtf8())
      }
    }
  }

  @Test
  fun writeSourceReadsFully() {
    forEachFactory { data, sink ->
      val source = object : ForwardingSource(Buffer()) {
        @Suppress("NAME_SHADOWING")
        override fun read(sink: Buffer, byteCount: Long): Long {
          sink.writeUtf8("abcd")
          return 4L
        }
        @Suppress("NAME_SHADOWING")
        suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
          sink.aWriteUtf8("abcd")
          return 4L
        }
      }
      sink.write(source, 8L)
      sink.flush()
      assertEquals("abcdabcd", data.readUtf8())
    }
  }

  @Test
  fun aWriteSourceReadsFully() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = object : ForwardingSource(Buffer()) {
          @Suppress("NAME_SHADOWING")
          override fun read(sink: Buffer, byteCount: Long): Long {
            sink.writeUtf8("abcd")
            return 4L
          }
          @Suppress("NAME_SHADOWING")
          suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
            sink.aWriteUtf8("abcd")
            return 4L
          }
        }
        sink.aWrite(source, 8L)
        sink.aFlush()
        assertEquals("abcdabcd", data.aReadUtf8())
      }
    }
  }

  @Test
  fun writeSourcePropagatesEof() {
    forEachFactory { data, sink ->
      val source = Buffer().writeUtf8("abcd")
      try {
        sink.write(source, 8L)
        fail()
      } catch (expected: EOFException) {}
      sink.flush()
      assertEquals("abcd", data.readUtf8())
    }
  }

  @Test
  fun aWriteSourcePropagatesEof() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = Buffer().aWriteUtf8("abcd")
        try {
          sink.aWrite(source, 8L)
          fail()
        } catch (expected: EOFException) {}
        sink.aFlush()
        assertEquals("abcd", data.readUtf8())
      }
    }
  }

  @Test
  fun writeSourceWithZeroIsNoOp() {
    forEachFactory { data, sink ->
      val source = object : ForwardingSource(Buffer()) {
        @Suppress("NAME_SHADOWING")
        override fun read(sink: Buffer, byteCount: Long): Long {
          throw AssertionError()
        }
        @Suppress("NAME_SHADOWING")
        suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
          throw AssertionError()
        }
      }
      sink.write(source, 0L)
      assertEquals(0L, data.size())
    }
  }

  @Test
  fun aWriteSourceWithZeroIsNoOp() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = object : ForwardingSource(Buffer()) {
          @Suppress("NAME_SHADOWING")
          override fun read(sink: Buffer, byteCount: Long): Long {
            throw AssertionError()
          }

          @Suppress("NAME_SHADOWING")
          suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
            throw AssertionError()
          }
        }
        sink.aWrite(source, 0L)
        assertEquals(0L, data.size())
      }
    }
  }

  @Test
  fun writeAllExhausted() {
    forEachFactory { data, sink ->
      val source = Buffer()
      assertEquals(0L, sink.writeAll(source))
      assertEquals(0L, source.size())
    }
  }

  @Test
  fun aWriteAllExhausted() {
    forEachFactory { data, sink ->
      runBlocking {
        val source = Buffer()
        assertEquals(0L, sink.aWriteAll(source))
        assertEquals(0L, source.size())
      }
    }
  }

  @Test
  fun closeEmitsBufferedBytes() {
    forEachFactory { data, sink ->
      sink.writeByte('a'.toInt())
      sink.close()
      assertEquals('a', data.readByte().toChar())
    }
  }

  @Test
  fun aCloseEmitsBufferedBytes() {
    forEachFactory { data, sink ->
      runBlocking {
        sink.aWriteByte('a'.toInt())
        sink.aClose()
        assertEquals('a', data.aReadByte().toChar())
      }
    }
  }

  @Test
  fun outputStream() {
    forEachFactory { data, sink ->
      val out = sink.outputStream()
      out.write('a'.toInt())
      out.write(repeat('b', 9998).toByteArray(UTF_8))
      out.write('c'.toInt())
      out.flush()
      assertEquals("a" + repeat('b', 9998) + "c", data.readUtf8())
    }
  }

  @Test
  fun aOutputStream() {
    forEachFactory { data, sink ->
      runBlocking {
        val out = sink.outputStream()
        out.write('a'.toInt())
        out.write(repeat('b', 9998).toByteArray(UTF_8))
        out.write('c'.toInt())
        out.flush()
        assertEquals("a" + repeat('b', 9998) + "c", data.aReadUtf8())
      }
    }
  }

  @Test
  fun outputStreamBounds() {
    forEachFactory { data, sink ->
      val out = sink.outputStream()
      try {
        out.write(ByteArray(100), 50, 51)
        fail()
      } catch (expected: ArrayIndexOutOfBoundsException) {}
    }
  }

  private fun forEachFactory(f: (data: Buffer, sink: BufferedSink)->Unit) {
    factories.forEach {
      val data = Buffer()
      val sink = it.create(data)
      f(data, sink)
    }
  }

}
