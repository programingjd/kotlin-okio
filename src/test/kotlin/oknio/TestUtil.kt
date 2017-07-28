package oknio

import org.junit.Assert.*
import java.util.*

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
