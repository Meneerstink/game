package gg.rsmod.plugins.content.drops

/**
 * Represents a drop table.
 * @param name      The name of this table.
 * @param entries   The drop entries.
 * @param totalSlots
 * The table size the script declared with `total(n)`, or 0 when it declared none. This is the
 * denominator a drop is rolled against, so slots the script never filled are a chance of nothing -
 * which is what a table like `total(1024)` with a handful of rare items has always meant. Before
 * this field existed the declared total was only validated and then thrown away, and the roll used
 * the *occupied* slot count instead, silently making every drop in an under-filled table far more
 * common than the script said.
 */
data class DropTable(
    val name: String?,
    val entries: Array<TableBuilder.Entry>,
    val totalSlots: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DropTable

        if (name != other.name) return false
        if (totalSlots != other.totalSlots) return false
        if (!entries.contentEquals(other.entries)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + entries.contentHashCode()
        result = 31 * result + totalSlots
        return result
    }
}
