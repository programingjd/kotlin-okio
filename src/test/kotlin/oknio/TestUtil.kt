package oknio

import org.junit.Assert.*
import java.util.*
import java.io.IOException
import java.util.Random


fun assertByteArraysEquals(a: ByteArray, b: ByteArray) = assertEquals(Arrays.toString(a), Arrays.toString(b))

fun assertByteArrayEquals(expectedUtf8: String, b: ByteArray) = assertEquals(expectedUtf8, String(b, UTF_8))

fun randomBytes(length: Int): ByteString {
  val random = Random(0L)
  val randomBytes = ByteArray(length)
  random.nextBytes(randomBytes)
  return ByteString.of(randomBytes)
}

fun repeat(c: Char, count: Int): String {
  val array = CharArray(count)
  Arrays.fill(array, c)
  return String(array)
}

@Throws(IOException::class)
fun bufferWithRandomSegmentLayout(dice: Random, data: ByteArray): Buffer {
  val result = Buffer()
  var pos = 0
  var byteCount: Int
  while (pos < data.size) {
    byteCount = Segment.SIZE / 2 + dice.nextInt(Segment.SIZE / 2)
    if (byteCount > data.size - pos) byteCount = data.size - pos
    val offset = dice.nextInt(Segment.SIZE - byteCount)
    val segment = Buffer()
    segment.write(ByteArray(offset))
    segment.write(data, pos, byteCount)
    segment.skip(offset.toLong())
    result.write(segment, byteCount.toLong())
    pos += byteCount
  }
  return result
}
