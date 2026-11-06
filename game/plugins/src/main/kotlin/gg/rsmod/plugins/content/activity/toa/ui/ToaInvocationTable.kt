package gg.rsmod.plugins.content.activity.toa.ui

/**
 * N462: the 44 standard ToA invocations in enum 4664 order (OSRS cache 2686 enum 4664 -> structs; param 1160 name, 1161 category,
 * 1162 raid level, 1159 bit; ChatGPT-A N462-notities/toa/10-invocations.tsv). Category 15 (Blazing Tombs, Event/Leagues) is left out
 * like a normal OSRS world (CS2 6609/6749). Exclusive groups (only one at a time): Attempts 3, Time Limit 4, Helpful Spirit 5, Path
 * Level 6 (10-invocations.txt "SELECTIE"); requirements: Arterial Spray / Blood Thinners -> Not Just a Head, Overclocked 2 ->
 * Overclocked, Insanity -> Overclocked 2.
 */
object ToaInvocationTable {
    class Entry(val order: Int, val name: String, val category: Int, val raidLevel: Int, val bit: Int, val requires: String?)

    val ENTRIES: List<Entry> =
        listOf(
            Entry(0, "Try Again", 3, 5, 1, null), Entry(1, "Persistence", 3, 10, 2, null),
            Entry(2, "Softcore Run", 3, 15, 3, null), Entry(3, "Hardcore Run", 3, 25, 4, null),
            Entry(4, "Walk for It", 4, 10, 5, null), Entry(5, "Jog for It", 4, 15, 6, null),
            Entry(6, "Run for It", 4, 20, 7, null), Entry(7, "Sprint for It", 4, 25, 8, null),
            Entry(8, "Need Some Help?", 5, 15, 9, null), Entry(9, "Need Less Help?", 5, 25, 10, null),
            Entry(10, "No Help Needed", 5, 40, 11, null), Entry(11, "Walk the Path", 9, 50, 12, null),
            Entry(12, "Pathseeker", 6, 15, 13, null), Entry(13, "Pathfinder", 6, 40, 14, null),
            Entry(14, "Pathmaster", 6, 50, 15, null), Entry(15, "Quiet Prayers", 7, 20, 16, null),
            Entry(16, "Deadly Prayers", 7, 20, 17, null), Entry(17, "On a Diet", 8, 15, 18, null),
            Entry(18, "Dehydration", 8, 30, 19, null), Entry(19, "Overly Draining", 8, 15, 20, null),
            Entry(20, "Lively Larvae", 11, 5, 21, null), Entry(21, "More Overlords", 11, 15, 22, null),
            Entry(22, "Blowing Mud", 11, 10, 23, null), Entry(23, "Medic!", 11, 15, 24, null),
            Entry(24, "Aerial Assault", 11, 10, 25, null), Entry(25, "Not Just a Head", 12, 15, 26, null),
            Entry(26, "Arterial Spray", 12, 10, 27, "Not Just a Head"), Entry(27, "Blood Thinners", 12, 5, 28, "Not Just a Head"),
            Entry(28, "Upset Stomach", 12, 15, 29, null), Entry(29, "Double Trouble", 10, 20, 30, null),
            Entry(30, "Keep Back", 10, 10, 31, null), Entry(31, "Stay Vigilant", 10, 15, 32, null),
            Entry(32, "Feeling Special?", 10, 20, 33, null), Entry(33, "Mind the Gap!", 13, 10, 34, null),
            Entry(34, "Gotta Have Faith", 13, 10, 35, null), Entry(35, "Jungle Japes", 13, 5, 36, null),
            Entry(36, "Shaking Things Up", 13, 10, 37, null), Entry(37, "Boulderdash", 13, 10, 38, null),
            Entry(38, "Ancient Haste", 14, 10, 39, null), Entry(39, "Acceleration", 14, 10, 40, null),
            Entry(40, "Penetration", 14, 10, 41, null), Entry(41, "Overclocked", 14, 10, 42, null),
            Entry(42, "Overclocked 2", 14, 10, 43, "Overclocked"), Entry(43, "Insanity", 14, 50, 44, "Overclocked 2"),
        )

    private val EXCLUSIVE = setOf(3, 4, 5, 6)
    fun byName(name: String): Entry? = ENTRIES.firstOrNull { it.name == name }

    /** The OSRS toggle: activating drops the other one of an exclusive group; deactivating drops everything that required it. */
    fun toggle(selected: Set<String>, name: String): Set<String> {
        val e = byName(name) ?: return selected
        val out = selected.toMutableSet()
        if (name in out) {
            out -= name
            var changed = true
            while (changed) changed = out.removeAll(out.filter { n -> byName(n)?.requires?.let { it !in out } == true }.toSet())
        } else {
            if (e.requires != null && e.requires !in out) return selected
            if (e.category in EXCLUSIVE) out.removeAll(ENTRIES.filter { it.category == e.category }.map { it.name }.toSet())
            out += name
        }
        return out
    }

    fun raidLevel(selected: Set<String>): Int = selected.sumOf { byName(it)?.raidLevel ?: 0 }

    /** Entry 0-149, Normal 150-299, Expert 300+ (10-invocations.txt MODES; CS2 toa_partydetails_sliding_action strings). */
    fun mode(level: Int): String = when { level < 150 -> "Entry"; level < 300 -> "Normal"; else -> "Expert" }
}
