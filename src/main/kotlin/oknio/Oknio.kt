package oknio


class Oknio private constructor() {

  companion object {

    fun buffer(source: Source): BufferedSource = RealBufferedSource(source)

    fun buffer(sink: Sink): BufferedSink = RealBufferedSink(sink)

    fun blackhole(): Sink {
      return object : Sink {
        override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
        suspend override fun aWrite(source: Buffer, byteCount: Long) = source.aSkip(byteCount)
        override fun flush() {}
        suspend override fun aFlush() {}
        override fun close() {}
        suspend override fun aClose() {}
        override fun timeout() = Timeout.NONE
      }
    }

  }

}
