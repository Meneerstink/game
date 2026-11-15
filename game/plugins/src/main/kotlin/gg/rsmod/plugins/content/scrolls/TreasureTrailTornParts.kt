package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.BonusSlot

/** Three source cryptics make ONE master step, never three free trail advances. */
class TreasureTrailThreePartStep(val components: List<TreasureTrailStepCatalogue.Step>) : ClueStep {
    init {
        require(components.size == 3 && components.all { it.tier == ClueScrollTier.MASTER && it.tornPart })
        require(components.map { it.id }.distinct().size == 3)
    }
    override val tier = ClueScrollTier.MASTER
    override val hint = components.joinToString("<br><br>") { it.hint }
    override val persistenceId = ("master:three-parts:" + components.joinToString("|") { it.key }).hashCode()
    override fun onAttempt(player: Player): Boolean = false // Only Combine can advance this trail step.

    fun digPart(player: Player): Int? = components.indexOfFirst {
        it.action == TreasureTrailStepCatalogue.Action.DIG && it.at(player.tile)
    }.takeIf { it >= 0 }

    fun talkPart(npc: Npc): Int? = components.indexOfFirst {
        it.action == TreasureTrailStepCatalogue.Action.TALK && npc.id in it.existingNpcs
    }.takeIf { it >= 0 }

    fun qualifies(player: Player, part: Int): Boolean {
        val requirement = components.getOrNull(part) ?: return false
        if (!requirement.acceptsEquipment(player.equipment.rawItems.map { it?.id })) return false
        fun bonus(slot: BonusSlot, excludeBlowpipe: Boolean = false): Int = player.equipment.filterNotNull().sumOf { item ->
            val def = item.getDef(player.world.definitions)
            if (excludeBlowpipe && def.name.startsWith("Toxic blowpipe", ignoreCase = true)) 0 else def.bonuses.getOrElse(slot.id) { 0 }
        }
        if (requirement.prayerBelow?.let { bonus(BonusSlot.PRAYER_BONUS) >= it } == true) return false
        if (requirement.rangedAtLeast?.let { bonus(BonusSlot.ATTACK_RANGED, requirement.excludeBlowpipe) < it } == true) return false
        return true
    }
}

/** Wiki Torn clue scroll: physical pieces are reusable between three-part clues while juggling. */
object TreasureTrailTornParts {
    val SOURCE_IDS = listOf(19837, 19838, 19839)
    enum class Obtain { GIVEN, ALREADY_HELD, NO_SPACE, MISSING_IMPORT, INVALID_STEP, REQUIREMENT }
    enum class Combine { COMBINED, MISSING_PARTS, NO_MASTER, DISCARDED_INCOMPATIBLE, STALE, MISSING_IMPORT }

    fun obtain(player: Player, step: TreasureTrailThreePartStep, part: Int): Obtain {
        if (part !in SOURCE_IDS.indices || ClueScrollManager.activeStep(player, ClueScrollTier.MASTER) !== step) return Obtain.INVALID_STEP
        if (!step.qualifies(player, part)) return Obtain.REQUIREMENT
        val id = ClueItems.local(SOURCE_IDS[part]) ?: return Obtain.MISSING_IMPORT
        if (ClueOwnership.stored(player).any { it.id == id }) return Obtain.ALREADY_HELD
        // No progress or hidden completion flag: the physical source piece is the result.
        if (!player.inventory.add(id, 1, assureFullInsertion = true).hasSucceeded()) return Obtain.NO_SPACE
        return Obtain.GIVEN
    }

    fun combine(player: Player, clicked: Item): Combine {
        if (!player.inventory.any { it === clicked }) return Combine.STALE
        val parts = SOURCE_IDS.map { ClueItems.local(it) ?: return Combine.MISSING_IMPORT }
        check(parts.distinct().size == 3) { "CLUE torn-part mappings collide" }
        if (clicked.id !in parts) return Combine.STALE
        if (parts.any { !player.inventory.contains(it) }) return Combine.MISSING_PARTS
        val tier = ClueScrollTier.MASTER
        val scroll = player.inventory.firstOrNull { it?.id == tier.scrollId } ?: return Combine.NO_MASTER
        val step = ClueScrollManager.activeStep(player, tier) ?: return Combine.NO_MASTER
        if (step !is TreasureTrailThreePartStep) {
            // Source explicitly discards parts when combining with a different,
            // single-action master clue. Never advance that unrelated clue.
            val staged = ItemContainer(player.inventory)
            if (parts.any { !staged.remove(it, 1, assureFullRemoval = true).hasSucceeded() }) return Combine.STALE
            commit(player, staged)
            return Combine.DISCARDED_INCOMPATIBLE
        }
        val progress = ClueScrollManager.progress(player, tier)
        return if (ClueScrollManager.advance(player, tier, scroll, progress, parts)) Combine.COMBINED else Combine.STALE
    }

    private fun commit(player: Player, staged: ItemContainer) {
        for (slot in 0 until staged.capacity) {
            val before = player.inventory[slot]; val after = staged[slot]
            if (before?.id != after?.id || before?.amount != after?.amount || before?.attr != after?.attr) player.inventory[slot] = after
        }
    }
}
