package oknio

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*


class ByteStringTest {

  internal interface Factory {
    fun decodeHex(hex: String): ByteString
    fun encodeUtf8(s: String): ByteString

    companion object {
      val BYTE_STRING = object: Factory {
        override fun decodeHex(hex: String) = ByteString.decodeHex(hex)
        override fun encodeUtf8(s: String) = ByteString.encodeUtf8(s)
      }
      val ONE_BYTE_PER_SEGMENT = object: Factory {
        override fun decodeHex(hex: String) = makeSegments(ByteString.decodeHex(hex))
        override fun encodeUtf8(s: String) = makeSegments(ByteString.encodeUtf8(s))

        private fun makeSegments(source: ByteString): ByteString {
          val buffer = Buffer()
          for(i in 0 until source.size()) {
            val segment = buffer.writableSegment(Segment.SIZE)
            segment.data[segment.pos] = source.getByte(i)
            ++segment.limit
            ++buffer.size
          }
          return ByteString(buffer.toByteArray())
        }
      }
    }

  }

  internal val factories = listOf(Factory.BYTE_STRING, Factory.ONE_BYTE_PER_SEGMENT)

  @Test
  fun ofCopyRange() {
    val bytes = "Hello, World!".toByteArray(UTF_8)
    val byteString = ByteString.of(bytes, 2, 9)
    bytes[4] = 'a'.toByte()
    assertEquals("llo, Worl", byteString.utf8())
  }

  @Test fun ofByteBuffer() {
    val bytes = "Hello, World!".toByteArray(UTF_8)
    val byteBuffer = ByteBuffer.wrap(bytes)
    byteBuffer.position(2).limit(11)
    val byteString = ByteString.of(byteBuffer)
    byteBuffer.put(4, 'a'.toByte())
    assertEquals("llo, Worl", byteString.utf8())
  }

  @Test
  fun getByte() {
    factories.forEach {
      val byteString = it.decodeHex("ab12")
      assertEquals(-85, byteString.getByte(0).toInt())
      assertEquals(18, byteString.getByte(1).toInt())
    }
  }

  @Test
  fun getByteOutOfBounds() {
    factories.forEach {
      val byteString = it.decodeHex("ab12")
      try {
        byteString.getByte(2)
        fail()
      }
      catch (expected: IndexOutOfBoundsException) {}
    }
  }

  @Test
  fun startsWithByteString() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertTrue(byteString.startsWith(ByteString.decodeHex("")))
      assertTrue(byteString.startsWith(ByteString.decodeHex("11")))
      assertTrue(byteString.startsWith(ByteString.decodeHex("1122")))
      assertTrue(byteString.startsWith(ByteString.decodeHex("112233")))
      assertFalse(byteString.startsWith(ByteString.decodeHex("2233")))
      assertFalse(byteString.startsWith(ByteString.decodeHex("11223344")))
      assertFalse(byteString.startsWith(ByteString.decodeHex("112244")))
    }
  }

  @Test
  fun endsWithByteString() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertTrue(byteString.endsWith(ByteString.decodeHex("")))
      assertTrue(byteString.endsWith(ByteString.decodeHex("33")))
      assertTrue(byteString.endsWith(ByteString.decodeHex("2233")))
      assertTrue(byteString.endsWith(ByteString.decodeHex("112233")))
      assertFalse(byteString.endsWith(ByteString.decodeHex("1122")))
      assertFalse(byteString.endsWith(ByteString.decodeHex("00112233")))
      assertFalse(byteString.endsWith(ByteString.decodeHex("002233")))
    }
  }

  @Test
  fun startsWithByteArray() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertTrue(byteString.startsWith(ByteString.decodeHex("").toByteArray()))
      assertTrue(byteString.startsWith(ByteString.decodeHex("11").toByteArray()))
      assertTrue(byteString.startsWith(ByteString.decodeHex("1122").toByteArray()))
      assertTrue(byteString.startsWith(ByteString.decodeHex("112233").toByteArray()))
      assertFalse(byteString.startsWith(ByteString.decodeHex("2233").toByteArray()))
      assertFalse(byteString.startsWith(ByteString.decodeHex("11223344").toByteArray()))
      assertFalse(byteString.startsWith(ByteString.decodeHex("112244").toByteArray()))
    }
  }

  @Test
  fun endsWithByteArray() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertTrue(byteString.endsWith(ByteString.decodeHex("").toByteArray()))
      assertTrue(byteString.endsWith(ByteString.decodeHex("33").toByteArray()))
      assertTrue(byteString.endsWith(ByteString.decodeHex("2233").toByteArray()))
      assertTrue(byteString.endsWith(ByteString.decodeHex("112233").toByteArray()))
      assertFalse(byteString.endsWith(ByteString.decodeHex("1122").toByteArray()))
      assertFalse(byteString.endsWith(ByteString.decodeHex("00112233").toByteArray()))
      assertFalse(byteString.endsWith(ByteString.decodeHex("002233").toByteArray()))
    }
  }

  @Test
  fun indexOfByteString() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("1122")))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("11")))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("11"), 0))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("")))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex(""), 0))
      assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233")))
      assertEquals(1, byteString.indexOf(ByteString.decodeHex("22")))
      assertEquals(1, byteString.indexOf(ByteString.decodeHex("22"), 1))
      assertEquals(1, byteString.indexOf(ByteString.decodeHex(""), 1))
      assertEquals(2, byteString.indexOf(ByteString.decodeHex("33")))
      assertEquals(2, byteString.indexOf(ByteString.decodeHex("33"), 2))
      assertEquals(2, byteString.indexOf(ByteString.decodeHex(""), 2))
      assertEquals(3, byteString.indexOf(ByteString.decodeHex(""), 3))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("44")))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("11223344")))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244")))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("2233"), 2))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("33"), 3))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex(""), 4))
    }
  }

  @Test
  fun indexOfWithOffset() {
    factories.forEach {
      val byteString = it.decodeHex("112233112233")
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), -1))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), 0))
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")))
      assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 1))
      assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 2))
      assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 3))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 4))
    }
  }

  @Test
  fun indexOfByteArray() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233").toByteArray()))
      assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233").toByteArray()))
      assertEquals(2, byteString.indexOf(ByteString.decodeHex("33").toByteArray()))
      assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244").toByteArray()))
    }
  }

  @Test
  fun lastIndexOfByteString() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233")))
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("1122")))
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11")))
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 3))
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 0))
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex(""), 0))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233")))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22")))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 3))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 1))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex(""), 1))
      assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33")))
      assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 3))
      assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 2))
      assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex(""), 2))
      assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex(""), 3))
      assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("")))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -1))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -2))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("44")))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("11223344")))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112244")))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("2233"), 0))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("33"), 1))
      assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex(""), -1))
    }
  }

  @Test
  fun lastIndexOfByteArray() {
    factories.forEach {
      val byteString = it.decodeHex("112233")
      assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233").toByteArray()))
      assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233").toByteArray()))
      assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33").toByteArray()))
      assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("").toByteArray()))
    }
  }

  @Test
  fun equals() {
    factories.forEach {
      val byteString = it.decodeHex("000102")
      assertTrue(byteString == byteString)
      assertTrue(byteString == ByteString.decodeHex("000102"))
      assertTrue(it.decodeHex("") == ByteString.EMPTY)
      assertTrue(it.decodeHex("") == ByteString.of())
      assertTrue(ByteString.EMPTY == it.decodeHex(""))
      assertTrue(ByteString.of() == it.decodeHex(""))
      assertFalse(byteString == Any())
      assertFalse(byteString == ByteString.decodeHex("000201"))
    }
  }

  private val bronzeHorseman = "На берегу пустынных волн"

  @Test
  fun utf8() {
    factories.forEach {
      val byteString = it.encodeUtf8(bronzeHorseman)
      assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(UTF_8))
      assertTrue(byteString == ByteString.of(bronzeHorseman.toByteArray(UTF_8)))
      assertEquals(byteString.utf8(), bronzeHorseman)
    }
  }

  @Test
  fun encodeDecodeStringUtf8() {
    val byteString = ByteString.encodeString(bronzeHorseman, UTF_8)
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(UTF_8))
    assertEquals(byteString, ByteString.decodeHex(
      "d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181" +
      "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd"
    ))
    assertEquals(bronzeHorseman, byteString.string(UTF_8))
  }

  @Test
  fun encodeDecodeStringUtf16be() {
    val utf16be = Charset.forName("UTF-16BE")
    val byteString = ByteString.encodeString(bronzeHorseman, utf16be)
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(utf16be))
    assertEquals(byteString, ByteString.decodeHex(
      "041d043000200431043504400435043304430020043f0443" +
      "04410442044b043d043d044b044500200432043e043b043d"
    ))
    assertEquals(bronzeHorseman, byteString.string(utf16be))
  }

  @Test
  fun encodeDecodeStringUtf32be() {
    val utf32be = Charset.forName("UTF-32BE")
    val byteString = ByteString.encodeString(bronzeHorseman, utf32be)
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(utf32be))
    assertEquals(byteString, ByteString.decodeHex(
      "0000041d0000043000000020000004310000043500000440" +
      "000004350000043300000443000000200000043f0000044300000441000004420000044b0000043d0000043d" +
      "0000044b0000044500000020000004320000043e0000043b0000043d"
    ))
    assertEquals(bronzeHorseman, byteString.string(utf32be))
  }

  @Test
  fun encodeDecodeStringAsciiIsLossy() {
    val ascii = Charset.forName("US-ASCII")
    val byteString = ByteString.encodeString(bronzeHorseman, ascii)
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.toByteArray(ascii))
    assertEquals(byteString, ByteString.decodeHex(
      "3f3f203f3f3f3f3f3f203f3f3f3f3f3f3f3f3f203f3f3f3f"
    ))
    assertEquals("?? ?????? ????????? ????", byteString.string(ascii))
  }

  @Test
  fun decodeMalformedStringReturnsReplacementCharacter() {
    val utf16be = Charset.forName("UTF-16BE")
    val string = ByteString.decodeHex("04").string(utf16be)
    assertEquals("\ufffd", string)
  }

  @Test
  fun testHashCode() {
    factories.forEach {
      val byteString = it.decodeHex("0102")
      assertEquals(byteString.hashCode(), byteString.hashCode())
      assertEquals(byteString.hashCode(), ByteString.decodeHex("0102").hashCode())
    }
  }

  @Test
  fun toAsciiLowerCaseNoUppercase() {
    factories.forEach {
      val s = it.encodeUtf8("a1_+")
      assertEquals(s, s.toAsciiLowercase())
      if (it === Factory.BYTE_STRING) {
        assertSame(s, s.toAsciiLowercase())
      }
    }
  }

  @Test
  fun toAsciiAllUppercase() {
    factories.forEach {
      assertEquals(ByteString.encodeUtf8("ab"), it.encodeUtf8("AB").toAsciiLowercase())
    }
  }

  @Test
  fun toAsciiStartsLowercaseEndsUppercase() {
    factories.forEach {
      assertEquals(ByteString.encodeUtf8("abcd"), it.encodeUtf8("abCD").toAsciiLowercase())
    }
  }

  @Test
  fun toAsciiStartsUppercaseEndsLowercase() {
    factories.forEach {
      assertEquals(ByteString.encodeUtf8("ABCD"), it.encodeUtf8("ABcd").toAsciiUppercase())
    }
  }

  @Test
  fun substring() {
    factories.forEach {
      val byteString = it.encodeUtf8("Hello, World!")
      assertEquals(byteString.substring(0), byteString)
      assertEquals(byteString.substring(0, 5), ByteString.encodeUtf8("Hello"))
      assertEquals(byteString.substring(7), ByteString.encodeUtf8("World!"))
      assertEquals(byteString.substring(6, 6), ByteString.encodeUtf8(""))
    }
  }

  @Test
  fun substringWithInvalidBounds() {
    factories.forEach {
      val byteString = it.encodeUtf8("Hello, World!")
      try {
        byteString.substring(-1)
        fail()
      }
      catch (expected: IllegalArgumentException) {}
      try {
        byteString.substring(0, 14)
        fail()
      }
      catch (expected: IllegalArgumentException) {}
      try {
        byteString.substring(8, 7)
        fail()
      }
      catch (expected: IllegalArgumentException) {}
    }
  }

  @Test
  fun encodeHex() {
    assertEquals("000102", ByteString.of(byteArrayOf(0x0.toByte(), 0x1.toByte(), 0x2.toByte())).hex())
  }

  @Test
  fun decodeHex() {
    assertEquals(ByteString.of(byteArrayOf(0x0.toByte(), 0x1.toByte(), 0x2.toByte())),
                 ByteString.decodeHex("000102"))
  }

  @Test
  fun decodeHexOddNumberOfChars() {
    try {
      ByteString.decodeHex("aaa")
      fail()
    }
    catch (expected: IllegalArgumentException) {}
  }

  @Test
  fun decodeHexInvalidChar() {
    try {
      ByteString.decodeHex("a\u0000")
      fail()
    }
    catch (expected: IllegalArgumentException) {}
  }

  @Test fun toStringOnEmpty() {
    factories.forEach {
      assertEquals("[size=0]", it.decodeHex("").toString())
    }
  }

  @Test fun toStringOnShortText() {
    factories.forEach {
      assertEquals("[text=Tyrannosaur]", it.encodeUtf8("Tyrannosaur").toString())
      assertEquals("[text=təˈranəˌsôr]", it.decodeHex("74c999cb8872616ec999cb8c73c3b472").toString())
    }
  }

  @Test fun toStringOnLongTextIsTruncated() {
    factories.forEach {
      val raw = "Um, I'll tell you the problem with the scientific power that you're using here, " +
                "it didn't require any discipline to attain it. You read what others had done and you " +
                "took the next step. You didn't earn the knowledge for yourselves, so you don't take any " +
                "responsibility for it. You stood on the shoulders of geniuses to accomplish something " +
                "as fast as you could, and before you even knew what you had, you patented it, and " +
                "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna " +
                "sell it."
      assertEquals("[size=517 text=Um, I'll tell you the problem with the scientific power that " + "you...]",
                   it.encodeUtf8(raw).toString())
    }
  }

  @Test fun toStringOnTextWithNewlines() {
    factories.forEach {
      assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]", it.encodeUtf8("a\r\nb\nc\rd\\e").toString())
    }
  }

  @Test
  fun toStringOnData() {
    factories.forEach {
      val byteString = it.decodeHex(
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb"
      )
      assertEquals(
        "[hex=" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb]",
        byteString.toString()
      )
    }
  }

  @Test
  fun toStringOnLongDataIsTruncated() {
    factories.forEach {
      val byteString = it.decodeHex(
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afba1"
      )
      assertEquals(
        "[size=65 hex=" +
        "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
        "4bf0b54023c29b624de9ef9c2f931efc580f9afb...]",
        byteString.toString()
      )
    }
  }

  @Test
  fun compareToSingleBytes() {
    factories.forEach {
      val originalByteStrings = Arrays.asList(
        it.decodeHex("00"),
        it.decodeHex("01"),
        it.decodeHex("7e"),
        it.decodeHex("7f"),
        it.decodeHex("80"),
        it.decodeHex("81"),
        it.decodeHex("fe"),
        it.decodeHex("ff")
      )
      val sortedByteStrings = ArrayList<ByteString>(originalByteStrings)
      Collections.shuffle(sortedByteStrings, Random(0L))
      Collections.sort(sortedByteStrings)
      assertEquals(originalByteStrings, sortedByteStrings)
    }
  }

  @Test
  fun compareToMultipleBytes() {
    factories.forEach {
      val originalByteStrings = listOf(
        it.decodeHex(""),
        it.decodeHex("00"),
        it.decodeHex("0000"),
        it.decodeHex("000000"),
        it.decodeHex("00000000"),
        it.decodeHex("0000000000"),
        it.decodeHex("0000000001"),
        it.decodeHex("000001"),
        it.decodeHex("00007f"),
        it.decodeHex("0000ff"),
        it.decodeHex("000100"),
        it.decodeHex("000101"),
        it.decodeHex("007f00"),
        it.decodeHex("00ff00"),
        it.decodeHex("010000"),
        it.decodeHex("010001"),
        it.decodeHex("01007f"),
        it.decodeHex("0100ff"),
        it.decodeHex("010100"),
        it.decodeHex("01010000"),
        it.decodeHex("0101000000"),
        it.decodeHex("0101000001"),
        it.decodeHex("010101"),
        it.decodeHex("7f0000"),
        it.decodeHex("7f0000ffff"),
        it.decodeHex("ffffff")
      )
      val sortedByteStrings = ArrayList<ByteString>(originalByteStrings)
      Collections.shuffle(sortedByteStrings, Random(0L))
      Collections.sort(sortedByteStrings)
      assertEquals(originalByteStrings, sortedByteStrings)
    }
  }

  @Test fun asByteBuffer() {
    assertEquals(
      0x42.toByte(),
      ByteString.of(byteArrayOf(0x41.toByte(), 0x42.toByte(), 0x43.toByte())).asByteBuffer().get(1)
    )
  }

}
