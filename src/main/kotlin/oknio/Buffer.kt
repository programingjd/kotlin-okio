package oknio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*


class Buffer: BufferedSource, BufferedSink, Cloneable {

  companion object {

    private val DIGITS = byteArrayOf('0'.toByte(), '1'.toByte(), '2'.toByte(), '3'.toByte(), '4'.toByte(),
                                     '5'.toByte(), '6'.toByte(), '7'.toByte(), '8'.toByte(), '9'.toByte(),
                                     'a'.toByte(), 'b'.toByte(), 'c'.toByte(), 'd'.toByte(), 'e'.toByte(),
                                     'f'.toByte())

    internal val REPLACEMENT_CHARACTER = '\ufffd'.toInt()

  }

  internal var head: Segment? = null
  internal var size: Long = 0L

  fun size() = size

  override fun buffer() = this

  override fun outputStream(): OutputStream {
    return object: OutputStream() {
      override fun write(source: Int) {
        writeByte(source)
      }
      override fun write(source: ByteArray, offset: Int, length: Int) {
        this@Buffer.write(source, offset, length)
      }
      override fun toString(): String {
        return "${this@Buffer}.outputStream()"
      }
    }
  }

  override fun inputStream(): InputStream {
    return object: InputStream() {
      override fun read(): Int {
        return if (size > 0L) readByte().toInt().and(0xff) else -1
      }
      override fun read(sink: ByteArray, offset: Int, lenght: Int): Int {
        return this@Buffer.read(sink, offset, lenght)
      }
      override fun available(): Int {
        return if (size > Integer.MAX_VALUE) Integer.MAX_VALUE else size.toInt()
      }
      override fun toString(): String {
        return "${this@Buffer}.inputStream()"
      }
    }
  }

  override fun emitCompleteSegments() = this

  suspend override fun aEmitCompleteSegments() = emitCompleteSegments()

  override fun emit() = this

  suspend override fun aEmit() = emit()

  override fun exhausted() = size == 0L

  suspend override fun aExhausted() = exhausted()

  @Throws(EOFException::class)
  override fun require(byteCount: Long) {
    if (size < byteCount) throw EOFException()
  }

  suspend override fun aRequire(byteCount: Long) = require(byteCount)

  override fun request(byteCount: Long) = size >= byteCount

  suspend override fun aRequest(byteCount: Long) = request(byteCount)

  fun copyTo(output: OutputStream): Buffer = copyTo(output, 0L, size)

  fun copyTo(output: OutputStream, offset: Long, byteCount: Long): Buffer {
    checkOffsetAndCount(size, offset, byteCount)
    if (byteCount == 0L) return this
    var s = head ?: throw NullPointerException()
    var off = offset
    while (off >= s.limit - s.pos) {
      off -= s.limit - s.pos
      s = s.next ?: throw NullPointerException()
    }
    var count = byteCount
    while (count > 0L) {
      val pos = s.pos + off
      val toCopy = Math.min(s.limit - pos, count)
      output.write(s.data, pos.toInt(), toCopy.toInt())
      count -= toCopy
      off = 0
      if (count > 0L)  s = s.next ?: throw NullPointerException()
    }
    return this
  }

  fun copyTo(output: Buffer, offset: Long, byteCount: Long): Buffer {
    checkOffsetAndCount(size, offset, byteCount)
    if (byteCount == 0L) return this
    output.size += byteCount
    var s = head ?: throw NullPointerException()
    var off = offset
    while (off >= s.limit - s.pos) {
      off -= s.limit - s.pos
      s = s.next ?: throw NullPointerException()
    }
    var count = byteCount
    while (count > 0L) {
      val copy = Segment(s)
      copy.pos += off.toInt()
      copy.limit = Math.min(copy.pos + count.toInt(), copy.limit)
      if (output.head == null) {
        copy.prev = copy
        copy.next = copy.prev
        output.head = copy.next
      }
      else {
        ((output.head ?: throw NullPointerException()).prev ?: throw NullPointerException()).push(copy)
      }
      count -= copy.limit - copy.pos
      off = 0
      if (count > 0L) s = s.next ?: throw NullPointerException()
    }
    return this
  }

  @Throws(IOException::class)
  fun writeTo(output: OutputStream) = writeTo(output, size)

  @Throws(IOException::class)
  fun writeTo(output: OutputStream, byteCount: Long): Buffer {
    checkOffsetAndCount(size, 0L, byteCount)
    var s = head ?: throw NullPointerException()
    var count = byteCount
    while (count > 0L) {
      val toCopy = Math.min(count, (s.limit - s.pos).toLong()).toInt()
      output.write(s.data, s.pos, toCopy)
      s.pos += toCopy
      size -= toCopy
      count -= toCopy
      if (s.pos == s.limit) {
        val toRecycle = s
        val s2 = toRecycle.pop()
        this.head = s2
        SegmentPool.recycle(toRecycle)
        if (count > 0L) s = s2 ?: throw NullPointerException()
      }
    }
    return this
  }

  @Throws(IOException::class)
  fun readFrom(input: InputStream): Buffer {
    readFrom(input, Long.MAX_VALUE, true)
    return this
  }

  @Throws(IOException::class)
  fun readFrom(input: InputStream, byteCount: Long): Buffer {
    readFrom(input, byteCount, false)
    return this
  }

  @Throws(IOException::class)
  fun readFrom(input: InputStream, byteCount: Long, forever: Boolean) {
    var count = byteCount
    while (count > 0L || forever) {
      val tail = writableSegment(1)
      val maxToCopy = Math.min(count, (Segment.SIZE - tail.limit).toLong()).toInt()
      val bytesRead = input.read(tail.data, tail.limit, maxToCopy)
      if (bytesRead == -1) {
        if (forever) return
        throw EOFException()
      }
      tail.limit += bytesRead
      size += bytesRead
      count -= bytesRead
    }
  }

  fun completeSegmentByteCount(): Long {
    var result = size
    if (result == 0L) return 0L
    val tail = head?.prev ?: throw NullPointerException()
    if (tail.limit < Segment.SIZE && tail.owner) {
      result -= tail.limit - tail.pos
    }
    return result
  }

  override fun readByte(): Byte {
    if (size == 0L) throw IllegalStateException("size == 0")

    val segment = head ?: throw NullPointerException()
    val pos = segment.pos
    val limit = segment.limit

    val b = segment.data[pos]
    size -= 1
    if (pos + 1 == limit) {
      head = segment.pop()
      SegmentPool.recycle(segment)
    }
    else {
      segment.pos = pos + 1
    }
    return b
  }

  suspend override fun aReadByte(): Byte = readByte()

  fun getByte(pos: Long): Byte {
    checkOffsetAndCount(size, pos, 1L)
    var s = head ?: throw NullPointerException()
    var p = pos
    while (true) {
      val segmentByteCount = s.limit - s.pos
      if (p < segmentByteCount) return s.data[s.pos + p.toInt()]
      p -= segmentByteCount
      s = s.next ?: throw NullPointerException()
    }
  }

  override fun readByteString() = ByteString(readByteArray())

  suspend override fun aReadByteString() = ByteString(aReadByteArray())

  override fun readByteString(byteCount: Long) = ByteString(readByteArray(byteCount))

  suspend override fun aReadByteString(byteCount: Long) = ByteString(aReadByteArray(byteCount))

  override fun readByteArray(): ByteArray {
    try {
      return readByteArray(size)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  suspend override fun aReadByteArray(): ByteArray {
    try {
      return aReadByteArray(size)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  override fun readByteArray(byteCount: Long): ByteArray {
    checkOffsetAndCount(size, 0L, byteCount)
    if (byteCount > Integer.MAX_VALUE) {
      throw IllegalArgumentException("bytecount > Integer.MAX_VALUE: ${byteCount}")
    }
    val result = ByteArray(byteCount.toInt())
    readFully(result)
    return result
  }

  suspend override fun aReadByteArray(byteCount: Long): ByteArray {
    checkOffsetAndCount(size, 0L, byteCount)
    if (byteCount > Integer.MAX_VALUE) {
      throw IllegalArgumentException("bytecount > Integer.MAX_VALUE: ${byteCount}")
    }
    val result = ByteArray(byteCount.toInt())
    aReadFully(result)
    return result
  }

  override fun read(sink: ByteArray) = read(sink, 0, sink.size)

  suspend override fun aRead(sink: ByteArray) = aRead(sink, 0, sink.size)

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
    checkOffsetAndCount(sink.size, offset, byteCount)
    val s = head ?: return -1
    val toCopy = Math.min(byteCount, s.limit - s.pos)
    System.arraycopy(s.data, s.pos, sink, offset, toCopy)
    s.pos += toCopy
    size -= toCopy
    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }
    return toCopy
  }

  suspend override fun aRead(sink: ByteArray, offset: Int, byteCount: Int) = read(sink, offset, byteCount)

  override fun readFully(sink: ByteArray) {
    var offset = 0
    while (offset < sink.size) {
      val read = read(sink, offset, sink.size - offset)
      if (read == -1) throw EOFException()
      offset += read
    }
  }

  suspend override fun aReadFully(sink: ByteArray) {
    var offset = 0
    while (offset < sink.size) {
      val read = aRead(sink, offset, sink.size - offset)
      if (read == -1) throw EOFException()
      offset += read
    }
  }

  fun clear() {
    try {
      skip(size)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  suspend fun aClear() {
    try {
      aSkip(size)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  override fun readUtf8(): String {
    try {
      return readString(size, UTF_8)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  suspend override fun aReadUtf8(): String {
    try {
      return aReadString(size, UTF_8)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  override fun readUtf8(byteCount: Long): String {
    try {
      return readString(byteCount, UTF_8)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  suspend override fun aReadUtf8(byteCount: Long): String {
    try {
      return aReadString(byteCount, UTF_8)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  override fun readString(charset: Charset): String {
    try {
      return readString(size, charset)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  suspend override fun aReadString(charset: Charset): String {
    try {
      return aReadString(size, charset)
    }
    catch (e: EOFException) {
      throw AssertionError(e)
    }
  }

  override fun readString(byteCount: Long, charset: Charset): String {
    checkOffsetAndCount(size, 0, byteCount)
    if (byteCount > Integer.MAX_VALUE) {
      throw IllegalArgumentException("byteCount > Integer.MAX_VALUE: ${byteCount}")
    }
    if (byteCount == 0L) return ""
    val s = head ?: throw NullPointerException()
    if (s.pos + byteCount > s.limit) {
      return String(readByteArray(byteCount), charset)
    }
    val result = String(s.data, s.pos, byteCount.toInt(), charset)
    s.pos += byteCount.toInt()
    size -= byteCount
    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }
    return result
  }

  suspend override fun aReadString(byteCount: Long, charset: Charset): String {
    checkOffsetAndCount(size, 0, byteCount)
    if (byteCount > Integer.MAX_VALUE) {
      throw IllegalArgumentException("byteCount > Integer.MAX_VALUE: ${byteCount}")
    }
    if (byteCount == 0L) return ""
    val s = head ?: throw NullPointerException()
    if (s.pos + byteCount > s.limit) {
      return String(aReadByteArray(byteCount), charset)
    }
    val result = String(s.data, s.pos, byteCount.toInt(), charset)
    s.pos += byteCount.toInt()
    size -= byteCount
    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }
    return result
  }

  override fun skip(byteCount: Long) {
    var count = byteCount
    while (count > 0L) {
      val head = head ?: throw EOFException()
      val toSkip = Math.min(count, (head.limit - head.pos).toLong()).toInt()
      size -= toSkip
      count -= toSkip
      head.pos += toSkip
      if (head.pos == head.limit) {
        val toRecycle = head
        this.head = toRecycle.pop()
        SegmentPool.recycle(toRecycle)
      }
    }
  }

  suspend override fun aSkip(byteCount: Long) = skip(byteCount)

  override fun write(byteString: ByteString): Buffer {
    byteString.write(this)
    return this
  }

  suspend override fun aWrite(byteString: ByteString): Buffer {
    byteString.aWrite(this)
    return this
  }

  override fun write(source: ByteArray) = write(source, 0, source.size)

  suspend override fun aWrite(source: ByteArray) = aWrite(source, 0, source.size)

  override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer {
    checkOffsetAndCount(source.size, offset, byteCount)
    val limit = offset + byteCount
    var off = offset
    while (off < limit) {
      val tail = writableSegment(1)
      val toCopy = Math.min(limit - off, Segment.SIZE - tail.limit)
      System.arraycopy(source, off, tail.data, tail.limit, toCopy)
      off += toCopy
      tail.limit += toCopy
    }
    size += byteCount
    return this
  }

  suspend override fun aWrite(source: ByteArray, offset: Int,
                              byteCount: Int) = write(source, offset, byteCount)

  override fun writeAll(source: Source): Long {
    var totalBytesRead = 0L
    while (true) {
      val readCount = source.read(this, Segment.SIZE.toLong())
      if (readCount == -1L) break
      totalBytesRead += readCount
    }
    return totalBytesRead
  }

  suspend override fun aWriteAll(source: Source): Long {
    var totalBytesRead = 0L
    while (true) {
      val readCount = source.aRead(this, Segment.SIZE.toLong())
      if (readCount == -1L) break
      totalBytesRead += readCount
    }
    return totalBytesRead
  }

  override fun write(source: Source, byteCount: Long): Buffer {
    var count = byteCount
    while (count > 0L) {
      val read = source.read(this, count)
      if (read == -1L) throw EOFException()
      count -= read
    }
    return this
  }

  suspend override fun aWrite(source: Source, byteCount: Long): Buffer {
    var count = byteCount
    while (count > 0L) {
      val read = source.aRead(this, count)
      if (read == -1L) throw EOFException()
      count -= read
    }
    return this
  }

  override fun writeByte(b: Int): Buffer {
    val tail = writableSegment(1)
    tail.data[tail.limit++] = b.toByte()
    size += 1
    return this
  }

  suspend override fun aWriteByte(b: Int) = writeByte(b)

  override fun writeUtf8(string: String) = writeUtf8(string, 0, string.length)

  suspend override fun aWriteUtf8(string: String) = aWriteUtf8(string, 0, string.length)

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer {
    if (beginIndex < 0) throw IllegalArgumentException("beginIndex < 0: ${beginIndex}")
    if (endIndex < beginIndex) {
      throw IllegalArgumentException("endIndex < beginIndex: ${endIndex} < ${beginIndex}")
    }
    if (endIndex > string.length) {
      throw IllegalArgumentException("endIndex > string.length: ${endIndex} > ${string.length}")
    }
    var i = beginIndex
    while (i < endIndex) {
      var c = string[i].toInt()
      if (c < 0x80) {
        val tail = writableSegment(1)
        val data = tail.data
        val segmentOffset = tail.limit - i
        val runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset)
        data[segmentOffset + i++] = c.toByte()
        while (i < runLimit) {
          c = string[i].toInt()
          if (c >= 0x80) break
          data[segmentOffset + i++] = c.toByte()
        }
        val runSize = i + segmentOffset - tail.limit
        tail.limit += runSize
        size += runSize
      }
      else if (c < 0x0800) {
        writeByte(c.shr(6).or(0xc0))
        writeByte(c.and(0x3f).or(0x80))
        ++i
      }
      else if (c < 0xd800 || c > 0xdfff) {
        writeByte(c.shr(12).or(0xe0))
        writeByte(c.shr(6).and(0x3f).or(0x80))
        writeByte(c.and(0x3f).or(0x80))
        ++i
      }
      else {
        val low = if (i + 1 < endIndex) string[i+1].toInt() else 0
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          writeByte(63) // '?'
          ++i
          continue
        }
        val codePoint = 0x010000 + ((c.and(0xd800.inv()).shl(10).or(low).and(0xdc00.inv())))
        writeByte(codePoint.shr(18).or(0xf0))
        writeByte(codePoint.shr(12).and(0x3f).or(0x80))
        writeByte(codePoint.shr(6).and(0x3f).or(0x80))
        writeByte(codePoint.and(0x3f).or(0x80))
        i += 2
      }
    }
    return this
  }

  suspend override fun aWriteUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer {
    if (beginIndex < 0) throw IllegalArgumentException("beginIndex < 0: ${beginIndex}")
    if (endIndex < beginIndex) {
      throw IllegalArgumentException("endIndex < beginIndex: ${endIndex} < ${beginIndex}")
    }
    if (endIndex > string.length) {
      throw IllegalArgumentException("endIndex > string.length: ${endIndex} > ${string.length}")
    }
    var i = beginIndex
    while (i < endIndex) {
      var c = string[i].toInt()
      if (c < 0x80) {
        val tail = writableSegment(1)
        val data = tail.data
        val segmentOffset = tail.limit - i
        val runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset)
        data[segmentOffset + i++] = c.toByte()
        while (i < runLimit) {
          c = string[i].toInt()
          if (c >= 0x80) break
          data[segmentOffset + i++] = c.toByte()
        }
        val runSize = i + segmentOffset - tail.limit
        tail.limit += runSize
        size += runSize
      }
      else if (c < 0x0800) {
        aWriteByte(c.shr(6).or(0xc0))
        aWriteByte(c.and(0x3f).or(0x80))
        ++i
      }
      else if (c < 0xd800 || c > 0xdfff) {
        aWriteByte(c.shr(12).or(0xe0))
        aWriteByte(c.shr(6).and(0x3f).or(0x80))
        aWriteByte(c.and(0x3f).or(0x80))
        ++i
      }
      else {
        val low = if (i + 1 < endIndex) string[i+1].toInt() else 0
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          aWriteByte(63) // '?'
          ++i
          continue
        }
        val codePoint = 0x010000 + ((c.and(0xd800.inv()).shl(10).or(low).and(0xdc00.inv())))
        aWriteByte(codePoint.shr(18).or(0xf0))
        aWriteByte(codePoint.shr(12).and(0x3f).or(0x80))
        aWriteByte(codePoint.shr(6).and(0x3f).or(0x80))
        aWriteByte(codePoint.and(0x3f).or(0x80))
        i += 2
      }
    }
    return this
  }

  override fun writeUtf8CodePoint(codePoint: Int): Buffer {
    if (codePoint < 0x80) {
      writeByte(codePoint)
    }
    else if (codePoint < 0x0800) {
      writeByte(codePoint.shr(6).or(0xc0))
      writeByte(codePoint.and(0x3f).or(0x80))
    }
    else if (codePoint < 0x010000) {
      if (codePoint in 0xd800..0xdfff) {
        writeByte(63) // '?'
      }
      else {
        writeByte(codePoint.shr(12).or(0xe0))
        writeByte(codePoint.shr(6).and(0x3f).or(0x80))
        writeByte(codePoint.and(0x3f).or(0x80))
      }
    }
    else if (codePoint < 0x10ffff) {
      writeByte(codePoint.shr(18).or(0xf0))
      writeByte(codePoint.shr(12).and(0x3f).or(0x80))
      writeByte(codePoint.shr(6).and(0x3f).or(0x80))
      writeByte(codePoint.and(0x3f).or(0x80))
    }
    else {
      throw IllegalArgumentException("Unexpected code point: ${Integer.toHexString(codePoint)}")
    }
    return this
  }

  suspend override fun aWriteUtf8CodePoint(codePoint: Int): Buffer {
    if (codePoint < 0x80) {
      aWriteByte(codePoint)
    }
    else if (codePoint < 0x0800) {
      aWriteByte(codePoint.shr(6).or(0xc0))
      aWriteByte(codePoint.and(0x3f).or(0x80))
    }
    else if (codePoint < 0x010000) {
      if (codePoint in 0xd800..0xdfff) {
        aWriteByte(63) // '?'
      }
      else {
        aWriteByte(codePoint.shr(12).or(0xe0))
        aWriteByte(codePoint.shr(6).and(0x3f).or(0x80))
        aWriteByte(codePoint.and(0x3f).or(0x80))
      }
    }
    else if (codePoint < 0x10ffff) {
      aWriteByte(codePoint.shr(18).or(0xf0))
      aWriteByte(codePoint.shr(12).and(0x3f).or(0x80))
      aWriteByte(codePoint.shr(6).and(0x3f).or(0x80))
      aWriteByte(codePoint.and(0x3f).or(0x80))
    }
    else {
      throw IllegalArgumentException("Unexpected code point: ${Integer.toHexString(codePoint)}")
    }
    return this
  }

  override fun writeString(string: String, charset: Charset) = writeString(string, 0, string.length, charset)

  suspend override fun aWriteString(string: String,
                                    charset: Charset) = aWriteString(string, 0, string.length, charset)

  override fun writeString(string: String, beginIndex: Int, endIndex: Int, charset: Charset): Buffer {
    if (beginIndex < 0) throw IllegalArgumentException("beginIndex < 0: ${beginIndex}")
    if (endIndex < beginIndex) {
      throw IllegalArgumentException("endIndex < beginIndex: ${endIndex} < ${beginIndex}")
    }
    if (endIndex > string.length) {
      throw IllegalArgumentException("endIndex > string.length: ${endIndex} > ${string.length}")
    }
    if (charset == UTF_8) return writeUtf8(string, beginIndex, endIndex)
    val data = string.substring(beginIndex, endIndex).toByteArray(charset)
    return write(data, 0, data.size)
  }

  suspend override fun aWriteString(string: String, beginIndex: Int, endIndex: Int,
                                    charset: Charset): Buffer {
    if (beginIndex < 0) throw IllegalArgumentException("beginIndex < 0: ${beginIndex}")
    if (endIndex < beginIndex) {
      throw IllegalArgumentException("endIndex < beginIndex: ${endIndex} < ${beginIndex}")
    }
    if (endIndex > string.length) {
      throw IllegalArgumentException("endIndex > string.length: ${endIndex} > ${string.length}")
    }
    if (charset == UTF_8) return aWriteUtf8(string, beginIndex, endIndex)
    val data = string.substring(beginIndex, endIndex).toByteArray(charset)
    return aWrite(data, 0, data.size)
  }

  internal fun writableSegment(minimumCapacity: Int): Segment {
    if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) throw IllegalArgumentException()
    val head = this.head
    if (head == null) {
      val newHead = SegmentPool.take()
      newHead.prev = newHead
      newHead.next = newHead.prev
      this.head = newHead
      return newHead
    }
    val tail = head.prev ?: throw NullPointerException()
    if (tail.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
      return tail.push(SegmentPool.take())
    }
    return tail
  }

  override fun write(source: Buffer, byteCount: Long) {
    if (source == this) throw IllegalArgumentException("source == this")
    checkOffsetAndCount(source.size, 0L, byteCount)
    var count = byteCount
    while (count > 0L) {
      var sourceHead = source.head ?: throw NullPointerException()
      if (count < sourceHead.limit - sourceHead.pos) {
        val tail = head?.prev
        if (tail != null && tail.owner &&
            count + tail.limit - (if (tail.shared) 0 else tail.pos) <= Segment.SIZE) {
          sourceHead.writeTo(tail, count.toInt())
          source.size -= count
          size += count
          return
        }
        else {
          sourceHead = sourceHead.split(count.toInt())
          source.head = sourceHead
        }
      }
      val segmentToMove = sourceHead
      val movedByteCount = segmentToMove.limit - segmentToMove.pos
      source.head = segmentToMove.pop()
      val head = head
      if (head == null) {
        segmentToMove.prev = segmentToMove
        segmentToMove.next = segmentToMove
        this.head = segmentToMove
      }
      else {
        val headPrev = head.prev ?: throw NullPointerException()
        headPrev.push(segmentToMove).compact()
      }
      source.size -= movedByteCount
      size += movedByteCount
      count -= movedByteCount
    }
  }

  suspend override fun aWrite(source: Buffer, byteCount: Long) = write(source, byteCount)

  override fun read(sink: Buffer, byteCount: Long): Long {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (size == 0L) return -1L
    val count = if (byteCount > size) size else byteCount
    sink.write(this, count)
    return count
  }

  suspend override fun aRead(sink: Buffer, byteCount: Long): Long {
    if (byteCount < 0L) throw IllegalArgumentException("byteCount < 0: ${byteCount}")
    if (size == 0L) return -1L
    val count = if (byteCount > size) size else byteCount
    sink.aWrite(this, count)
    return count
  }

  override fun readFully(sink: Buffer, byteCount: Long) {
    if (size < byteCount) {
      sink.write(this, size)
      throw EOFException()
    }
    sink.write(this, byteCount)
  }

  suspend override fun aReadFully(sink: Buffer, byteCount: Long) {
    if (size < byteCount) {
      sink.aWrite(this, size)
      throw EOFException()
    }
    sink.aWrite(this, byteCount)
  }

  override fun readAll(sink: Sink): Long {
    val byteCount = size
    if (byteCount > 0L) sink.write(this, byteCount)
    return byteCount
  }

  suspend override fun aReadAll(sink: Sink): Long {
    val byteCount = size
    if (byteCount > 0L) sink.aWrite(this, byteCount)
    return byteCount
  }

  override fun indexOf(b: Byte) = indexOf(b, 0L, Long.MAX_VALUE)

  suspend override fun aIndexOf(b: Byte) = aIndexOf(b, 0L, Long.MAX_VALUE)

  override fun indexOf(b: Byte, from: Long) = indexOf(b, from, Long.MAX_VALUE)

  suspend override fun aIndexOf(b: Byte, from: Long) = aIndexOf(b, from, Long.MAX_VALUE)

  override fun indexOf(b: Byte, from: Long, to: Long): Long {
    if (from < 0 || to < from) throw IllegalArgumentException("size=${size} fromIndex=${from} toIndex=${to}")
    val to2 = if (to > size) size else to
    if (from == to) return -1L
    var s = head ?: return -1L
    var offset: Long
    if (size - from < from) {
      offset = size
      while (offset > from) {
        s = s.prev ?: throw NullPointerException()
        offset -= s.limit - s.pos
      }
    }
    else {
      offset = 0L
      var nextOffset = offset + s.limit - s.pos
      while (nextOffset < from) {
        s = s.next ?: throw NullPointerException()
        offset = nextOffset
        nextOffset = offset + s.limit - s.pos
      }
    }
    var from2 = from
    while (offset < to2) {
      val data = s.data
      var pos = (s.pos + from2 - offset).toInt()
      while (pos < Math.min(s.limit.toLong(), s.pos + to - offset).toInt()) {
        if (data[pos] == b) {
          return pos - s.pos + offset
        }
        ++pos
      }
      offset += s.limit - s.pos
      from2 = offset
      if (offset < to2) s = s.next ?: throw NullPointerException()
    }
    return -1L
  }

  suspend override fun aIndexOf(b: Byte, from: Long, to: Long) = indexOf(b, from, to)

  override fun indexOf(b: ByteString) = indexOf(b, 0L)

  suspend override fun aIndexOf(b: ByteString) = aIndexOf(b, 0L)

  override fun indexOf(b: ByteString, from: Long): Long {
    val bytesSize = b.size()
    if (bytesSize == 0) throw IllegalArgumentException("bytestring is empty")
    if (from < 0L) throw IllegalArgumentException("fromIndex < 0")
    var s = head ?: return -1L
    var offset: Long
    if (size - from < from) {
      offset = size
      while (offset > from) {
        s = s.prev ?: throw NullPointerException()
        offset -= s.limit - s.pos
      }
    }
    else {
      offset = 0L
      var nextOffset = offset + s.limit - s.pos
      while (nextOffset < from) {
        s = s.next ?: throw NullPointerException()
        offset = nextOffset
        nextOffset = offset + s.limit - s.pos
      }
    }
    val b0 = b.getByte(0)
    val to2 = size - bytesSize + 1
    var from2 = from
    while (offset < to2) {
      val data = s.data
      var pos = (s.pos + from2 - offset).toInt()
      while (pos < Math.min(s.limit.toLong(), s.pos + to2 - offset).toInt()) {
        if (data[pos] == b0 && rangeEquals(s, pos + 1, b, 1, bytesSize)) {
          return pos - s.pos + offset
        }
        ++pos
      }
      offset += s.limit - s.pos
      from2 = offset
      if (offset < to2) s = s.next ?: throw NullPointerException()
    }
    return -1L
  }

  suspend override fun aIndexOf(b: ByteString, from: Long) = indexOf(b, from)

  private fun rangeEquals(segment: Segment, segmentPos: Int, b: ByteString,
                          bOffset: Int, bytesLimit: Int): Boolean {
    var s = segment
    var pos = segmentPos
    var limit = segment.limit
    for (i in bOffset until bytesLimit) {
      if (pos == limit) {
        s = s.next ?: throw NullPointerException()
        pos = s.pos
        limit = s.limit
      }
      if (s.data[pos] != b.getByte(i)) return false
      ++pos
    }
    return true
  }

  override fun rangeEquals(offset: Long, b: ByteString) = rangeEquals(offset, b, 0, b.size())

  suspend override fun aRangeEquals(offset: Long, b: ByteString) = aRangeEquals(offset, b, 0, b.size())

  override fun rangeEquals(offset: Long, b: ByteString, bOffset: Int, byteCount: Int): Boolean {
    if (offset < 0L || bOffset < 0 || byteCount < 0 || size - offset < byteCount ||
        b.size() - bOffset < byteCount) return false
    return (0 until byteCount).none { getByte(offset + it) != b.getByte(bOffset + it) }
  }

  suspend override fun aRangeEquals(offset: Long, b: ByteString, bOffset: Int,
                                    byteCount: Int) = rangeEquals(offset, b, bOffset, byteCount)

  override fun close() {}

  suspend override fun aClose() {}

  override fun flush() {}

  suspend override fun aFlush() {}

  override fun timeout() = Timeout.NONE

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other is Buffer) {
      if (size != other.size) return false
      if (size == 0L) return true

      var sa = this.head ?: throw NullPointerException()
      var sb = other.head ?: throw NullPointerException()
      var posA = sa.pos
      var posB = sb.pos
      var pos = 0L
      while (pos < size) {
        val count = Math.min(sa.limit - posA, sb.limit - posB)
        for (i in 0 until count) {
          if (sa.data[posA++] != sb.data[posB++]) return false
        }
        if (posA == sa.limit) {
          sa = sa.next ?: throw NullPointerException()
          posA = sa.pos
        }
        if (posB == sb.limit) {
          sb = sb.next ?: throw NullPointerException()
          posB = sb.pos
        }
        pos += count
      }
      return true
    }
    else {
      return false
    }
  }

  override fun hashCode(): Int {
    var s = head ?: return 0
    var result = 1
    while (true) {
      val limit = s.limit
      for (pos in s.pos until limit) {
        result = 31 * result + s.data[pos]
      }
      s = s.next ?: throw NullPointerException()
      if (s == head) break
    }
    return result
  }

  override fun toString(): String {
    return ByteString(toByteArray()).toString()
  }

  internal fun toByteArray() = toByteArray(size)

  internal fun toByteArray(byteCount: Long): ByteArray {
    checkOffsetAndCount(size, 0L, byteCount)
    if (size == 0L) return ByteArray(0)
    var offset = 0
    var segmentCount = 0
    var s = head ?: throw NullPointerException()
    while (offset < byteCount) {
      if (s.limit == s.pos) throw AssertionError("s.limit == s.pos")
      offset += s.limit - s.pos
      ++segmentCount
      if (offset < byteCount) s = s.next ?: throw NullPointerException()
    }
    val segments = Array<ByteArray?>(segmentCount, { null })
    val directory = IntArray(segmentCount * 2)
    offset = 0
    segmentCount = 0
    s = head ?: throw NullPointerException()
    while (offset < byteCount) {
      segments[segmentCount] = s.data
      offset += s.limit - s.pos
      if (offset > byteCount) offset = byteCount.toInt()
      directory[segmentCount] = offset
      directory[segmentCount + segments.size] = s.pos
      s.shared = true
      ++segmentCount
      if (offset < byteCount) s = s.next ?: throw NullPointerException()
    }
    offset = 0
    segmentCount = segments.size
    val result = ByteArray(directory[segmentCount - 1])
    for (i in 0 until segmentCount) {
      val segmentPos = directory[segmentCount + i]
      val nextOffset = directory[i]
      System.arraycopy(segments[i], segmentPos, result, offset, nextOffset - offset)
      offset = nextOffset
    }
    return result
  }

  override public fun clone(): Buffer {
    val result = Buffer()
    if (size == 0L) return result
    val head = head ?: throw NullPointerException()
    val resultHead = Segment(head)
    resultHead.prev = resultHead
    resultHead.next = resultHead
    result.head = resultHead
    var s = head.next ?: throw NullPointerException()
    while (s != head) {
      ((result.head ?: throw NullPointerException()).prev ?: throw NullPointerException()).push(Segment(s))
      s = s.next ?: throw NullPointerException()
    }
    result.size = size
    return result
  }

  internal fun segmentSizes(): List<Int> {
    val head = head ?: return Collections.emptyList()
    val result = ArrayList<Int>()
    result.add(head.limit - head.pos)
    var s = head.next ?: throw NullPointerException()
    while (s != head) {
      result.add(s.limit - s.pos)
      s = s.next ?: throw NullPointerException()
    }
    return result
  }

}
