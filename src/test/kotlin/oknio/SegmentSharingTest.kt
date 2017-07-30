package oknio

import org.junit.Test
import org.junit.Assert.*


class SegmentSharingTest {

  private val us = repeat('u', Segment.SIZE / 2 - 2)
  private val vs = repeat('v', Segment.SIZE / 2 - 1)
  private val ws = repeat('w', Segment.SIZE / 2)
  private val xs = repeat('x', Segment.SIZE / 2 + 1)
  private val ys = repeat('y', Segment.SIZE / 2 + 2)
  private val zs = repeat('z', Segment.SIZE / 2 + 3)

  @Test
  fun snapshotOfEmptyBuffer() {
    val snapshot = ByteString(Buffer().toByteArray())
    assertEquivalent(snapshot, ByteString.EMPTY)
  }

  @Test
  fun snapshotsAreEquivalent() {
    val byteString = ByteString(concatenateBuffers(xs, ys, zs).toByteArray())
    assertEquivalent(byteString, ByteString(concatenateBuffers(xs, ys + zs).toByteArray()))
    assertEquivalent(byteString, ByteString(concatenateBuffers(xs + ys + zs).toByteArray()))
    assertEquivalent(byteString, ByteString.encodeUtf8(xs + ys + zs))
  }

  @Test
  fun snapshotGetByte() {
    val byteString = ByteString(concatenateBuffers(xs, ys, zs).toByteArray())
    assertEquals('x', byteString.getByte(0).toChar())
    assertEquals('x', byteString.getByte(xs.length - 1).toChar())
    assertEquals('y', byteString.getByte(xs.length).toChar())
    assertEquals('y', byteString.getByte(xs.length + ys.length - 1).toChar())
    assertEquals('z', byteString.getByte(xs.length + ys.length).toChar())
    assertEquals('z', byteString.getByte(xs.length + ys.length + zs.length - 1).toChar())
    try {
      byteString.getByte(-1)
      fail()
    }
    catch (expected: IndexOutOfBoundsException) {}
    try {
      byteString.getByte(xs.length + ys.length + zs.length)
      fail()
    }
    catch (expected: IndexOutOfBoundsException) {}
  }

  @Test
  fun snapshotSegmentsAreNotRecycled() {
    val buffer = concatenateBuffers(xs, ys, zs)
    val snapshot = ByteString(buffer.toByteArray())
    assertEquals(xs + ys + zs, snapshot.utf8())
    synchronized(SegmentPool::class.java) {
      SegmentPool.next = null
      SegmentPool.byteCount = 0
      buffer.clear()
      assertNull(SegmentPool.next)
    }
  }

  @Test
  fun clonesAreEquivalent() {
    val bufferA = concatenateBuffers(xs, ys, zs)
    val bufferB = bufferA.clone()
    assertEquivalent(bufferA, bufferB)
    assertEquivalent(bufferA, concatenateBuffers(xs + ys, zs))
  }

  @Test
  fun mutateAfterClone() {
    val bufferA = Buffer()
    bufferA.writeUtf8("abc")
    val bufferB = bufferA.clone()
    bufferA.writeUtf8("def")
    bufferB.writeUtf8("DEF")
    assertEquals("abcdef", bufferA.readUtf8())
    assertEquals("abcDEF", bufferB.readUtf8())
  }

  @Test
  fun concatenateSegmentsCanCombine() {
    val bufferA = Buffer().writeUtf8(ys).writeUtf8(us)
    assertEquals(ys, bufferA.readUtf8(ys.length.toLong()))
    val bufferB = Buffer().writeUtf8(vs).writeUtf8(ws)
    val bufferC = bufferA.clone()
    bufferA.write(bufferB, vs.length.toLong())
    bufferC.writeUtf8(xs)

    assertEquals(us + vs, bufferA.readUtf8())
    assertEquals(ws, bufferB.readUtf8())
    assertEquals(us + xs, bufferC.readUtf8())
  }

  @Test
  fun shareAndSplit() {
    val bufferA = Buffer().writeUtf8("xxxx")
    val snapshot = ByteString(bufferA.toByteArray())
    val bufferB = Buffer()
    bufferB.write(bufferA, 2)
    bufferB.writeUtf8("yy")
    assertEquals("xxxx", snapshot.utf8())
  }

  @Test
  fun appendSnapshotToEmptyBuffer() {
    val bufferA = concatenateBuffers(xs, ys)
    val snapshot = ByteString(bufferA.toByteArray())
    val bufferB = Buffer()
    bufferB.write(snapshot)
    assertEquivalent(bufferB, bufferA)
  }

  @Test
  fun appendSnapshotToNonEmptyBuffer() {
    val bufferA = concatenateBuffers(xs, ys)
    val snapshot = ByteString(bufferA.toByteArray())
    val bufferB = Buffer().writeUtf8(us)
    bufferB.write(snapshot)
    assertEquivalent(bufferB, Buffer().writeUtf8(us + xs + ys))
  }

  @Test
  fun copyToSegmentSharing() {
    val bufferA = concatenateBuffers(ws, xs + "aaaa", ys, "bbbb" + zs)
    val bufferB = concatenateBuffers(us)
    bufferA.copyTo(bufferB, (ws.length + xs.length).toLong(), 4L + ys.length + 4L)
    assertEquivalent(bufferB, Buffer().writeUtf8(us + "aaaa" + ys + "bbbb"))
  }


  private fun concatenateBuffers(vararg segments: String): Buffer {
    val result = Buffer()
    for (s in segments) {
      val offsetInSegment = if (s.length < Segment.SIZE) (Segment.SIZE - s.length) / 2 else 0
      val buffer = Buffer()
      buffer.writeUtf8(repeat('_', offsetInSegment))
      buffer.writeUtf8(s)
      buffer.skip(offsetInSegment.toLong())
      result.write(buffer, buffer.size)
    }
    return result
  }

}

