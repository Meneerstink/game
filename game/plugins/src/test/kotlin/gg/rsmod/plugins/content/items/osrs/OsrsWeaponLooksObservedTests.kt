package gg.rsmod.plugins.content.items.osrs

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N34 (Onderzoeker C2 10-16): [OsrsWeaponLooks] was only checked for HAVING a row, never against what OSRS really plays. Source of the
 * observed sequences: RuneLite plugin weapon-animation-replacer `data.json` (geheur, commit 504f6bd6, copy in test resources) -
 * `poseanims` maps a set to its OSRS item ids, `animationSets` gives per set (Swap.java AnimationType order) STAND, WALK, RUN,
 * WALK_BACKWARD, SHUFFLE_LEFT, SHUFFLE_RIGHT, ROTATE, ATTACK_STAB, ATTACK_SLASH, ATTACK_CRUSH, ATTACK_SPEC, DEFEND, ...
 *
 * Class rule over every imported weapon with a look that the file covers: each attack sequence we play is one of the set's observed
 * attacks (stab / slash / crush; the special is not a normal attack), and our block is the set's DEFEND. Local sequence ids go back
 * to OSRS ids through OSRS_IMPORT_MASTER.yml (`seq` / `playerseq` records); a sequence not imported keeps its id in both caches.
 */
class OsrsWeaponLooksObservedTests {
    private val master = File("../../../../OSRS_IMPORT_MASTER.yml").readText().replace("\r\n", "\n")

    /** Local item -> OSRS item of every imported item. */
    private val osrsItem: Map<Int, Int> =
        Regex("""local_item_id: (\d+)\s*\n\s*name: .*\n\s*upstream_item_id: (\d+)""")
            .findAll(master).associate { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    /** Local sequence -> OSRS sequence. */
    private val osrsSeq: Map<Int, Int> =
        Regex("""fx_kind: (?:player)?seq\s*\n\s*upstream_fx_id: (\d+)\s*\n\s*local_fx_id: (\d+)""")
            .findAll(master).associate { it.groupValues[2].toInt() to it.groupValues[1].toInt() }

    private fun upstream(local: Int) = osrsSeq[local] ?: local

    /** OSRS item -> (set name, observed sequences). */
    private val observed: Map<Int, Pair<String, IntArray>> by lazy {
        val root = JsonParser().parse(File("src/test/resources/osrs/weapon-animation-replacer-data.json").readText()).asJsonObject
        val sets = root["animationSets"].asJsonArray.associate { s -> s.asJsonObject["name"].asString to s.asJsonObject["animations"].asJsonArray.map { it.asInt }.toIntArray() }
        val out = mutableMapOf<Int, Pair<String, IntArray>>()
        root["poseanims"].asJsonObject.entrySet().forEach { (name, items) ->
            val anims = sets[name] ?: return@forEach
            items.asJsonArray.forEach { out.putIfAbsent(it.asInt, name to anims) }
        }
        out
    }

    /**
     * Where a second in-game observation disagrees with the replacer set, the row keeps its sourced choice ("<local item>:attack" /
     * ":block" -> why). This list may only shrink.
     */
    private val disputed =
        mapOf(
            "22685:attack" to "Ancient godsword: the Block style swings DH_SWORD_UPDATE_BLOCK; the replacer has no slot for that style",
            "22731:attack" to "Mystic smoke staff: combat-logger observed HUMAN_AXE_CHOP 393 for a staff bash, the replacer 419",
            "23749:attack" to "Smoke battlestaff: combat-logger 393 vs replacer 419",
            "23751:attack" to "Mist battlestaff: combat-logger 393 vs replacer 419",
            "23753:attack" to "Mystic mist staff: combat-logger 393 vs replacer 419",
            "23755:attack" to "Dust battlestaff: combat-logger 393 vs replacer 419",
            "23757:attack" to "Mystic dust staff: combat-logger 393 vs replacer 419",
            "22759:attack" to "Staff of the dead: bladed-staff swipe 440 (Zenyte table); the replacer only fills crush",
            "22761:attack" to "Toxic staff of the dead: as the Staff of the dead",
            "22950:attack" to "Staff of balance: as the Staff of the dead",
            "23626:attack" to "Gilded spear: combat-logger spear 428 / 440 vs replacer 381 / 380 / 382",
            "23628:attack" to "Gilded hasta: as the Gilded spear",
            "23634:attack" to "Gilded spade: combat-logger / Glabay blunt 401 vs replacer 2h set",
            "23634:block" to "Gilded spade: kept with its blunt attacks",
            "23916:attack" to "Viggora's chainmace (u): stab WILD_CAVE_CHAINMACE_STAB 246 (Jagex name); the replacer fills crush only",
            "23916:block" to "Viggora's chainmace (u): Jagex WILD_CAVE_CHAINMACE_DEFEND 7200 vs replacer 4177",
            "23919:attack" to "Viggora's chainmace: as the uncharged one",
            "23919:block" to "Viggora's chainmace: as the uncharged one",
        )

    @Test
    fun `every imported OSRS weapon attacks and blocks with the sequences OSRS players observe`() {
        val differs = mutableMapOf<String, String>()
        var compared = 0
        for (local in OsrsWeaponLooks.COVERED.sorted()) {
            val osrs = osrsItem[local] ?: continue
            val (set, anims) = observed[osrs] ?: continue
            val attacks = listOf(anims[7], anims[8], anims[9]).filter { it >= 0 }.toSet()
            val defend = anims[11]
            if (attacks.isEmpty() && defend < 0) continue
            compared++
            val ours =
                (0..3).flatMap { style ->
                    listOf(false, true).flatMap { npc -> listOf(false, true).mapNotNull { stab -> OsrsWeaponLooks.attackAnimation(local, style, npc, stab) } }
                }.map { upstream(it) }.toSet()
            val foreign = ours - attacks
            if (attacks.isNotEmpty() && foreign.isNotEmpty()) differs["$local:attack"] = "$local (osrs $osrs, '$set'): attacks $foreign not in observed $attacks"
            val block = OsrsWeaponLooks.blockAnimation(local)?.let { upstream(it) }
            if (defend >= 0 && block != defend) differs["$local:block"] = "$local (osrs $osrs, '$set'): block $block, observed $defend"
        }
        assertTrue(compared >= 40, "compared $compared weapons")
        val wrong = differs.filterKeys { it !in disputed }.values
        assertTrue(wrong.isEmpty(), "${wrong.size} weapons differ from OSRS:\n" + wrong.joinToString("\n"))
        val stale = disputed.keys - differs.keys
        assertTrue(stale.isEmpty(), "disputed entries that now match the observation (remove them): $stale")
    }
}
