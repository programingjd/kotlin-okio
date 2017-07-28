package oknio

import java.nio.charset.Charset

internal fun checkOffsetAndCount(size: Long, offset: Long, byteCount: Long) {
  if (offset.or(byteCount) < 0 || offset > size || size - offset < byteCount) {
    throw ArrayIndexOutOfBoundsException("size=${size} offset=${offset} bytecount=${byteCount}")
  }
}

internal fun checkOffsetAndCount(size: Int, offset: Int, byteCount: Int) {
  if (offset.or(byteCount) < 0 || offset > size || size - offset < byteCount) {
    throw ArrayIndexOutOfBoundsException("size=${size} offset=${offset} bytecount=${byteCount}")
  }
}

internal fun arrayRangeEquals(a: ByteArray, aOffset: Int,
                              b: ByteArray, bOffset: Int,
                              byteCount: Int): Boolean {
  return (0 until byteCount).none { a[it + aOffset] != b[it + bOffset] }
}

internal val UTF_8 = Charset.forName("UTF-8")
