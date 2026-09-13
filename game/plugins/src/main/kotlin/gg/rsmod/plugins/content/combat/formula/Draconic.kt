package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.ext.isSpecies

/**
 * Targets affected by dragonbane effects (Dragon hunter crossbow, Dragon hunter lance).
 *
 * OSRS Wiki "Draconic (attribute)" (fetched 2026-09-14) lists the draconic monsters by name, "all levels/forms/variants"
 * included, and names three that are "unaffected by the above effects": Elvarg, Revenant dragon and Zulrah. Most dragon
 * NPCs in this server carry no species tag (combat-defs.json `species: []`), so the check matches the definition name
 * against that list and also accepts the existing [NpcSpecies.DRAGON] tag. Players are never draconic.
 */
object Draconic {
    val NAMES: Set<String> =
        setOf(
            "adamant dragon", "alchemical hydra", "ancient wyvern", "baby black dragon", "baby blue dragon", "baby green dragon",
            "baby red dragon", "black dragon", "blue dragon", "bronze dragon", "brutal black dragon", "brutal blue dragon",
            "brutal green dragon", "brutal red dragon", "colossal hydra", "drake", "frost dragon", "galvek", "great olm",
            "green dragon", "guardian drake", "hydra", "iron dragon", "king black dragon", "lava dragon", "lava strykewyrm",
            "long-tailed wyvern", "magma strykewyrm", "mithril dragon", "reanimated dragon", "red dragon", "rune dragon",
            "shadow wyrm", "skeletal wyvern", "spitting wyvern", "steel dragon", "taloned wyvern", "the hueycoatl", "vorkath",
            "wyrm", "wyrmling",
        )

    val EXCLUDED: Set<String> = setOf("elvarg", "revenant dragon", "zulrah")

    fun isDraconic(target: Pawn): Boolean {
        if (target !is Npc) return false
        val name = target.name.lowercase()
        if (name in EXCLUDED) return false
        return name in NAMES || target.isSpecies(NpcSpecies.DRAGON)
    }
}
