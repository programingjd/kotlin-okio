package oknio

import java.io.IOException
import java.util.LinkedHashMap
import java.util.ArrayList
import org.junit.Assert.*





class MockSink: Sink {

  private val log = ArrayList<String>()
  private val callThrows = LinkedHashMap<Int, IOException>()

  fun assertLog(vararg messages: String) {
    assertArrayEquals(listOf(*messages).toTypedArray(), log.toTypedArray())
  }

  fun assertLogContains(message: String) {
    assertTrue(log.contains(message))
  }

  fun scheduleThrow(call: Int, e: IOException) {
    callThrows.put(call, e)
  }

  @Throws(IOException::class)
  private fun throwIfScheduled() {
    val exception = callThrows[log.size - 1]
    if (exception != null) throw exception
  }

  override fun write(source: Buffer, byteCount: Long) {
    log.add("write($source, $byteCount)")
    source.skip(byteCount)
    throwIfScheduled()
  }

  suspend override fun aWrite(source: Buffer, byteCount: Long) {
    log.add("write($source, $byteCount)")
    source.aSkip(byteCount)
    throwIfScheduled()
  }

  override fun flush() {
    log.add("flush()")
    throwIfScheduled()
  }

  suspend override fun aFlush() {
    log.add("flush()")
    throwIfScheduled()
  }

  override fun close() {
    log.add("close()")
    throwIfScheduled()
  }

  suspend override fun aClose() {
    log.add("close()")
    throwIfScheduled()
  }

  override fun timeout(): Timeout {
    log.add("timeout()")
    return Timeout.NONE
  }

}
