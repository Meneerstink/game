package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped

/**
 * Ahrim the Blighted's "Blighted Aura" set effect (RSPS_DEFINITIEF_MASTERPLAN.md's "Stat drain"
 * further-foundations item: "DWH, BGS, Barrows, specs/effects" - this is the "Barrows" entry;
 * BGS's own drain is already implemented directly in
 * `content/combat/specialattack/weapons/bandos_godsword.plugin.kts`, and DWH (dragon warhammer)
 * has zero item ids anywhere in this cache - a real 2016 OSRS release, genuinely absent from
 * this ~2011/rev-667 cache, so blocked rather than built).
 *
 * Sourced from the OSRS Wiki's "Ahrim the Blighted's equipment" article: while wearing the full
 * set (hood, staff, robe top, robe skirt) any successful (non-splash) *magic* hit has a 25%
 * (1/4) chance to lower the target's Strength level by a flat 5, stacking repeatedly across
 * separate procs rather than being a one-time effect.
 *
 * Wired into [gg.rsmod.plugins.content.combat.PawnExt.dealHit]'s "BATCH 2" block, the same
 * once-per-landed-hit choke point [gg.rsmod.plugins.content.mechanics.lifesteal.GuthanLifesteal]
 * and Ancient Curses' Sap/Leech already use. That block is only entered `if (landHit)`, which
 * already excludes magic splashes, so no extra "did this land" check is needed here beyond the
 * [CombatClass.MAGIC] gate.
 */
object AhrimBlightedAura {
    private const val PROC_CHANCE_NUMERATOR = 1
    private const val PROC_CHANCE_DENOMINATOR = 4
    private const val STRENGTH_DRAIN = 5

    private fun isWearingAhrim(player: Player): Boolean =
        player.hasEquipped(
            EquipmentType.HEAD,
            Items.AHRIMS_HOOD,
            Items.AHRIMS_HOOD_25,
            Items.AHRIMS_HOOD_50,
            Items.AHRIMS_HOOD_75,
            Items.AHRIMS_HOOD_100,
        ) &&
            player.hasEquipped(
                EquipmentType.WEAPON,
                Items.AHRIMS_STAFF,
                Items.AHRIMS_STAFF_25,
                Items.AHRIMS_STAFF_50,
                Items.AHRIMS_STAFF_75,
                Items.AHRIMS_STAFF_100,
            ) &&
            player.hasEquipped(
                EquipmentType.CHEST,
                Items.AHRIMS_ROBE_TOP,
                Items.AHRIMS_ROBE_TOP_25,
                Items.AHRIMS_ROBE_TOP_50,
                Items.AHRIMS_ROBE_TOP_75,
                Items.AHRIMS_ROBE_TOP_100,
            ) &&
            player.hasEquipped(
                EquipmentType.LEGS,
                Items.AHRIMS_ROBE_SKIRT,
                Items.AHRIMS_ROBE_SKIRT_25,
                Items.AHRIMS_ROBE_SKIRT_50,
                Items.AHRIMS_ROBE_SKIRT_75,
                Items.AHRIMS_ROBE_SKIRT_100,
            )

    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        combatClass: CombatClass,
    ) {
        if (attacker !is Player || combatClass != CombatClass.MAGIC) return
        if (!isWearingAhrim(attacker)) return
        if (!attacker.world.chance(PROC_CHANCE_NUMERATOR, PROC_CHANCE_DENOMINATOR)) return
        when (target) {
            is Player -> target.skills.alterCurrentLevel(Skills.STRENGTH, -STRENGTH_DRAIN)
            is Npc -> target.stats.alterCurrentLevel(NpcSkills.STRENGTH, -STRENGTH_DRAIN)
        }
    }
}
