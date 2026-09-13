package gg.rsmod.plugins.content.combat.attack

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.isProtectedFrom
import kotlin.math.abs
import kotlin.math.max

/**
 * Generic npc attack conditions (NpcAttacks.condition), ported from Void's `npcCondition` registrations.
 * Each condition decides whether a data-driven attack section may be chosen (Void `Attack.valid`).
 *
 * Boss-specific conditions live with their boss (God Wars, Chaos Elemental). Not ported yet: Wizards
 * `not_confused/not_weakened/not_cursed/not_vulnerable` (Spell.canDrain), Ghast pouch/food, TzHaar healers,
 * frost dragon and tormented demon style state; attacks using them are never selected until they are.
 */

fun Player.wornName(slot: EquipmentType): String = getEquipment(slot)?.getDef(world.definitions)?.name?.lowercase() ?: ""

/** Void `Equipment.isEarmuffs/isNosePeg/isFaceMask`: the item or any (full) slayer helmet. */
fun Player.wearsSlayerHat(item: String): Boolean {
    val hat = wornName(EquipmentType.HEAD)
    return hat == item || hat.startsWith("slayer helmet") || hat.startsWith("full slayer helmet")
}

// Void CockroachSoldier.kt
NpcAttacks.condition("ranged_only") { npc, target ->
    target is Player && max(abs(npc.tile.x - target.tile.x), abs(npc.tile.z - target.tile.z)) > 2
}

// Void Banshee.kt
NpcAttacks.condition("earmuffs") { _, target -> target is Player && (target.wearsSlayerHat("earmuffs") || target.wornName(EquipmentType.HEAD) == "masked earmuffs") }
NpcAttacks.condition("no_earmuffs") { _, target -> target is Player && !(target.wearsSlayerHat("earmuffs") || target.wornName(EquipmentType.HEAD) == "masked earmuffs") }

// Void AberrantSpectre.kt (no_nose_peg is also true for a non-player target)
NpcAttacks.condition("nose_peg") { _, target -> target is Player && target.wearsSlayerHat("nose peg") }
NpcAttacks.condition("no_nose_peg") { _, target -> !(target is Player && target.wearsSlayerHat("nose peg")) }

// Void DustDevil.kt
NpcAttacks.condition("face_mask") { _, target -> target is Player && target.wearsSlayerHat("face mask") }
NpcAttacks.condition("no_face_mask") { _, target -> target is Player && !target.wearsSlayerHat("face mask") }

// Void Cockatrice.kt
NpcAttacks.condition("mirror_shield") { _, target -> target is Player && target.wornName(EquipmentType.SHIELD) == "mirror shield" }
NpcAttacks.condition("no_mirror_shield") { _, target -> target is Player && target.wornName(EquipmentType.SHIELD) != "mirror shield" }

// Void FeverSpider.kt
NpcAttacks.condition("slayer_gloves") { _, target -> target is Player && target.wornName(EquipmentType.GLOVES) == "slayer gloves" }
NpcAttacks.condition("no_slayer_gloves") { _, target -> target is Player && target.wornName(EquipmentType.GLOVES) != "slayer gloves" }

// Void MosLeHarmlessCave.kt
NpcAttacks.condition("witchwood_icon") { _, target -> target is Player && target.wornName(EquipmentType.AMULET) == "witchwood icon" }
NpcAttacks.condition("no_witchwood_icon") { _, target -> target is Player && target.wornName(EquipmentType.AMULET) != "witchwood icon" }

// Void Suqah.kt / Aquanite.kt
NpcAttacks.condition("no_protect_melee") { _, target -> !target.isProtectedFrom(CombatClass.MELEE) }
NpcAttacks.condition("no_protect_magic") { _, target -> !target.isProtectedFrom(CombatClass.MAGIC) }
NpcAttacks.condition("no_protect_range") { _, target -> !target.isProtectedFrom(CombatClass.RANGED) }
NpcAttacks.condition("target_protect_magic") { _, target -> target is Player && target.isProtectedFrom(CombatClass.MAGIC) }

// Void Wizards.kt
NpcAttacks.condition("not_frozen") { _, target -> !target.timers.has(FROZEN_TIMER) && !target.timers.has(FREEZE_IMMUNITY_TIMER) }

// Void Catablepon.kt: target Strength above 3 + 92% of its level
NpcAttacks.condition("catablepon") { _, target ->
    target is Player && target.skills.getCurrentLevel(Skills.STRENGTH) > 3 + (target.skills.getMaxLevel(Skills.STRENGTH) * 0.92)
}
