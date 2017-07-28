package oknio

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.Arrays
import java.util.Random


class BufferTest {

  @Test
  fun readAndWriteUtf8() {
    val buffer = Buffer()
    buffer.writeUtf8("ab")
    assertEquals(2, buffer.size())
    buffer.writeUtf8("cdef")
    assertEquals(6, buffer.size())
    assertEquals("abcd", buffer.readUtf8(4))
    assertEquals(2, buffer.size())
    assertEquals("ef", buffer.readUtf8(2))
    assertEquals(0, buffer.size())
    try {
      buffer.readUtf8(1)
      fail()
    }
    catch (expected: ArrayIndexOutOfBoundsException) {}
  }

  @Test
  fun aReadAndWriteUtf8() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8("ab")
      assertEquals(2, buffer.size())
      buffer.aWriteUtf8("cdef")
      assertEquals(6, buffer.size())
      assertEquals("abcd", buffer.aReadUtf8(4))
      assertEquals(2, buffer.size())
      assertEquals("ef", buffer.aReadUtf8(2))
      assertEquals(0, buffer.size())
      try {
        buffer.aReadUtf8(1)
        fail()
      }
      catch (expected: ArrayIndexOutOfBoundsException) {}
    }
  }

  @Test
  fun completeSegmentByteCountOnEmptyBuffer() {
    val buffer = Buffer()
    assertEquals(0, buffer.completeSegmentByteCount())
  }

  @Test
  fun completeSegmentByteCountOnBufferWithFullSegments() {
    val buffer = Buffer()
    buffer.writeUtf8(repeat('a', Segment.SIZE * 4))
    assertEquals(Segment.SIZE * 4L, buffer.completeSegmentByteCount())
  }

  @Test
  fun aCompleteSegmentByteCountOnBufferWithFullSegments() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8(repeat('a', Segment.SIZE * 4))
      assertEquals(Segment.SIZE * 4L, buffer.completeSegmentByteCount())
    }
  }

  @Test
  fun completeSegmentByteCountOnBufferWithIncompleteTailSegment() {
    val buffer = Buffer()
    buffer.writeUtf8(repeat('a', Segment.SIZE * 4 - 10))
    assertEquals(Segment.SIZE * 3L, buffer.completeSegmentByteCount())
  }

  @Test
  fun aCompleteSegmentByteCountOnBufferWithIncompleteTailSegment() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8(repeat('a', Segment.SIZE * 4 - 10))
      assertEquals(Segment.SIZE * 3L, buffer.completeSegmentByteCount())
    }
  }

  @Test
  fun bufferToString() {
    assertEquals("[size=0]", Buffer().toString())
    assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]",
                 Buffer().writeUtf8("a\r\nb\nc\rd\\e").toString())
    assertEquals("[text=Tyrannosaur]",
                 Buffer().writeUtf8("Tyrannosaur").toString())
    assertEquals("[text=təˈranəˌsôr]", Buffer()
      .write(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"))
      .toString())
    assertEquals("[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000" +
                 "0000000000000000000000000000000000000000000000000000]",
                 Buffer().write(ByteArray(64)).toString())
  }

  @Test
  fun aBufferToString() {
    runBlocking {
      assertEquals("[size=0]", Buffer().toString())
      assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]",
                   Buffer().aWriteUtf8("a\r\nb\nc\rd\\e").toString())
      assertEquals("[text=Tyrannosaur]",
                   Buffer().aWriteUtf8("Tyrannosaur").toString())
      assertEquals("[text=təˈranəˌsôr]", Buffer()
        .aWrite(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"))
        .toString())
      assertEquals("[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000" +
                     "0000000000000000000000000000000000000000000000000000]",
                   Buffer().aWrite(ByteArray(64)).toString())
    }
  }

  @Test
  fun multipleSegmentBuffers() {
    val buffer = Buffer()
    buffer.writeUtf8(repeat('a', 1000))
    buffer.writeUtf8(repeat('b', 2500))
    buffer.writeUtf8(repeat('c', 5000))
    buffer.writeUtf8(repeat('d', 10000))
    buffer.writeUtf8(repeat('e', 25000))
    buffer.writeUtf8(repeat('f', 50000))

    assertEquals(repeat('a', 999), buffer.readUtf8(999))
    assertEquals("a" + repeat('b', 2500) + "c", buffer.readUtf8(2502))
    assertEquals(repeat('c', 4998), buffer.readUtf8(4998))
    assertEquals("c" + repeat('d', 10000) + "e", buffer.readUtf8(10002))
    assertEquals(repeat('e', 24998), buffer.readUtf8(24998))
    assertEquals("e" + repeat('f', 50000), buffer.readUtf8(50001))
    assertEquals(0, buffer.size())
  }

  @Test
  fun aMultipleSegmentBuffers() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8(repeat('a', 1000))
      buffer.aWriteUtf8(repeat('b', 2500))
      buffer.aWriteUtf8(repeat('c', 5000))
      buffer.aWriteUtf8(repeat('d', 10000))
      buffer.aWriteUtf8(repeat('e', 25000))
      buffer.aWriteUtf8(repeat('f', 50000))

      assertEquals(repeat('a', 999), buffer.aReadUtf8(999))
      assertEquals("a" + repeat('b', 2500) + "c", buffer.aReadUtf8(2502))
      assertEquals(repeat('c', 4998), buffer.aReadUtf8(4998))
      assertEquals("c" + repeat('d', 10000) + "e", buffer.aReadUtf8(10002))
      assertEquals(repeat('e', 24998), buffer.aReadUtf8(24998))
      assertEquals("e" + repeat('f', 50000), buffer.aReadUtf8(50001))
      assertEquals(0, buffer.size())
    }
  }

  @Test
  fun fillAndDrainPool() {
    val buffer = Buffer()

    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)

    buffer.readByteString(SegmentPool.MAX_SIZE.toLong())
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

    buffer.readByteString(SegmentPool.MAX_SIZE.toLong())
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)

    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)
  }

  @Test
  fun aFillAndDrainPool() {
    runBlocking {
      val buffer = Buffer()

      buffer.aWrite(ByteArray(SegmentPool.MAX_SIZE))
      buffer.aWrite(ByteArray(SegmentPool.MAX_SIZE))
      assertEquals(0, SegmentPool.byteCount)

      buffer.aReadByteString(SegmentPool.MAX_SIZE.toLong())
      assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

      buffer.aReadByteString(SegmentPool.MAX_SIZE.toLong())
      assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

      buffer.aWrite(ByteArray(SegmentPool.MAX_SIZE))
      assertEquals(0, SegmentPool.byteCount)

      buffer.aWrite(ByteArray(SegmentPool.MAX_SIZE))
      assertEquals(0, SegmentPool.byteCount)
    }
  }

  @Test
  fun moveBytesBetweenBuffersShareSegment() {
    val size = Segment.SIZE / 2 - 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(size * 2), segmentSizes)
  }

  @Test
  fun aMoveBytesBetweenBuffersShareSegment() {
    runBlocking {
      val size = Segment.SIZE / 2 - 1
      val segmentSizes = aMoveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
      assertEquals(listOf(size * 2), segmentSizes)
    }
  }

  @Test
  fun moveBytesBetweenBuffersReassignSegment() {
    val size = Segment.SIZE / 2 + 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(size, size), segmentSizes)
  }

  @Test
  fun aMoveBytesBetweenBuffersReassignSegment() {
    runBlocking {
      val size = Segment.SIZE / 2 + 1
      val segmentSizes = aMoveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
      assertEquals(listOf(size, size), segmentSizes)
    }
  }

  @Test
  fun moveBytesBetweenBuffersMultipleSegments() {
    val size = 3 * Segment.SIZE + 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(Segment.SIZE, Segment.SIZE, Segment.SIZE, 1,
                        Segment.SIZE, Segment.SIZE, Segment.SIZE, 1), segmentSizes)
  }

  @Test
  fun aMoveBytesBetweenBuffersMultipleSegments() {
    runBlocking {
      val size = 3 * Segment.SIZE + 1
      val segmentSizes = aMoveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
      assertEquals(listOf(Segment.SIZE, Segment.SIZE, Segment.SIZE, 1,
                          Segment.SIZE, Segment.SIZE, Segment.SIZE, 1), segmentSizes)
    }
  }

  private fun moveBytesBetweenBuffers(vararg contents: String): List<Int> {
    val expected = StringBuilder()
    val buffer = Buffer()
    for (s in contents) {
      val source = Buffer()
      source.writeUtf8(s)
      buffer.writeAll(source)
      expected.append(s)
    }
    val segmentSizes = buffer.segmentSizes()
    assertEquals(expected.toString(), buffer.readUtf8(expected.length.toLong()))
    return segmentSizes
  }

  suspend private fun aMoveBytesBetweenBuffers(vararg contents: String): List<Int> {
    val expected = StringBuilder()
    val buffer = Buffer()
    for (s in contents) {
      val source = Buffer()
      source.aWriteUtf8(s)
      buffer.aWriteAll(source)
      expected.append(s)
    }
    val segmentSizes = buffer.segmentSizes()
    assertEquals(expected.toString(), buffer.aReadUtf8(expected.length.toLong()))
    return segmentSizes
  }

  @Test
  fun writeSplitSourceBufferLeft() {
    val writeSize = Segment.SIZE / 2 + 1

    val sink = Buffer()
    sink.writeUtf8(repeat('b', Segment.SIZE - 10))

    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(Segment.SIZE - 10, writeSize), sink.segmentSizes())
    assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), source.segmentSizes())
  }

  @Test
  fun aWriteSplitSourceBufferLeft() {
    runBlocking {
      val writeSize = Segment.SIZE / 2 + 1

      val sink = Buffer()
      sink.aWriteUtf8(repeat('b', Segment.SIZE - 10))

      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      sink.write(source, writeSize.toLong())

      assertEquals(listOf(Segment.SIZE - 10, writeSize), sink.segmentSizes())
      assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), source.segmentSizes())
    }
  }

  @Test
  fun writeSplitSourceBufferRight() {
    val writeSize = Segment.SIZE / 2 - 1

    val sink = Buffer()
    sink.writeUtf8(repeat('b', Segment.SIZE - 10))

    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(Segment.SIZE - 10, writeSize), sink.segmentSizes())
    assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), source.segmentSizes())
  }

  @Test
  fun aWriteSplitSourceBufferRight() {
    runBlocking {
      val writeSize = Segment.SIZE / 2 - 1

      val sink = Buffer()
      sink.aWriteUtf8(repeat('b', Segment.SIZE - 10))

      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      sink.write(source, writeSize.toLong())

      assertEquals(listOf(Segment.SIZE - 10, writeSize), sink.segmentSizes())
      assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), source.segmentSizes())
    }
  }

  @Test
  fun writePrefixDoesntSplit() {
    val sink = Buffer()
    sink.writeUtf8(repeat('b', 10))

    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE * 2))
    sink.write(source, 20)

    assertEquals(listOf(30), sink.segmentSizes())
    assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), source.segmentSizes())
    assertEquals(30, sink.size())
    assertEquals(Segment.SIZE * 2L - 20L, source.size())
  }

  @Test
  fun aWritePrefixDoesntSplit() {
    runBlocking {
      val sink = Buffer()
      sink.aWriteUtf8(repeat('b', 10))

      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      sink.aWrite(source, 20)

      assertEquals(listOf(30), sink.segmentSizes())
      assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), source.segmentSizes())
      assertEquals(30, sink.size())
      assertEquals(Segment.SIZE * 2L - 20L, source.size())
    }
  }

  @Test
  fun writePrefixDoesntSplitButRequiresCompact() {
    val sink = Buffer()
    sink.writeUtf8(repeat('b', Segment.SIZE - 10)) // limit = size - 10
    sink.readUtf8((Segment.SIZE - 20).toLong()) // pos = size = 20

    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE * 2))
    sink.write(source, 20L)

    assertEquals(listOf(30), sink.segmentSizes())
    assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), source.segmentSizes())
    assertEquals(30L, sink.size())
    assertEquals(Segment.SIZE * 2L - 20L, source.size())
  }

  @Test
  fun aWritePrefixDoesntSplitButRequiresCompact() {
    runBlocking {
      val sink = Buffer()
      sink.aWriteUtf8(repeat('b', Segment.SIZE - 10)) // limit = size - 10
      sink.aReadUtf8((Segment.SIZE - 20).toLong()) // pos = size = 20

      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      sink.aWrite(source, 20L)

      assertEquals(listOf(30), sink.segmentSizes())
      assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), source.segmentSizes())
      assertEquals(30L, sink.size())
      assertEquals(Segment.SIZE * 2L - 20L, source.size())
    }
  }

  @Test
  fun copyToSpanningSegments() {
    val source = Buffer()
    source.writeUtf8(repeat('a', Segment.SIZE * 2))
    source.writeUtf8(repeat('b', Segment.SIZE * 2))

    val output = ByteArrayOutputStream()
    source.copyTo(output, 10L, (Segment.SIZE * 3).toLong())

    assertEquals(repeat('a', Segment.SIZE * 2 - 10) + repeat('b', Segment.SIZE + 10),
                 output.toString())
    assertEquals(repeat('a', Segment.SIZE * 2) + repeat('b', Segment.SIZE * 2),
                 source.readUtf8((Segment.SIZE * 4).toLong()))
  }

  @Test
  fun aCopyToSpanningSegments() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      source.aWriteUtf8(repeat('b', Segment.SIZE * 2))

      val output = ByteArrayOutputStream()
      source.copyTo(output, 10L, (Segment.SIZE * 3).toLong())

      assertEquals(repeat('a', Segment.SIZE * 2 - 10) + repeat('b', Segment.SIZE + 10),
                   output.toString())
      assertEquals(repeat('a', Segment.SIZE * 2) + repeat('b', Segment.SIZE * 2),
                   source.aReadUtf8((Segment.SIZE * 4).toLong()))
    }
  }

  @Test
  fun copyToStream() {
    val buffer = Buffer().writeUtf8("hello, world!")
    val output = ByteArrayOutputStream()
    buffer.copyTo(output)
    val outString = String(output.toByteArray(), UTF_8)
    assertEquals("hello, world!", outString)
    assertEquals("hello, world!", buffer.readUtf8())
  }

  @Test
  fun aCopyToStream() {
    runBlocking {
      val buffer = Buffer().aWriteUtf8("hello, world!")
      val output = ByteArrayOutputStream()
      buffer.copyTo(output)
      val outString = String(output.toByteArray(), UTF_8)
      assertEquals("hello, world!", outString)
      assertEquals("hello, world!", buffer.aReadUtf8())
    }
  }

  @Test
  fun writeToSpanningSegments() {
    val buffer = Buffer()
    buffer.writeUtf8(repeat('a', Segment.SIZE * 2))
    buffer.writeUtf8(repeat('b', Segment.SIZE * 2))

    val output = ByteArrayOutputStream()
    buffer.skip(10L)
    buffer.writeTo(output, (Segment.SIZE * 3).toLong())

    assertEquals(repeat('a', Segment.SIZE * 2 - 10) + repeat('b', Segment.SIZE + 10), output.toString())
    assertEquals(repeat('b', Segment.SIZE - 10), buffer.readUtf8(buffer.size))
  }

  @Test
  fun aWriteToSpanningSegments() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8(repeat('a', Segment.SIZE * 2))
      buffer.aWriteUtf8(repeat('b', Segment.SIZE * 2))

      val output = ByteArrayOutputStream()
      buffer.aSkip(10L)
      buffer.writeTo(output, (Segment.SIZE * 3).toLong())

      assertEquals(repeat('a', Segment.SIZE * 2 - 10) + repeat('b', Segment.SIZE + 10), output.toString())
      assertEquals(repeat('b', Segment.SIZE - 10), buffer.aReadUtf8(buffer.size))
    }
  }

  @Test
  fun writeToStream() {
    val buffer = Buffer().writeUtf8("hello, world!")
    val output = ByteArrayOutputStream()
    buffer.writeTo(output)
    val outString = String(output.toByteArray(), UTF_8)
    assertEquals("hello, world!", outString)
    assertEquals(0L, buffer.size())
  }

  @Test
  fun readFromStream() {
    val input = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer()
    buffer.readFrom(input)
    val output = buffer.readUtf8()
    assertEquals("hello, world!", output)
  }

  @Test
  fun readFromSpanningSegments() {
    val input = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer().writeUtf8(repeat('a', Segment.SIZE - 10))
    buffer.readFrom(input)
    val output = buffer.readUtf8()
    assertEquals(repeat('a', Segment.SIZE - 10) + "hello, world!", output)
  }

  @Test
  fun aReadFromSpanningSegments() {
    runBlocking {
      val input = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
      val buffer = Buffer().aWriteUtf8(repeat('a', Segment.SIZE - 10))
      buffer.readFrom(input)
      val output = buffer.aReadUtf8()
      assertEquals(repeat('a', Segment.SIZE - 10) + "hello, world!", output)
    }
  }

  @Test
  fun readFromStreamWithCount() {
    val input = ByteArrayInputStream("hello, world!".toByteArray(UTF_8))
    val buffer = Buffer()
    buffer.readFrom(input, 10L)
    val output = buffer.readUtf8()
    assertEquals("hello, wor", output)
  }

  @Test
  fun moveAllRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8(repeat('a', 10))

    val source = Buffer()
    source.writeUtf8(repeat('b', 15))

    assertEquals(10L, source.read(sink, 10L))
    assertEquals(20L, sink.size())
    assertEquals(5L, source.size())
    assertEquals(repeat('a', 10) + repeat('b', 10), sink.readUtf8(20L))
  }

  @Test
  fun aMoveAllRequestedBytesWithRead() {
    runBlocking {
      val sink = Buffer()
      sink.aWriteUtf8(repeat('a', 10))

      val source = Buffer()
      source.aWriteUtf8(repeat('b', 15))

      assertEquals(10L, source.aRead(sink, 10L))
      assertEquals(20L, sink.size())
      assertEquals(5L, source.size())
      assertEquals(repeat('a', 10) + repeat('b', 10), sink.aReadUtf8(20L))
    }
  }

  @Test
  fun moveFewerThanRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8(repeat('a', 10))

    val source = Buffer()
    source.writeUtf8(repeat('b', 20))

    assertEquals(20L, source.read(sink, 25L))
    assertEquals(30L, sink.size())
    assertEquals(0L, source.size())
    assertEquals(repeat('a', 10) + repeat('b', 20), sink.readUtf8(30L))
  }

  @Test
  fun aMoveFewerThanRequestedBytesWithRead() {
    runBlocking {
      val sink = Buffer()
      sink.aWriteUtf8(repeat('a', 10))

      val source = Buffer()
      source.aWriteUtf8(repeat('b', 20))

      assertEquals(20L, source.aRead(sink, 25L))
      assertEquals(30L, sink.size())
      assertEquals(0L, source.size())
      assertEquals(repeat('a', 10) + repeat('b', 20), sink.aReadUtf8(30L))
    }
  }

  @Test
  fun indexOfWithOffset() {
    val buffer = Buffer()
    val halfSegment = Segment.SIZE / 2
    buffer.writeUtf8(repeat('a', halfSegment))
    buffer.writeUtf8(repeat('b', halfSegment))
    buffer.writeUtf8(repeat('c', halfSegment))
    buffer.writeUtf8(repeat('d', halfSegment))
    assertEquals(0L, buffer.indexOf('a'.toByte(), 0L))
    assertEquals(halfSegment - 1L, buffer.indexOf('a'.toByte(), halfSegment - 1L))
    assertEquals(halfSegment.toLong(), buffer.indexOf('b'.toByte(), halfSegment - 1L))
    assertEquals(halfSegment * 2L, buffer.indexOf('c'.toByte(), halfSegment - 1L))
    assertEquals(halfSegment * 3L, buffer.indexOf('d'.toByte(), halfSegment - 1L))
    assertEquals(halfSegment * 3L, buffer.indexOf('d'.toByte(), halfSegment * 2L))
    assertEquals(halfSegment * 3L, buffer.indexOf('d'.toByte(), halfSegment * 3L))
    assertEquals(halfSegment * 4L - 1L, buffer.indexOf('d'.toByte(), halfSegment * 4L - 1L))
  }

  @Test
  fun aIndexOfWithOffset() {
    runBlocking {
      val buffer = Buffer()
      val halfSegment = Segment.SIZE / 2
      buffer.aWriteUtf8(repeat('a', halfSegment))
      buffer.aWriteUtf8(repeat('b', halfSegment))
      buffer.aWriteUtf8(repeat('c', halfSegment))
      buffer.aWriteUtf8(repeat('d', halfSegment))
      assertEquals(0L, buffer.aIndexOf('a'.toByte(), 0L))
      assertEquals(halfSegment - 1L, buffer.aIndexOf('a'.toByte(), halfSegment - 1L))
      assertEquals(halfSegment.toLong(), buffer.aIndexOf('b'.toByte(), halfSegment - 1L))
      assertEquals(halfSegment * 2L, buffer.aIndexOf('c'.toByte(), halfSegment - 1L))
      assertEquals(halfSegment * 3L, buffer.aIndexOf('d'.toByte(), halfSegment - 1L))
      assertEquals(halfSegment * 3L, buffer.aIndexOf('d'.toByte(), halfSegment * 2L))
      assertEquals(halfSegment * 3L, buffer.aIndexOf('d'.toByte(), halfSegment * 3L))
      assertEquals(halfSegment * 4L - 1L, buffer.aIndexOf('d'.toByte(), halfSegment * 4L - 1L))
    }
  }

  @Test
  fun byteAt() {
    val buffer = Buffer()
    buffer.writeUtf8("a")
    buffer.writeUtf8(repeat('b', Segment.SIZE))
    buffer.writeUtf8("c")
    assertEquals('a'.toByte(), buffer.getByte(0L))
    assertEquals('a'.toByte(), buffer.getByte(0L))
    assertEquals('c'.toByte(), buffer.getByte(buffer.size - 1L))
    assertEquals('b'.toByte(), buffer.getByte(buffer.size - 2L))
    assertEquals('b'.toByte(), buffer.getByte(buffer.size - 3L))
  }

  @Test
  fun aByteAt() {
    runBlocking {
      val buffer = Buffer()
      buffer.aWriteUtf8("a")
      buffer.aWriteUtf8(repeat('b', Segment.SIZE))
      buffer.aWriteUtf8("c")
      assertEquals('a'.toByte(), buffer.getByte(0L))
      assertEquals('a'.toByte(), buffer.getByte(0L))
      assertEquals('c'.toByte(), buffer.getByte(buffer.size - 1L))
      assertEquals('b'.toByte(), buffer.getByte(buffer.size - 2L))
      assertEquals('b'.toByte(), buffer.getByte(buffer.size - 3L))
    }
  }

  @Test
  fun getByteOfEmptyBuffer() {
    val buffer = Buffer()
    try {
      buffer.getByte(0L)
      fail()
    }
    catch (expected: IndexOutOfBoundsException) {}
  }

  @Test
  fun writePrefixToEmptyBuffer() {
    val sink = Buffer()
    val source = Buffer()
    source.writeUtf8("abcd")
    sink.write(source, 2L)
    assertEquals("ab", sink.readUtf8(2L))
  }

  @Test
  fun aWritePrefixToEmptyBuffer() {
    runBlocking {
      val sink = Buffer()
      val source = Buffer()
      source.aWriteUtf8("abcd")
      sink.aWrite(source, 2L)
      assertEquals("ab", sink.aReadUtf8(2L))
    }
  }

  @Test
  fun cloneDoesNotObserveWritesToOriginal() {
    val original = Buffer()
    val clone = original.clone()
    original.writeUtf8("abc")
    assertEquals(0L, clone.size())
  }

  @Test
  fun cloneDoesNotObserveReadsFromOriginal() {
    val original = Buffer()
    original.writeUtf8("abc")
    val clone = original.clone()
    assertEquals("abc", original.readUtf8(3L))
    assertEquals(3L, clone.size())
    assertEquals("ab", clone.readUtf8(2L))
  }

  @Test
  fun aCloneDoesNotObserveReadsFromOriginal() {
    runBlocking {
      val original = Buffer()
      original.aWriteUtf8("abc")
      val clone = original.clone()
      assertEquals("abc", original.aReadUtf8(3L))
      assertEquals(3L, clone.size())
      assertEquals("ab", clone.aReadUtf8(2L))
    }
  }

  @Test
  fun originalDoesNotObserveWritesToClone() {
    val original = Buffer()
    val clone = original.clone()
    clone.writeUtf8("abc")
    assertEquals(0L, original.size())
  }

  @Test
  fun originalDoesNotObserveReadsFromClone() {
    val original = Buffer()
    original.writeUtf8("abc")
    val clone = original.clone()
    assertEquals("abc", clone.readUtf8(3L))
    assertEquals(3L, original.size())
    assertEquals("ab", original.readUtf8(2L))
  }

  @Test
  fun cloneMultipleSegments() {
    val original = Buffer()
    original.writeUtf8(repeat('a', Segment.SIZE * 3))
    val clone = original.clone()
    original.writeUtf8(repeat('b', Segment.SIZE * 3))
    clone.writeUtf8(repeat('c', Segment.SIZE * 3))

    assertEquals(repeat('a', Segment.SIZE * 3) + repeat('b', Segment.SIZE * 3),
                 original.readUtf8((Segment.SIZE * 6).toLong()))
    assertEquals(repeat('a', Segment.SIZE * 3) + repeat('c', Segment.SIZE * 3),
                 clone.readUtf8((Segment.SIZE * 6).toLong()))
  }

  @Test
  fun aCloneMultipleSegments() {
    runBlocking {
      val original = Buffer()
      original.aWriteUtf8(repeat('a', Segment.SIZE * 3))
      val clone = original.clone()
      original.aWriteUtf8(repeat('b', Segment.SIZE * 3))
      clone.aWriteUtf8(repeat('c', Segment.SIZE * 3))

      assertEquals(repeat('a', Segment.SIZE * 3) + repeat('b', Segment.SIZE * 3),
                   original.aReadUtf8((Segment.SIZE * 6).toLong()))
      assertEquals(repeat('a', Segment.SIZE * 3) + repeat('c', Segment.SIZE * 3),
                   clone.aReadUtf8((Segment.SIZE * 6).toLong()))
    }
  }

  @Test
  fun equalsAndHashCodeEmpty() {
    val a = Buffer()
    val b = Buffer()
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Test
  fun equalsAndHashCode() {
    val a = Buffer().writeUtf8("dog")
    val b = Buffer().writeUtf8("hotdog")
    assertFalse(a == b)
    assertFalse(a.hashCode() == b.hashCode())

    b.readUtf8(3L)
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Test
  fun aEqualsAndHashCode() {
    runBlocking {
      val a = Buffer().aWriteUtf8("dog")
      val b = Buffer().aWriteUtf8("hotdog")
      assertFalse(a == b)
      assertFalse(a.hashCode() == b.hashCode())

      b.aReadUtf8(3L)
      assertTrue(a == b)
      assertTrue(a.hashCode() == b.hashCode())
    }
  }

  @Test
  fun equalsAndHashCodeSpanningSegments() {
    val data = ByteArray(1024 * 1024)
    val dice = Random(0L)
    dice.nextBytes(data)

    val a = bufferWithRandomSegmentLayout(dice, data)
    val b = bufferWithRandomSegmentLayout(dice, data)
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())

    ++data[data.size / 2]
    val c = bufferWithRandomSegmentLayout(dice, data)
    assertFalse(a == c)
    assertFalse(a.hashCode() == c.hashCode())
  }

  @Test
  fun bufferInputStreamByteByByte() {
    val source = Buffer()
    source.writeUtf8("abc")

    val input = source.inputStream()
    assertEquals(3, input.available())
    assertEquals('a'.toInt(), input.read())
    assertEquals('b'.toInt(), input.read())
    assertEquals('c'.toInt(), input.read())
    assertEquals(-1, input.read())
    assertEquals(0, input.available())
  }

  @Test
  fun bufferInputStreamBulkReads() {
    val source = Buffer()
    source.writeUtf8("abc")

    val byteArray = ByteArray(4)

    Arrays.fill(byteArray, (-5).toByte())
    val input = source.inputStream()
    assertEquals(3, input.read(byteArray))
    assertEquals("[97, 98, 99, -5]", Arrays.toString(byteArray))

    Arrays.fill(byteArray, (-7).toByte())
    assertEquals(-1, input.read(byteArray))
    assertEquals("[-7, -7, -7, -7]", Arrays.toString(byteArray))
  }

  @Test
  fun readAllWritesAllSegmentsAtOnce() {
    val write1 = Buffer().writeUtf8(repeat('a', Segment.SIZE) +
                                      repeat('b', Segment.SIZE) +
                                      repeat('c', Segment.SIZE))

    val source = Buffer().writeUtf8(repeat('a', Segment.SIZE) +
                                      repeat('b', Segment.SIZE) +
                                      repeat('c', Segment.SIZE))

    val mockSink = MockSink()

    assertEquals(Segment.SIZE * 3L, source.readAll(mockSink))
    assertEquals(0L, source.size())
    mockSink.assertLog("write(" + write1 + ", " + write1.size() + ")")
  }

  @Test
  fun aReadAllWritesAllSegmentsAtOnce() {
    runBlocking {
      val write1 = Buffer().aWriteUtf8(repeat('a', Segment.SIZE) +
                                        repeat('b', Segment.SIZE) +
                                        repeat('c', Segment.SIZE))

      val source = Buffer().aWriteUtf8(repeat('a', Segment.SIZE) +
                                        repeat('b', Segment.SIZE) +
                                        repeat('c', Segment.SIZE))

      val mockSink = MockSink()

      assertEquals(Segment.SIZE * 3L, source.aReadAll(mockSink))
      assertEquals(0L, source.size())
      mockSink.assertLog("write(" + write1 + ", " + write1.size() + ")")
    }
  }

  @Test
  fun writeAllMultipleSegments() {
    val source = Buffer().writeUtf8(repeat('a', Segment.SIZE * 3))
    val sink = Buffer()

    assertEquals(Segment.SIZE * 3L, sink.writeAll(source))
    assertEquals(0L, source.size())
    assertEquals(repeat('a', Segment.SIZE * 3), sink.readUtf8())
  }

  @Test
  fun aWriteAllMultipleSegments() {
    runBlocking {
      val source = Buffer().aWriteUtf8(repeat('a', Segment.SIZE * 3))
      val sink = Buffer()

      assertEquals(Segment.SIZE * 3L, sink.aWriteAll(source))
      assertEquals(0L, source.size())
      assertEquals(repeat('a', Segment.SIZE * 3), sink.aReadUtf8())
    }
  }

  @Test
  fun copyTo() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target, 1L, 3L)

    assertEquals("art", target.readUtf8())
    assertEquals("party", source.readUtf8())
  }

  @Test
  fun aCopyTo() {
    runBlocking {
      val source = Buffer()
      source.aWriteUtf8("party")

      val target = Buffer()
      source.copyTo(target, 1L, 3L)

      assertEquals("art", target.aReadUtf8())
      assertEquals("party", source.aReadUtf8())
    }
  }

  @Test
  fun copyToOnSegmentBoundary() {
    val bs = repeat('b', Segment.SIZE)
    val cs = repeat('c', Segment.SIZE)
    val ds = repeat('d', Segment.SIZE)
    val es = repeat('e', Segment.SIZE)

    val source = Buffer()
    source.writeUtf8(bs)
    source.writeUtf8(cs)
    source.writeUtf8(ds)

    val target = Buffer()
    target.writeUtf8(es)

    source.copyTo(target, bs.length.toLong(), (cs.length + ds.length).toLong())
    assertEquals(es + cs + ds, target.readUtf8())
  }

  @Test
  fun aCopyToOnSegmentBoundary() {
    runBlocking {
      val bs = repeat('b', Segment.SIZE)
      val cs = repeat('c', Segment.SIZE)
      val ds = repeat('d', Segment.SIZE)
      val es = repeat('e', Segment.SIZE)

      val source = Buffer()
      source.aWriteUtf8(bs)
      source.aWriteUtf8(cs)
      source.aWriteUtf8(ds)

      val target = Buffer()
      target.aWriteUtf8(es)

      source.copyTo(target, bs.length.toLong(), (cs.length + ds.length).toLong())
      assertEquals(es + cs + ds, target.aReadUtf8())
    }
  }

  @Test
  fun copyToOffSegmentBoundary() {
    val bs = repeat('b', Segment.SIZE - 1)
    val cs = repeat('c', Segment.SIZE + 2)
    val ds = repeat('d', Segment.SIZE - 4)
    val es = repeat('e', Segment.SIZE + 8)

    val source = Buffer()
    source.writeUtf8(bs)
    source.writeUtf8(cs)
    source.writeUtf8(ds)

    val target = Buffer()
    target.writeUtf8(es)

    source.copyTo(target, bs.length.toLong(), (cs.length + ds.length).toLong())
    assertEquals(es + cs + ds, target.readUtf8())
  }

  @Test
  fun aCopyToOffSegmentBoundary() {
    runBlocking {
      val bs = repeat('b', Segment.SIZE - 1)
      val cs = repeat('c', Segment.SIZE + 2)
      val ds = repeat('d', Segment.SIZE - 4)
      val es = repeat('e', Segment.SIZE + 8)

      val source = Buffer()
      source.aWriteUtf8(bs)
      source.aWriteUtf8(cs)
      source.aWriteUtf8(ds)

      val target = Buffer()
      target.aWriteUtf8(es)

      source.copyTo(target, bs.length.toLong(), (cs.length + ds.length).toLong())
      assertEquals(es + cs + ds, target.aReadUtf8())
    }
  }

  @Test
  fun copyToSourceAndTargetCanBeTheSame() {
    val bs = repeat('b', Segment.SIZE)
    val cs = repeat('c', Segment.SIZE)

    val source = Buffer()
    source.writeUtf8(bs)
    source.writeUtf8(cs)

    source.copyTo(source, 0L, source.size())
    assertEquals(bs + cs + bs + cs, source.readUtf8())
  }

  @Test
  fun aCopyToSourceAndTargetCanBeTheSame() {
    runBlocking {
      val bs = repeat('b', Segment.SIZE)
      val cs = repeat('c', Segment.SIZE)

      val source = Buffer()
      source.aWriteUtf8(bs)
      source.aWriteUtf8(cs)

      source.copyTo(source, 0L, source.size())
      assertEquals(bs + cs + bs + cs, source.aReadUtf8())
    }
  }

  @Test
  fun copyToEmptySource() {
    val source = Buffer()
    val target = Buffer().writeUtf8("aaa")
    source.copyTo(target, 0L, 0L)
    assertEquals("", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test
  fun aCopyToEmptySource() {
    runBlocking {
      val source = Buffer()
      val target = Buffer().aWriteUtf8("aaa")
      source.copyTo(target, 0L, 0L)
      assertEquals("", source.aReadUtf8())
      assertEquals("aaa", target.aReadUtf8())
    }
  }

  @Test
  fun copyToEmptyTarget() {
    val source = Buffer().writeUtf8("aaa")
    val target = Buffer()
    source.copyTo(target, 0L, 3L)
    assertEquals("aaa", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test
  fun aCopyToEmptyTarget() {
    runBlocking {
      val source = Buffer().aWriteUtf8("aaa")
      val target = Buffer()
      source.copyTo(target, 0L, 3L)
      assertEquals("aaa", source.aReadUtf8())
      assertEquals("aaa", target.aReadUtf8())
    }
  }

  @Test
  fun snapshotReportsAccurateSize() {
    val buf = Buffer().write(byteArrayOf(0, 1, 2, 3))
    assertEquals(1, ByteString(buf.toByteArray(1L)).size())
  }

  @Test
  fun aSnapshotReportsAccurateSize() {
    runBlocking {
      val buf = Buffer().aWrite(byteArrayOf(0, 1, 2, 3))
      assertEquals(1, ByteString(buf.toByteArray(1L)).size())
    }
  }

}
