package oknio


class Utf8 private constructor() {

  companion object {

    fun size(string: String): Long {
      return size(string, 0, string.length)
    }

    fun size(string: String, beginIndex: Int, endIndex: Int): Long {
      if (beginIndex < 0) throw IllegalArgumentException("beginIndex < 0: ${beginIndex}")
      if (endIndex < beginIndex) {
        throw IllegalArgumentException("endIndex < beginIndex: ${endIndex} < ${beginIndex}")
      }
      if (endIndex > string.length) {
        throw IllegalArgumentException("endIndex > string.length: ${endIndex} > ${string.length}")
      }

      var result = 0L
      var i = beginIndex
      while (i < endIndex) {
        val c = string[i].toInt()
        if (c < 0x80) {
          ++result
          ++i
        }
        else if (c < 0x0800) {
          result += 2
          ++i
        }
        else if (c < 0xd800 || c > 0xdfff) {
          result += 3
          ++i
        }
        else {
          val low = i + (if (1 < endIndex) string[i+1].toInt() else 0)
          if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
            ++result
            ++i
          }
          else {
            result += 4
            i += 2
          }
        }
      }
      return result
    }

  }

}
