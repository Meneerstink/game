package gg.rsmod.plugins.content.mechanics.pvp.breach

import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Breach conformance 2026-09-27, layer E1 (data diff): every breach monster against the OSRS Wiki infobox of its "Deadman:
 * Annihilation" version (owner 2026-09-23: breach monsters use the Annihilation stats). The raw wikitext of each page is kept in
 * `data/cfg/deadman/wiki-infobox/<page>.txt` (fetched with `?action=raw`, 2026-09-27), so the test runs offline and a later wiki
 * change shows up as a diff when the files are refreshed.
 *
 * Compared per monster: hitpoints, attack speed, the combat levels (att/str/def/mage/range), attack and defence bonuses (OSRS
 * "dstandard" is our single ranged defence), and the 100 % poison / venom resistances.
 */
class BreachWikiInfoboxTests {
    private val dir = Paths.get("..", "..", "data", "cfg", "deadman", "wiki-infobox").toFile()

    /** Infobox fields of one version: `key` + version number first, then the shared `key`. */
    private class Infobox(
        text: String,
    ) {
        private val fields: Map<String, String> =
            Regex("^\\|([^=]+?)\\s*=\\s*(.*)$", RegexOption.MULTILINE).findAll(text.substringAfter("{{Infobox Monster").substringBefore("\n}}"))
                .associate { it.groupValues[1].trim() to it.groupValues[2].trim() }

        val version: Int =
            (1..9).firstOrNull { fields["version$it"]?.contains("Annihilation") == true } ?: 0

        operator fun get(key: String): String? = (if (version > 0) fields["$key$version"] else null) ?: fields[key]

        fun int(key: String): Int? = get(key)?.replace(",", "")?.let { Regex("-?\\d+").find(it)?.value?.toInt() }
    }

    private fun infobox(osrsId: Int): Infobox? =
        dir.listFiles { f -> f.name.endsWith(".txt") }?.map { Infobox(it.readText()) }?.firstOrNull { box ->
            box["id"]?.split(',')?.map { it.trim() }?.contains(osrsId.toString()) == true
        }

    @Test
    fun `every breach monster matches its Deadman Annihilation infobox`() {
        gg.rsmod.plugins.content.combat.formula.ElementalWeakness.load(Paths.get("..", "..", "data", "cfg", "elemental_weakness.tsv"))
        gg.rsmod.plugins.content.combat.formula.ElementalWeakness.idOverrides += BreachMonsters.WEAKNESS_OVERRIDES
        val failures = mutableListOf<String>()
        val missing = mutableListOf<String>()
        BreachMonsters.ROSTER.filter { it.name != "Zemouregal Summon" }.forEach { m ->
            val box = infobox(m.osrsId)
            if (box == null) {
                missing += "${m.id} ${m.name} (osrs ${m.osrsId})"
                return@forEach
            }
            fun check(
                field: String,
                ours: Int,
                wiki: Int?,
            ) {
                if (wiki != null && wiki != ours) failures += "${m.name}: $field ours=$ours wiki=$wiki"
            }
            val s = m.stats
            check("hitpoints", s.hp, box.int("hitpoints"))
            check("attack speed", m.speed, box.int("attack speed"))
            check("att", s.att, box.int("att"))
            check("str", s.str, box.int("str"))
            check("def", s.def, box.int("def"))
            check("mage", s.mage, box.int("mage"))
            check("range", s.range, box.int("range"))
            check("attbns", s.attbns, box.int("attbns"))
            check("strbns", s.strbns, box.int("strbns"))
            check("amagic", s.amagic, box.int("amagic"))
            check("mbns", s.mbns, box.int("mbns"))
            check("arange", s.arange, box.int("arange"))
            check("rngbns", s.rngbns, box.int("rngbns"))
            check("dstab", s.dstab, box.int("dstab"))
            check("dslash", s.dslash, box.int("dslash"))
            check("dcrush", s.dcrush, box.int("dcrush"))
            check("dmagic", s.dmagic, box.int("dmagic"))
            check("dranged(dstandard)", s.dranged, box.int("dstandard"))
            val poison = box.int("poisonresistance") == 100
            val venom = box.int("venomresistance") == 100
            if (box["poisonresistance"] != null && poison != (m.id in BreachMonsters.POISON_IMMUNE)) failures += "${m.name}: poison immune ours=${m.id in BreachMonsters.POISON_IMMUNE} wiki=$poison"
            if (box["venomresistance"] != null && venom != (m.id in BreachMonsters.VENOM_IMMUNE)) failures += "${m.name}: venom immune ours=${m.id in BreachMonsters.VENOM_IMMUNE} wiki=$venom"
            val freeze = box.int("freezeresistance") == 100
            if (freeze != (m.id in BreachMonsters.FREEZE_IMMUNE)) failures += "${m.name}: freeze immune ours=${m.id in BreachMonsters.FREEZE_IMMUNE} wiki=$freeze"
            // elemental weakness (the shared ElementalWeakness table, looked up by name)
            val type = box["elementalweaknesstype"]?.takeIf { it.isNotBlank() }?.lowercase()
            val percent = box.int("elementalweaknesspercent")
            val ours =
                gg.rsmod.plugins.content.combat.formula.ElementalWeakness.of(m.id, m.name)
            val oursText = ours?.let { "${it.element.name.lowercase()} ${it.percent}" }
            val wikiText = if (type != null && percent != null) "$type $percent" else null
            if (oursText != wikiText) failures += "${m.name}: elemental weakness ours=$oursText wiki=$wikiText"
        }
        assertTrue(missing.isEmpty(), "no wiki infobox for: ${missing.joinToString()}")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Combat level and size of every breach npc in the game cache against its Annihilation infobox ("combat", "size"). */
    @Test
    fun `every breach monster has the Annihilation combat level and size in the cache`() {
        val store = com.displee.cache.CacheLibrary(Paths.get("..", "..", "data", "cache").toString())
        val failures = mutableListOf<String>()
        try {
            val defs = gg.rsmod.game.fs.DefinitionSet()
            defs.load(store, gg.rsmod.game.fs.def.NpcDef::class.java)
            BreachMonsters.ROSTER.filter { it.name != "Zemouregal Summon" }.forEach { m ->
                val box = infobox(m.osrsId) ?: return@forEach
                val def = defs.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, m.id) ?: return@forEach failures.add("${m.name}: no npc ${m.id}").let { }
                // shown level = the spawn override (BreachMonsters.COMBAT_LEVEL_BY_OSRS) or the cache level
                val shown = BreachMonsters.COMBAT_LEVEL_BY_OSRS[m.osrsId] ?: def.combatLevel
                box.int("combat")?.let { if (it != shown) failures += "${m.name}: combat level shown=$shown (cache ${def.combatLevel}) wiki=$it" }
                box.int("size")?.let { if (it != def.size) failures += "${m.name}: size cache=${def.size} wiki=$it" }
            }
        } finally {
            store.close()
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * The infobox "max hit" of the Annihilation version ("60 (Melee)", "43 (Magic), 16 (Melee)", "21 (x4)", "78 (Melee), 108") must
     * be a max hit of one of the monster's attack sections of that style. "?" and extras the sections do not model ("(Explosion)",
     * "(11 with burn)", the Bloat's flies issued by its own loop) are skipped.
     */
    @Test
    fun `every breach monster's attack sections carry the Annihilation max hits`() {
        val rows = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).associateBy { it.id }
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.filter { it.name != "Zemouregal Summon" && it.id != 14470 }.forEach { m ->
            val box = infobox(m.osrsId) ?: return@forEach
            val text = box["max hit"] ?: return@forEach
            val hits = rows.getValue(m.id).attacks.flatMap { a -> a.hits.map { h -> h.offense to h.max / 10 } }
            fun category(offense: String): String =
                when {
                    gg.rsmod.plugins.content.combat.attack.NpcAttacks.isMelee(offense) -> "melee"
                    offense == "range" -> "ranged"
                    else -> offense
                }
            Regex("(\\d+)\\+?\\s*(?:\\(([^)]*)\\))?").findAll(text).forEach { match ->
                val value = match.groupValues[1].toInt()
                val label = match.groupValues[2].lowercase()
                if ("explosion" in label || "burn" in label) return@forEach
                val wanted =
                    when {
                        label.startsWith("melee") -> "melee"
                        label.startsWith("magic") -> "magic"
                        label.startsWith("ranged") || label.startsWith("range") -> "ranged"
                        label.startsWith("dragonfire") -> "dragonfire"
                        else -> null // plain or "(x4)": any style
                    }
                val ok = hits.any { (offense, max) -> max == value && (wanted == null || category(offense) == wanted) }
                if (!ok) failures += "${m.name}: wiki max hit $value${if (wanted != null) " ($wanted)" else ""}, sections have $hits"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
