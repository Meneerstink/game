package gg.rsmod.util

/**
 * @authors Harley<github.com/HarleyGilpin> and Zed<github.com/CSS-Lletya>
 * Text Wrapping for NPC and Player Chat Interfaces.
 * wrapLength can be modified for other use cases.
 */
object TextWrapping {
    private const val SPACE = ' '
    private const val DEFAULT_LENGTH = 50

    @JvmStatic
    fun wrap(str: String?): Array<String>? {
        return wrap(str, DEFAULT_LENGTH)
    }

    @JvmStatic
    fun wrap(
        str: String?,
        wrapLength: Int,
    ): Array<String>? {
        var wrapLengthVar = wrapLength

        str ?: return null

        if (wrapLengthVar < 1) {
            wrapLengthVar = 1
        }

        val inputLineLength = str.length
        var offset = 0
        val wrappedLines = mutableListOf<String>()

        while (offset < inputLineLength) {
            val spaceToWrapAt = findSpaceToWrapAt(str, offset, wrapLengthVar)

            offset =
                if (spaceToWrapAt >= offset) {
                    wrappedLines.add(str.substring(offset, spaceToWrapAt))
                    spaceToWrapAt + 1
                } else {
                    wrappedLines.add(str.substring(offset))
                    inputLineLength
                }
        }

        return wrappedLines.toTypedArray()
    }

    private fun findSpaceToWrapAt(
        str: String,
        offset: Int,
        wrapLength: Int,
    ): Int {
        if (str.length - offset <= wrapLength) {
            return str.length
        }
        // N357 (owner 2026-10-24, the Bloodhound's last words cut off): break at the last space that keeps the line within
        // wrapLength - the first space after it let a line run ~10 characters past the dialogue box. A single longer word
        // still breaks at the first space after it.
        val before = str.lastIndexOf(SPACE, offset + wrapLength)
        if (before > offset) {
            return before
        }
        val spaceToWrapAt = str.indexOf(SPACE, offset + wrapLength)
        if (spaceToWrapAt == -1) {
            return str.length
        }
        return spaceToWrapAt
    }
}
