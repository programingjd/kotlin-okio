package oknio

import kotlinx.coroutines.experimental.runBlocking
import java.io.EOFException
import java.io.IOException
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Tests solely for the behavior of RealBufferedSource's implementation. For generic
 * BufferedSource behavior use BufferedSourceTest.
 */
class RealBufferedSourceTest {

  @Test
  fun inputStreamTracksSegments() {
    val source = Buffer()
    source.writeUtf8("a")
    source.writeUtf8(repeat('b', Segment.SIZE))
    source.writeUtf8("c")
    val input = RealBufferedSource(source).inputStream()
    assertEquals(0, input.available())
    assertEquals(Segment.SIZE + 2L, source.size())
    assertEquals('a'.toInt(), input.read())
    assertEquals(Segment.SIZE - 1, input.available())
    assertEquals(2L, source.size())
    val data = ByteArray(Segment.SIZE * 2)
    assertEquals(Segment.SIZE - 1, input.read(data, 0, data.size))
    assertEquals(repeat('b', Segment.SIZE - 1), String(data, 0, Segment.SIZE - 1, UTF_8))
    assertEquals(2L, source.size())
    assertEquals('b'.toInt(), input.read())
    assertEquals(1, input.available())
    assertEquals(0L, source.size())
    assertEquals('c'.toInt(), input.read())
    assertEquals(0, input.available())
    assertEquals(0L, source.size())
    assertEquals(-1, input.read())
    assertEquals(0L, source.size())
  }

  @Test
  fun inputStreamCloses() {
    val source = RealBufferedSource(Buffer())
    val input = source.inputStream()
    input.close()
    try {
      source.require(1L)
      fail()
    }
    catch (e: IllegalStateException) {
      assertEquals("closed", e.message)
    }
  }

  @Test
  fun aInputStreamCloses() {
    runBlocking {
      val source = RealBufferedSource(Buffer())
      val input = source.inputStream()
      input.close()
      try {
        source.aRequire(1L)
        fail()
      }
      catch (e: IllegalStateException) {
        assertEquals("closed", e.message)
      }
    }
  }

  @Test
  fun indexOfStopsReadingAtLimit() {
    val buffer = Buffer().writeUtf8("abcdef")
    val bufferedSource = RealBufferedSource(object : ForwardingSource(buffer) {
      override fun read(sink: Buffer, byteCount: Long): Long {
        return super.read(sink, Math.min(1L, byteCount))
      }
      suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
        return super.aRead(sink, Math.min(1L, byteCount))
      }
    })
    assertEquals(6L, buffer.size())
    assertEquals(-1L, bufferedSource.indexOf('e'.toByte(), 0L, 4L))
    assertEquals(2L, buffer.size())
  }

  @Test
  fun aIndexOfStopsReadingAtLimit() {
    runBlocking {
      val buffer = Buffer().aWriteUtf8("abcdef")
      val bufferedSource = RealBufferedSource(object : ForwardingSource(buffer) {
        override fun read(sink: Buffer, byteCount: Long): Long {
          return super.read(sink, Math.min(1L, byteCount))
        }
        suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
          return super.aRead(sink, Math.min(1L, byteCount))
        }
      })
      assertEquals(6L, buffer.size())
      assertEquals(-1L, bufferedSource.aIndexOf('e'.toByte(), 0L, 4L))
      assertEquals(2L, buffer.size())
    }
  }

  @Test
  fun requireTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.buffer().writeUtf8("aa")
    bufferedSource.require(2L)
    assertEquals(2L, bufferedSource.buffer().size())
    assertEquals(2L, source.size())
  }


  @Test
  fun aRequireTracksBufferFirst() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8("bb")
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.buffer().aWriteUtf8("aa")
      bufferedSource.aRequire(2L)
      assertEquals(2L, bufferedSource.buffer().size())
      assertEquals(2L, source.size())
    }
  }

  @Test
  fun requireIncludesBufferBytes() {
    val source = Buffer()
    source.writeUtf8("b")
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.buffer().writeUtf8("a")
    bufferedSource.require(2L)
    assertEquals("ab", bufferedSource.buffer().readUtf8(2L))
  }

  @Test
  fun aRequireIncludesBufferBytes() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8("b")
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.buffer().aWriteUtf8("a")
      bufferedSource.aRequire(2L)
      assertEquals("ab", bufferedSource.buffer().aReadUtf8(2L))
    }
  }

  @Test
  fun requireInsufficientData() {
    val source = Buffer()
    source.writeUtf8("a")
    val bufferedSource = RealBufferedSource(source)
    try {
      bufferedSource.require(2L)
      fail()
    }
    catch (expected: EOFException) {}
  }

  @Test
  fun aRequireInsufficientData() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8("a")
      val bufferedSource = RealBufferedSource(source)
      try {
        bufferedSource.aRequire(2L)
        fail()
      }
      catch (expected: EOFException) {}
    }
  }

  @Test
  fun requireReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE))
    source.writeUtf8(repeat('b', Segment.SIZE))
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.require(2L)
    assertEquals(Segment.SIZE.toLong(), source.size())
    assertEquals(Segment.SIZE.toLong(), bufferedSource.buffer().size())
  }

  @Test
  fun aRequireReadsOneSegmentAtATime() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE))
      source.aWriteUtf8(repeat('b', Segment.SIZE))
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.aRequire(2L)
      assertEquals(Segment.SIZE.toLong(), source.size())
      assertEquals(Segment.SIZE.toLong(), bufferedSource.buffer().size())
    }
  }

  @Test
  fun skipReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE))
    source.writeUtf8(repeat('b', Segment.SIZE))
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.skip(2L)
    assertEquals(Segment.SIZE.toLong(), source.size())
    assertEquals(Segment.SIZE - 2L, bufferedSource.buffer().size())
  }

  @Test
  fun aSkipReadsOneSegmentAtATime() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE))
      source.aWriteUtf8(repeat('b', Segment.SIZE))
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.aSkip(2L)
      assertEquals(Segment.SIZE.toLong(), source.size())
      assertEquals(Segment.SIZE - 2L, bufferedSource.buffer().size())
    }
  }

  @Test
  fun skipTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.buffer().writeUtf8("aa")
    bufferedSource.skip(2L)
    assertEquals(0L, bufferedSource.buffer().size())
    assertEquals(2L, source.size())
  }

  @Test
  fun aSkipTracksBufferFirst() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8("bb")
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.buffer().aWriteUtf8("aa")
      bufferedSource.aSkip(2L)
      assertEquals(0L, bufferedSource.buffer().size())
      assertEquals(2L, source.size())
    }
  }

  @Test
  fun operationsAfterClose() {
    val source = Buffer()
    val bufferedSource = RealBufferedSource(source)
    bufferedSource.close()
    try {
      bufferedSource.indexOf(1.toByte())
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSource.skip(1L)
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSource.readByte()
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSource.readByteString(10L)
      fail()
    }
    catch (expected: IllegalStateException) {}
    val input = bufferedSource.inputStream()
    try {
      input.read()
      fail()
    }
    catch (expected: IOException) {}
    try {
      input.read(ByteArray(10))
      fail()
    }
    catch (expected: IOException) {}
  }

  @Test
  fun aOperationsAfterClose() {
    runBlocking {
      val source = Buffer()
      val bufferedSource = RealBufferedSource(source)
      bufferedSource.close()
      try {
        bufferedSource.aIndexOf(1.toByte())
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSource.aSkip(1L)
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSource.aReadByte()
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSource.aReadByteString(10L)
        fail()
      }
      catch (expected: IllegalStateException) {}
    }
  }

  @Test
  fun readAllReadsOneSegmentAtATime() {
    val write1 = Buffer().writeUtf8(repeat('a', Segment.SIZE))
    val write2 = Buffer().writeUtf8(repeat('b', Segment.SIZE))
    val write3 = Buffer().writeUtf8(repeat('c', Segment.SIZE))
    val source = Buffer().writeUtf8(
      repeat('a', Segment.SIZE) +
      repeat('b', Segment.SIZE) +
      repeat('c', Segment.SIZE)
    )
    val mockSink = MockSink()
    val bufferedSource = Oknio.buffer(source as Source)
    assertEquals(Segment.SIZE * 3L, bufferedSource.readAll(mockSink))
    mockSink.assertLog(
      "write(${write1}, ${write1.size()})",
      "write(${write2}, ${write2.size()})",
      "write(${write3}, ${write3.size()})"
    )
  }

  @Test
  fun aReadAllReadsOneSegmentAtATime() {
    runBlocking {
      val write1 = Buffer().aWriteUtf8(repeat('a', Segment.SIZE))
      val write2 = Buffer().aWriteUtf8(repeat('b', Segment.SIZE))
      val write3 = Buffer().aWriteUtf8(repeat('c', Segment.SIZE))
      val source = Buffer().aWriteUtf8(
        repeat('a', Segment.SIZE) +
        repeat('b', Segment.SIZE) +
        repeat('c', Segment.SIZE)
      )
      val mockSink = MockSink()
      val bufferedSource = Oknio.buffer(source as Source)
      assertEquals(Segment.SIZE * 3L, bufferedSource.aReadAll(mockSink))
      mockSink.assertLog(
        "write(${write1}, ${write1.size()})",
        "write(${write2}, ${write2.size()})",
        "write(${write3}, ${write3.size()})"
      )
    }
  }

}
