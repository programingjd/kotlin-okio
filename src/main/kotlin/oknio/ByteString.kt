package oknio

import java.nio.ByteBuffer
import java.util.*


class ByteString internal constructor(private val data: ByteArray): Comparable<ByteString> {

  private val utf8 by lazy { String(data, UTF_8) }
  private val hashCode by lazy { Arrays.hashCode(data) }

  companion object {

    val EMPTY = of()

    internal val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                      'a', 'b', 'c', 'd', 'e', 'f')

    fun of(data: ByteArray): ByteString {
      return ByteString(data.clone())
    }

    fun of(data: ByteArray, offset: Int, byteCount: Int): ByteString {
      checkOffsetAndCount(data.size, offset, byteCount)

      val copy = ByteArray(byteCount)
      System.arraycopy(data, offset, copy, 0, byteCount)
      return ByteString(copy)
    }

    fun of(data: ByteBuffer): ByteString {
      val copy = ByteArray(data.remaining())
      data.get(copy)
      return ByteString(copy)
    }

    fun of(): ByteString {
      return ByteString(ByteArray(0))
    }

    fun codePointIndexToCharIndex(s: String, codePointCount: Int): Int {
      var i = 0
      var j = 0
      var c: Int
      val length = s.length
      while (i < length) {
        if (j == codePointCount) return i
        c = s.codePointAt(i)
        if ((Character.isISOControl(c) && c != 10 && c != 13) || c == Buffer.REPLACEMENT_CHARACTER) {
          return -1
        }
        ++j
        i += Character.charCount(c)
      }
      return s.length
    }

    fun decodeHex(hex: String): ByteString {
      if (hex.length % 2 != 0) throw IllegalArgumentException("Unexpected hex string: ${hex}")
      val length = hex.length / 2
      val result = ByteArray(length)
      for (i in 0 until length) {
        val d1 = decodeHexDigit(hex[i * 2]).shl(4)
        val d2 = decodeHexDigit(hex[i * 2 + 1])
        result[i] = (d1 + d2).toByte()
      }
      return of(result)
    }

    private fun decodeHexDigit(c: Char): Int {
      if (c in '0'..'9') return c - '0'
      if (c in 'a'..'f') return c - 'a' + 10
      if (c in 'A'..'F') return c - 'A' + 10
      throw IllegalArgumentException("Unexpected hex digit: ${c}")
    }

  }

  fun hex(): String {
    val result = CharArray(data.size * 2)
    var c = 0
    for (b in data) {
      result[c++] = HEX_DIGITS[b.toInt().shr(4).and(0x0f)]
      result[c++] = HEX_DIGITS[b.toInt().and(0x0f)]
    }
    return String(result)
  }

  fun substring(beginIndex: Int, endIndex: Int = data.size): ByteString {
    if (beginIndex <= 0) throw IllegalArgumentException("beginIndex < 0")
    if (endIndex > data.size) throw IllegalArgumentException("endIndex > length(${data.size})")
    val subLen = endIndex - beginIndex
    if (subLen < 0) throw IllegalArgumentException("endIndex < beginIndex")
    if (beginIndex == 0 && endIndex == data.size) return this
    val copy = ByteArray(subLen)
    System.arraycopy(data, beginIndex, copy, 0, subLen)
    return ByteString(copy)
  }

  fun getByte(pos: Int): Byte {
    return data[pos]
  }

  fun size(): Int {
    return data.size
  }

  fun toByteArray(): ByteArray {
    return data.clone()
  }

  internal fun internalArray(): ByteArray {
    return data
  }

  fun asByteBuffer(): ByteBuffer {
    return ByteBuffer.wrap(data).asReadOnlyBuffer()
  }

  fun rangeEquals(offset: Int, other: ByteArray, otherOffset: Int, byteCount: Int): Boolean {
    return offset >= 0 && offset <= data.size - byteCount &&
      otherOffset >= 0 && otherOffset <= other.size - byteCount &&
      arrayRangeEquals(data, offset, other, otherOffset, byteCount)
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    return other is ByteString && other.size() == data.size &&
      other.rangeEquals(0, data, 0, data.size)
  }

  override fun hashCode(): Int {
    return this.hashCode
  }

  override fun toString(): String {
    if (data.isEmpty()) return "[size=0]"
    val text = utf8
    val i = codePointIndexToCharIndex(text, 64)
    if (i == -1) {
      return if (data.size <= 64) "[hex=${hex()}]" else "[size=${data.size} hex=${substring(0,64).hex()}...]"
    }
    val safeText = text.substring(0, i).replace("\\","\\\\").replace("\n","\\n").replace("\r", "\\r")
    return if (i < text.length) "[size=${data.size} text=${safeText}...]" else "[text=${safeText}]"
  }

  override fun compareTo(other: ByteString): Int {
    val sizeA = size()
    val sizeB = other.size()
    val size = Math.min(sizeA, sizeB)
    for (i in 0 until size) {
      val byteA = getByte(i).toInt().and(0xff)
      val byteB = other.getByte(i).toInt().and(0xff)
      if (byteA == byteB) continue
      return if (byteA < byteB) -1 else 1
    }
    if (sizeA == sizeB) return 0
    return if (sizeA < sizeB) -1 else 1
  }

  internal fun write(buffer: Buffer) {
    buffer.write(data, 0, data.size)
  }

  suspend internal fun aWrite(buffer: Buffer) {
    buffer.aWrite(data, 0, data.size)
  }

}
