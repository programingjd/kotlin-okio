package oknio

import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.fail

class RealBufferedSinkTest {

  @Test
  fun inputStreamCloses() {
    val sink = RealBufferedSink(Buffer())
    val out = sink.outputStream()
    out.close()
    try {
      sink.writeUtf8("Hi!")
      fail()
    }
    catch (e: IllegalStateException) {
      assertEquals("closed", e.message)
    }
  }

  @Test
  fun aInputStreamCloses() {
    runBlocking {
      val sink = RealBufferedSink(Buffer())
      val out = sink.outputStream()
      out.close()
      try {
        sink.aWriteUtf8("Hi!")
        fail()
      }
      catch (e: IllegalStateException) {
        assertEquals("closed", e.message)
      }
    }
  }

  @Test
  fun bufferedSinkEmitsTailWhenItIsComplete() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE - 1))
    assertEquals(0L, sink.size())
    bufferedSink.writeByte(0)
    assertEquals(Segment.SIZE.toLong(), sink.size())
    assertEquals(0L, bufferedSink.buffer().size())
  }

  @Test
  fun aBufferedSinkEmitsTailWhenItIsComplete() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8(repeat('a', Segment.SIZE - 1))
      assertEquals(0L, sink.size())
      bufferedSink.aWriteByte(0)
      assertEquals(Segment.SIZE.toLong(), sink.size())
      assertEquals(0L, bufferedSink.buffer().size())
    }
  }

  @Test
  fun bufferedSinkEmitMultipleSegments() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 4 - 1))
    assertEquals(Segment.SIZE * 3L, sink.size())
    assertEquals(Segment.SIZE - 1L, bufferedSink.buffer().size())
  }

  @Test
  fun aBufferedSinkEmitMultipleSegments() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8(repeat('a', Segment.SIZE * 4 - 1))
      assertEquals(Segment.SIZE * 3L, sink.size())
      assertEquals(Segment.SIZE - 1L, bufferedSink.buffer().size())
    }
  }

  @Test
  fun bufferedSinkFlush() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeByte('a'.toInt())
    assertEquals(0L, sink.size())
    bufferedSink.flush()
    assertEquals(0L, bufferedSink.buffer().size())
    assertEquals(1L, sink.size())
  }

  @Test
  fun aBufferedSinkFlush() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteByte('a'.toInt())
      assertEquals(0L, sink.size())
      bufferedSink.aFlush()
      assertEquals(0L, bufferedSink.buffer().size())
      assertEquals(1L, sink.size())
    }
  }

  @Test
  fun bytesEmittedToSinkWithFlush() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8("abc")
    bufferedSink.flush()
    assertEquals(3L, sink.size())
  }

  @Test
  fun aBytesEmittedToSinkWithFlush() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8("abc")
      bufferedSink.aFlush()
      assertEquals(3L, sink.size())
    }
  }

  @Test
  fun bytesNotEmittedToSinkWithoutFlush() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8("abc")
    assertEquals(0L, sink.size())
  }

  @Test
  fun aBytesNotEmittedToSinkWithoutFlush() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8("abc")
      assertEquals(0L, sink.size())
    }
  }

  @Test
  fun bytesEmittedToSinkWithEmit() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8("abc")
    bufferedSink.emit()
    assertEquals(3L, sink.size())
  }

  @Test
  fun aBytesEmittedToSinkWithEmit() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8("abc")
      bufferedSink.aEmit()
      assertEquals(3L, sink.size())
    }
  }

  @Test
  fun completeSegmentsEmitted() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 3))
    assertEquals(Segment.SIZE * 3L, sink.size())
  }

  @Test
  fun aCompleteSegmentsEmitted() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8(repeat('a', Segment.SIZE * 3))
      assertEquals(Segment.SIZE * 3L, sink.size())
    }
  }

  @Test
  fun incompleteSegmentsNotEmitted() {
    val sink = Buffer()
    val bufferedSink = RealBufferedSink(sink)
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 3 - 1))
    assertEquals(Segment.SIZE * 2L, sink.size())
  }

  @Test
  fun aIncompleteSegmentsNotEmitted() {
    runBlocking {
      val sink = Buffer()
      val bufferedSink = RealBufferedSink(sink)
      bufferedSink.aWriteUtf8(repeat('a', Segment.SIZE * 3 - 1))
      assertEquals(Segment.SIZE * 2L, sink.size())
    }
  }

  @Test
  fun closeWithExceptionWhenWriting() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException())
    val bufferedSink = RealBufferedSink(mockSink)
    bufferedSink.writeByte('a'.toInt())
    try {
      bufferedSink.close()
      fail()
    }
    catch (expected: IOException) {}
    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test
  fun aCloseWithExceptionWhenWriting() {
    runBlocking {
      val mockSink = MockSink()
      mockSink.scheduleThrow(0, IOException())
      val bufferedSink = RealBufferedSink(mockSink)
      bufferedSink.aWriteByte('a'.toInt())
      try {
        bufferedSink.aClose()
        fail()
      }
      catch (expected: IOException) {
      }
      mockSink.assertLog("write([text=a], 1)", "close()")
    }
  }

  @Test
  fun closeWithExceptionWhenClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(1, IOException())
    val bufferedSink = RealBufferedSink(mockSink)
    bufferedSink.writeByte('a'.toInt())
    try {
      bufferedSink.close()
      fail()
    }
    catch (expected: IOException) {}
    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test
  fun aCloseWithExceptionWhenClosing() {
    runBlocking {
      val mockSink = MockSink()
      mockSink.scheduleThrow(1, IOException())
      val bufferedSink = RealBufferedSink(mockSink)
      bufferedSink.aWriteByte('a'.toInt())
      try {
        bufferedSink.aClose()
        fail()
      }
      catch (expected: IOException) {
      }
      mockSink.assertLog("write([text=a], 1)", "close()")
    }
  }

  @Test
  fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val bufferedSink = RealBufferedSink(mockSink)
    bufferedSink.writeByte('a'.toInt())
    try {
      bufferedSink.close()
      fail()
    }
    catch (expected: IOException) {
      assertEquals("first", expected.message)
    }
    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test
  fun aCloseWithExceptionWhenWritingAndClosing() {
    runBlocking {
      val mockSink = MockSink()
      mockSink.scheduleThrow(0, IOException("first"))
      mockSink.scheduleThrow(1, IOException("second"))
      val bufferedSink = RealBufferedSink(mockSink)
      bufferedSink.aWriteByte('a'.toInt())
      try {
        bufferedSink.aClose()
        fail()
      }
      catch (expected: IOException) {
        assertEquals("first", expected.message)
      }
      mockSink.assertLog("write([text=a], 1)", "close()")
    }
  }

  @Test
  fun operationsAfterClose() {
    val mockSink = MockSink()
    val bufferedSink = RealBufferedSink(mockSink)
    bufferedSink.writeByte('a'.toInt())
    bufferedSink.close()
    try {
      bufferedSink.writeByte('a'.toInt())
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSink.write(ByteArray(10))
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSink.emitCompleteSegments()
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSink.emit()
      fail()
    }
    catch (expected: IllegalStateException) {}
    try {
      bufferedSink.flush()
      fail()
    }
    catch (expected: IllegalStateException) {}
    val os = bufferedSink.outputStream()
    try {
      os.write('a'.toInt())
      fail()
    }
    catch (expected: IOException) {}
    try {
      os.write(ByteArray(10))
      fail()
    }
    catch (expected: IOException) {}
    os.flush()
  }

  @Test
  fun aOperationsAfterClose() {
    runBlocking {
      val mockSink = MockSink()
      val bufferedSink = RealBufferedSink(mockSink)
      bufferedSink.aWriteByte('a'.toInt())
      bufferedSink.aClose()
      try {
        bufferedSink.aWriteByte('a'.toInt())
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSink.aWrite(ByteArray(10))
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSink.aEmitCompleteSegments()
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSink.aEmit()
        fail()
      }
      catch (expected: IllegalStateException) {}
      try {
        bufferedSink.aFlush()
        fail()
      }
      catch (expected: IllegalStateException) {}
    }
  }

  @Test
  fun writeAll() {
    val mockSink = MockSink()
    val bufferedSink = Oknio.buffer(mockSink)
    bufferedSink.buffer().writeUtf8("abc")
    assertEquals(3L, bufferedSink.writeAll(Buffer().writeUtf8("def")))
    assertEquals(6L, bufferedSink.buffer().size())
    assertEquals("abcdef", bufferedSink.buffer().readUtf8(6))
    mockSink.assertLog()
  }

  @Test
  fun aWriteAll() {
    runBlocking {
      val mockSink = MockSink()
      val bufferedSink = Oknio.buffer(mockSink)
      bufferedSink.buffer().aWriteUtf8("abc")
      assertEquals(3L, bufferedSink.aWriteAll(Buffer().writeUtf8("def")))
      assertEquals(6L, bufferedSink.buffer().size())
      assertEquals("abcdef", bufferedSink.buffer().aReadUtf8(6))
      mockSink.assertLog()
    }
  }

  @Test
  fun writeAllExhausted() {
    val mockSink = MockSink()
    val bufferedSink = Oknio.buffer(mockSink)
    assertEquals(0L, bufferedSink.writeAll(Buffer()))
    assertEquals(0L, bufferedSink.buffer().size())
    mockSink.assertLog()
  }

  @Test
  fun aWriteAllExhausted() {
    runBlocking {
      val mockSink = MockSink()
      val bufferedSink = Oknio.buffer(mockSink)
      assertEquals(0L, bufferedSink.aWriteAll(Buffer()))
      assertEquals(0L, bufferedSink.buffer().size())
      mockSink.assertLog()
    }
  }

  @Test
  fun writeAllWritesOneSegmentAtATime() {
    val write1 = Buffer().writeUtf8(repeat('a', Segment.SIZE))
    val write2 = Buffer().writeUtf8(repeat('b', Segment.SIZE))
    val write3 = Buffer().writeUtf8(repeat('c', Segment.SIZE))
    val source = Buffer().writeUtf8(
      repeat('a', Segment.SIZE) +
      repeat('b', Segment.SIZE) +
      repeat('c', Segment.SIZE)
    )
    val mockSink = MockSink()
    val bufferedSink = Oknio.buffer(mockSink)
    assertEquals(Segment.SIZE * 3L, bufferedSink.writeAll(source))
    mockSink.assertLog(
      "write(${write1}, ${write1.size()})",
      "write(${write2}, ${write2.size()})",
      "write(${write3}, ${write3.size()})"
    )
  }

  @Test
  fun aWriteAllWritesOneSegmentAtATime() {
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
      val bufferedSink = Oknio.buffer(mockSink)
      assertEquals(Segment.SIZE * 3L, bufferedSink.aWriteAll(source))
      mockSink.assertLog(
        "write(${write1}, ${write1.size()})",
        "write(${write2}, ${write2.size()})",
        "write(${write3}, ${write3.size()})"
      )
    }
  }

}
