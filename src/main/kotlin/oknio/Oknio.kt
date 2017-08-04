package oknio


class Oknio private constructor() {

  companion object {

    fun buffer(source: Source): BufferedSource = RealBufferedSource(source)

    //fun buffer(sink: Sink): BufferedSink = RealBufferedSink(sink)



  }

}
