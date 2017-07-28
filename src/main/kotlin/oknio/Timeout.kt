package oknio

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit


open class Timeout {

  companion object {
    val NONE: Timeout = object: Timeout() {
      override fun timeout(timeout: Long, unit: TimeUnit) = this
      override fun deadlineNanoTime(deadlineNanoTime: Long) = this
      override fun throwIfReached() {}
    }
  }

  private var hasDeadline = false
  private var deadlineNanoTime = 0L
  private var timeoutNanos = 0L

  open fun timeout(timeout: Long, unit: TimeUnit): Timeout {
    this.timeoutNanos = unit.toNanos(timeout)
    return this
  }

  open fun timeoutNanos(): Long {
    return timeoutNanos
  }

  open fun hasDeadline(): Boolean {
    return hasDeadline
  }

  open fun deadlineNanoTime(): Long {
    if (!hasDeadline) throw IllegalStateException("No deadline")
    return this.deadlineNanoTime
  }

  open fun deadlineNanoTime(deadlineNanoTime: Long): Timeout {
    this.hasDeadline = true
    this.deadlineNanoTime = deadlineNanoTime
    return this
  }

  fun deadline(duration: Long, unit: TimeUnit): Timeout {
    return deadlineNanoTime(System.nanoTime() + unit.toNanos(duration))
  }

  open fun clearTimeout(): Timeout {
    this.hasDeadline = false
    return this
  }

  @Throws(IOException::class)
  open fun throwIfReached() {
    if (Thread.interrupted()) {
      throw InterruptedIOException("thread interrupted")
    }
    if (this.hasDeadline && this.deadlineNanoTime - System.nanoTime() <= 0) {
      throw InterruptedIOException("deadline reached")
    }
  }

}
