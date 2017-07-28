package oknio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class SegmentPool private constructor() {

  internal companion object {

    val MAX_SIZE = 128 * 1024

    var next: Segment? = null

    var byteCount = 0

    private val lock = ReentrantLock()

    fun take(): Segment {
      lock.withLock {
        val next = next
        if (next != null) {
          val result = next
          this.next = result.next
          result.next = null
          byteCount -= Segment.SIZE
          return result
        }
      }
      return Segment()
    }

    fun recycle(segment: Segment) {
      if (segment.next != null || segment.prev != null) throw IllegalArgumentException()
      if (segment.shared) return
      lock.withLock {
        if (byteCount + Segment.SIZE > MAX_SIZE) return
        byteCount += Segment.SIZE
        segment.next = next
        segment.pos = 0
        segment.limit = 0
        next = segment
      }
    }

  }

}
