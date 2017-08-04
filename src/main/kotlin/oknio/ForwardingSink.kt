package oknio


abstract class ForwardingSink(private val delegate: Sink): Sink {

  fun delegate() = delegate

  override fun write(source: Buffer, byteCount: Long) = delegate.write(source, byteCount)

  suspend override fun aWrite(source: Buffer, byteCount: Long) = delegate.aWrite(source, byteCount)

  override fun flush() = delegate.flush()

  suspend override fun aFlush() = delegate.aFlush()

  override fun timeout() = delegate.timeout()

  override fun close() = delegate.close()

  suspend override fun aClose() = delegate.aClose()


}
