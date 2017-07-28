package oknio

import org.junit.Test
import org.junit.Assert.*


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
  fun completeSegmentByteCountOnBufferWithIncompleteTailSegment() {
    val buffer = Buffer()
    buffer.writeUtf8(repeat('a', Segment.SIZE * 4 - 10))
    assertEquals(Segment.SIZE * 3L, buffer.completeSegmentByteCount())
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
  fun moveBytesBetweenBuffersShareSegment() {
    val size = Segment.SIZE / 2 - 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(size * 2), segmentSizes)
  }

  @Test
  fun moveBytesBetweenBuffersReassignSegment() {
    val size = Segment.SIZE / 2 + 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(size, size), segmentSizes)
  }

  @Test
  fun moveBytesBetweenBuffersMultipleSegments() {
    val size = 3 * Segment.SIZE + 1
    val segmentSizes = moveBytesBetweenBuffers(repeat('a', size), repeat('b', size))
    assertEquals(listOf(Segment.SIZE, Segment.SIZE, Segment.SIZE, 1,
                        Segment.SIZE, Segment.SIZE, Segment.SIZE, 1), segmentSizes)
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

}
