package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * OSRS elemental weakness (OSRS Wiki "Elemental weakness", read 2026-09-24):
 * - "For every percentage point of elemental weakness that an NPC has, the player will gain an extra 1% magic damage and 1% magic
 *   accuracy."
 * - Max hit: "⌊⌊Base Max×(1+Magic Damage%)⌋×(1+Slayer or Salve%)⌋+⌊Base Max×Elemental Weakness%⌋" - added after every other source.
 * - Only standard-spellbook strike, bolt, blast, wave and surge spells count (not god spells, not Ancient Magicks).
 * Data: data/cfg/elemental_weakness.tsv (wiki infobox_monster, by monster name).
 */
object ElementalWeakness {
    enum class Element { AIR, WATER, EARTH, FIRE }

    data class Weakness(val element: Element, val percent: Int)

    private var table: Map<String, Weakness> = emptyMap()

    fun load(path: Path = Paths.get("./data/cfg/elemental_weakness.tsv")) {
        if (!Files.exists(path)) return
        table =
            Files.readAllLines(path).asSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }
                .filter { it.size >= 3 }
                .mapNotNull { (name, element, percent) ->
                    val e = Element.values().firstOrNull { it.name == element.trim().uppercase() } ?: return@mapNotNull null
                    name.lowercase() to Weakness(e, percent.trim().toInt())
                }
                .toMap()
    }

    val size: Int get() = table.size

    fun of(name: String): Weakness? = table[name.lowercase().trim()]

    fun elementOf(spell: CombatSpell?): Element? {
        spell ?: return null
        if (CombatSpell.ELEMENTAL_TIERS.none { row -> row.any { it.first == spell } }) return null
        return Element.values().firstOrNull { spell.name.startsWith(it.name.replace("AIR", "WIND") + "_") }
    }

    /** The weakness percentage [spell] exploits on [target], 0 when none applies. */
    fun percent(target: Pawn, spell: CombatSpell?): Int {
        if (target !is Npc) return 0
        val element = elementOf(spell) ?: return 0
        val weakness = of(target.def.name) ?: return 0
        return if (weakness.element == element) weakness.percent else 0
    }
}
