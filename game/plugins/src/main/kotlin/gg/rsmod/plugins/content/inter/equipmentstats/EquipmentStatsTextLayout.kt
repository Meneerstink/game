package gg.rsmod.plugins.content.inter.equipmentstats

/**
 * Builds the three cache-owned text columns used by interface 667's item-statistics panel.
 * Every operation advances all columns exactly once so section titles, labels and values cannot
 * drift onto different baselines.
 */
class EquipmentStatsTextLayout {
    private val titles = StringBuilder()
    private val names = StringBuilder()
    private val values = StringBuilder()

    fun title(text: String) {
        titles.append(text).append(BREAK)
        names.append(BREAK)
        values.append(BREAK)
    }

    fun row(
        label: String,
        value: String,
    ) {
        titles.append(BREAK)
        names.append(label).append(':').append(BREAK)
        values.append(value).append(BREAK)
    }

    fun columns(): Columns =
        Columns(
            titles = titles.toString(),
            names = names.toString(),
            values = values.toString(),
        )

    data class Columns(
        val titles: String,
        val names: String,
        val values: String,
    )

    private companion object {
        const val BREAK = "<br>"
    }
}
