package oknio


abstract class ForwardingSource(private val delegate: Source): Source {

  fun delegate() = delegate

  override fun read(sink: Buffer, byteCount: Long) = delegate.read(sink, byteCount)

  suspend override fun aRead(sink: Buffer, byteCount: Long) = delegate.aRead(sink, byteCount)

  override fun timeout() = delegate.timeout()

  override fun close() = delegate.close()

  suspend override fun aClose() = delegate.aClose()

}
