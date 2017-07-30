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

fun assertEquivalent(b1: ByteString, b2: ByteString) {
  assertTrue(b1 == b2)
  assertTrue(b1 == b1)
  assertTrue(b2 == b1)
  assertEquals(b1.hashCode(), b2.hashCode())
  assertEquals(b1.hashCode(), b1.hashCode())
  assertEquals(b1.toString(), b2.toString())
  assertEquals(b1.size(), b2.size())
  val b2Bytes = b2.toByteArray()
  for (i in b2Bytes.indices) {
    val b = b2Bytes[i]
    assertEquals(b, b1.getByte(i))
  }
  assertByteArraysEquals(b1.toByteArray(), b2Bytes)
  assertNotNull(b1)
  assertFalse(b1 == Any())
  if (b2Bytes.isNotEmpty()) {
    val b3Bytes = b2Bytes.clone()
    ++b3Bytes[b3Bytes.size - 1]
    val b3 = ByteString(b3Bytes)
    assertFalse(b1 == b3)
    assertFalse(b1.hashCode() == b3.hashCode())
  }
  else {
    val b3 = ByteString.encodeUtf8("a")
    assertFalse(b1 == b3)
    assertFalse(b1.hashCode() == b3.hashCode())
  }
}

fun assertEquivalent(b1: Buffer, b2: Buffer) {
  assertTrue(b1 == b2)
  assertTrue(b1 == b1)
  assertTrue(b2 == b1)
  assertEquals(b1.hashCode(), b2.hashCode())
  assertEquals(b1.hashCode(), b1.hashCode())
  assertEquals(b1.toString(), b2.toString())
  assertEquals(b1.size(), b2.size())
  val buffer = Buffer()
  b2.copyTo(buffer, 0, b2.size)
  val b2Bytes = b2.readByteArray()
  for (i in b2Bytes.indices) {
    val b = b2Bytes[i]
    assertEquals(b, b1.getByte(i.toLong()))
  }
  assertNotNull(b1)
  assertFalse(b1 == Any())
  if (b2Bytes.isNotEmpty()) {
    val b3Bytes = b2Bytes.clone()
    ++b3Bytes[b3Bytes.size - 1]
    val b3 = Buffer().write(b3Bytes)
    assertFalse(b1 == b3)
    assertFalse(b1.hashCode() == b3.hashCode())
  }
  else {
    val b3 = Buffer().writeUtf8("a")
    assertFalse(b1 == b3)
    assertFalse(b1.hashCode() == b3.hashCode())
  }
}
