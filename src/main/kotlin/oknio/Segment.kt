package oknio

import java.nio.ByteBuffer


internal class Segment private constructor(val data: ByteArray,
                                           internal var pos: Int,
                                           internal var limit: Int,
                                           internal var shared: Boolean,
                                           internal var owner: Boolean) {
  constructor(): this(ByteArray(SIZE), 0, 0, false, true)
  constructor(data: ByteArray, pos: Int, limit: Int): this(data, pos, limit, true, false)
  constructor(shareFrom: Segment): this(shareFrom.data, shareFrom.pos, shareFrom.limit, c(shareFrom), false)

  val buffer = ByteBuffer.wrap(data)
  var next: Segment? = null
  var prev: Segment? = null

  internal companion object {

    val SIZE = 8192
    val SHARE_MINIMUM = 1024

    private fun c(shareFrom: Segment): Boolean {
      shareFrom.shared = true
      return true
    }

  }

  fun pop(): Segment? {
    val prev = prev ?: throw NullPointerException()
    val next = next ?: throw NullPointerException()
    val result = if (next != this) next else null
    prev.next = next
    next.prev = prev
    this.next = null
    this.prev = null
    return result
  }

  fun push(segment: Segment): Segment {
    segment.prev = this
    val next = next ?: throw NullPointerException()
    segment.next = next
    next.prev = segment
    this.next = segment
    return segment
  }

  fun split(byteCount: Int): Segment {
    if (byteCount <= 0 || byteCount > limit - pos) throw IllegalArgumentException()
    val prefix: Segment
    if (byteCount >= SHARE_MINIMUM) {
      prefix = Segment(this)
    }
    else {
      prefix = SegmentPool.take()
      System.arraycopy(data, pos, prefix.data, 0, byteCount)
    }

    prefix.limit = prefix.pos + byteCount
    pos += byteCount
    val prev = prev ?: throw NullPointerException()
    prev.push(prefix)
    return prefix
  }

  fun compact() {
    val prev = prev ?: throw NullPointerException()
    if (prev == this) throw IllegalStateException()
    if (!prev.owner) return
    val byteCount = limit - pos
    val availableByteCount = SIZE - prev.limit + (if (prev.shared) 0 else prev.pos)
    if (byteCount > availableByteCount) return
    writeTo(prev, byteCount)
    pop()
    SegmentPool.recycle(this)
  }

  fun writeTo(sink: Segment, byteCount: Int) {
    if (!sink.owner) throw IllegalArgumentException()
    if (sink.limit + byteCount > SIZE) {
      if (sink.shared) throw IllegalArgumentException()
      if (sink.limit + byteCount - sink.pos > SIZE) throw IllegalArgumentException()
      System.arraycopy(sink.data, sink.pos, sink.data, 0, sink.limit - sink.pos)
      sink.limit -= sink.pos
      sink.pos = 0
    }
    System.arraycopy(data, pos, sink.data, sink.limit, byteCount)
    sink.limit += byteCount
    pos += byteCount
  }

  suspend fun aWriteTo(sink: Segment, byteCount: Int) = writeTo(sink, byteCount)

}
