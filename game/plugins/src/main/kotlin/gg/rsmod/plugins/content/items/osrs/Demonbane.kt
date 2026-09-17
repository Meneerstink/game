package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.isSpecies
import gg.rsmod.plugins.api.ext.refreshBonuses
import kotlin.random.Random

/**
 * OSRS import batch "demonbane" (tx-20260917-051747). Sources: OSRS Wiki raw wikitext "Arclight", "Emberlight", "Darklight", "Burning
 * claws", "Burning claw", "Scorching bow", "Purging staff", "Transcript:Emberlight", "Transcript:Scorching bow" and the wiki DPS
 * calculator (weirdgloop/osrs-dps-calc `PlayerVsNPCCalc.ts`, `dists/claws.ts`), all read 2026-09-17.
 *
 * Arclight: "Beginning with 1,000 charges after creation, it gains an additional 333 charges for every ancient shard added, or 1,000
 * if three are added simultaneously"; "a maximum capacity of 10,000 charges"; "One charge is consumed for each successful hit,
 * excluding those from its special attack. If all charges are consumed, it will transform into an inactive variant which functions
 * identically to Darklight"; infusion: "each charge consumed increases the infusion in return, with a fully charged and spent Arclight
 * corresponding to 100% infusion" and "Infusion progress will not be lost if Arclight runs out of charges". Emberlight needs an
 * Arclight that is "fully charged, infused, or a combination of the two" ("each percent of infusion being worth 100 charges"; the
 * excess "will be wasted").
 *
 * Demonbane (calculator `trackAddFactor(x, demonbaneFactor(n))` on the attack roll and max hit): Arclight/Emberlight 70, Silverlight/
 * Darklight (and the inactive Arclight, "identically to Darklight") 60, Bone/Burning claws 5.
 *
 * Stored per sword: [ItemAttribute.CHARGES] = charges, [ItemAttribute.DEGRADE] = consumed charges (0..10,000 = 0..100.00 % infusion).
 */
object Demonbane {
    const val ARCLIGHT_PERCENT = 70
    const val DARKLIGHT_PERCENT = 60
    const val BURNING_CLAWS_PERCENT = 5

    const val START_CHARGES = 1_000
    const val MAX_CHARGES = 10_000
    const val CHARGES_PER_SHARD = 333
    const val CHARGES_PER_THREE_SHARDS = 1_000
    const val SHARDS_TO_CREATE = 3
    const val FULL_INFUSION = 10_000

    const val EMBERLIGHT_SMITHING_LEVEL = 74
    const val SYNAPSE_FIRST_EXPERIENCE = 730.0
    const val SYNAPSE_EXPERIENCE = 73.0

    const val ALTAR_MESSAGE = "You require additional power from the catacombs altar to create Arclight."
    const val SYNAPSE_ON_SWORD_MESSAGE = "You try to attach the synapse to the sword. The blade sizzles for a moment, but the synapse just slides off it again."

    /** Weapons whose normal attacks carry the 70 % demonbane passive. */
    val ARCLIGHT_CLASS = intArrayOf(Items.ARCLIGHT, Items.EMBERLIGHT)

    /** Weapons that "function identically to Darklight" (60 %). */
    val DARKLIGHT_CLASS = intArrayOf(Items.SILVERLIGHT, Items.DARKLIGHT, Items.ARCLIGHT_INACTIVE)

    fun isDemon(target: Pawn): Boolean = target is Npc && target.isSpecies(NpcSpecies.DEMON)

    /** Melee demonbane percentage of [player]'s weapon against [target], 0 when none applies. */
    fun meleePercent(
        player: Player,
        target: Pawn,
    ): Int {
        if (!isDemon(target)) return 0
        return when {
            player.hasEquipped(EquipmentType.WEAPON, *ARCLIGHT_CLASS) -> ARCLIGHT_PERCENT
            player.hasEquipped(EquipmentType.WEAPON, *DARKLIGHT_CLASS) -> DARKLIGHT_PERCENT
            player.hasEquipped(EquipmentType.WEAPON, Items.BURNING_CLAWS) -> BURNING_CLAWS_PERCENT
            else -> 0
        }
    }

    fun charges(item: Item): Int = if (item.id == Items.ARCLIGHT) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun infusion(item: Item): Int = item.attr[ItemAttribute.DEGRADE] ?: 0

    /** Infusion shown by Check, in whole percent (each percent = 100 consumed charges). */
    fun infusionPercent(item: Item): Int = infusion(item) / 100

    /** The sword for [charges] and [infusion]: charged Arclight above 0 charges, the inactive variant (infusion kept) otherwise. */
    fun arclight(
        charges: Int,
        infusion: Int,
    ): Item {
        val clamped = charges.coerceIn(0, MAX_CHARGES)
        val item = Item(if (clamped > 0) Items.ARCLIGHT else Items.ARCLIGHT_INACTIVE)
        if (clamped > 0) item.attr[ItemAttribute.CHARGES] = clamped
        if (infusion > 0) item.attr[ItemAttribute.DEGRADE] = infusion.coerceAtMost(FULL_INFUSION)
        return item
    }

    /** A new Arclight made from Darklight and three shards. */
    fun created(): Item = arclight(START_CHARGES, 0)

    /** Charges [shards] add: 1,000 per three used together, 333 per remaining single shard. */
    fun chargesForShards(shards: Int): Int = (shards / 3) * CHARGES_PER_THREE_SHARDS + (shards % 3) * CHARGES_PER_SHARD

    /**
     * Shards (at most [available]) actually used on [sword]: the largest count whose charges still fit under the 10,000 cap. Using
     * shards on a sword that is already full uses none.
     */
    fun shardsToUse(
        sword: Item,
        available: Int,
    ): Int {
        val room = MAX_CHARGES - charges(sword)
        if (room <= 0) return 0
        var shards = available.coerceAtLeast(0)
        while (shards > 0 && chargesForShards(shards) > room + CHARGES_PER_SHARD - 1) shards--
        return shards
    }

    fun addShards(
        sword: Item,
        shards: Int,
    ): Item = arclight(charges(sword) + chargesForShards(shards), infusion(sword))

    /** One charge for a successful normal hit; the sword turns inactive at 0 with its infusion kept. */
    fun afterSuccessfulHit(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        if (weapon.id != Items.ARCLIGHT) return
        val next = arclight(charges(weapon) - 1, infusion(weapon) + 1)
        player.equipment[EquipmentType.WEAPON.id] = next
        if (next.id != Items.ARCLIGHT) player.refreshBonuses()
    }

    /** Charges plus infusion (each consumed charge counts once), as used by the Emberlight upgrade. */
    fun upgradeProgress(sword: Item): Int = if (sword.id == Items.ARCLIGHT || sword.id == Items.ARCLIGHT_INACTIVE) charges(sword) + infusion(sword) else 0

    fun canUpgrade(sword: Item): Boolean = upgradeProgress(sword) >= FULL_INFUSION

    fun checkMessage(sword: Item): String =
        if (sword.id == Items.ARCLIGHT) {
            "Your Arclight has ${charges(sword)} charges and is ${infusionPercent(sword)}% infused."
        } else {
            "Your Arclight has no charges left and is ${infusionPercent(sword)}% infused."
        }

    /** Tormented synapse products: the Revert option returns only the synapse; a PvP killer receives the synapse. */
    val SYNAPSE_PRODUCTS = mapOf(Items.EMBERLIGHT to "Emberlight", Items.SCORCHING_BOW to "Scorching bow", Items.PURGING_STAFF to "Purging staff")

    fun revertWarning(name: String) = "If you revert your $name, you'll get your Tormented synapse back, but you'll lose any other materials used to make the weapon."

    fun revertTitle(name: String) = "Revert your $name and get your Tormented synapse back?"

    fun revertDone(name: String) = "You strip the Tormented synapse from your $name. The rest of the weapon is beyond salvaging."
}

/**
 * Weaken (Darklight, the inactive Arclight, Arclight, Emberlight): 50 % energy, accuracy rolled "against an opponent's stab defence";
 * lowers Strength, Attack and Defence "by 5% (10% for demons) of their base level + 1" (Darklight / Arclight), Emberlight "5% (15% for
 * Demons)".
 */
object Weaken {
    const val ENERGY = 50

    fun demonPercent(weaponId: Int): Int = if (weaponId == Items.EMBERLIGHT) 15 else 10

    fun drainAmount(
        baseLevel: Int,
        weaponId: Int,
        demon: Boolean,
    ): Int = baseLevel * (if (demon) demonPercent(weaponId) else 5) / 100 + 1
}

/**
 * Burning claws special "Burning barrage" (35 % energy since 22 July 2026): three accuracy rolls against slash defence; the calculator's
 * `burningClawSpec` gives the exact hitsplats (total damage `low..max + low` with `low = trunc(max × (3 - roll) / 4)`):
 * roll 1 `[t/2, t/4, t/4]`, roll 2 `[t/2 - 1, t/2 - 1, 2]`, roll 3 `[t - 2, 1, 1]`; all three fail: 1/5 `[0, 0, 0]`, 2/5 `[1, 0, 0]`,
 * 2/5 `[1, 1, 0]`. "Each of the three hits also has a chance to inflict a burn" - 15 % / 30 % / 45 % for the roll that succeeded; no burn
 * when every roll fails.
 */
object BurningClaws {
    const val ENERGY = 35

    data class Barrage(
        val hitsplats: List<Int>,
        /** 0-based roll that succeeded, -1 when all three failed. */
        val successfulRoll: Int,
    ) {
        val burnChance: Double get() = if (successfulRoll < 0) 0.0 else 0.15 * (successfulRoll + 1)
    }

    fun barrage(
        maxHit: Int,
        rollLands: () -> Boolean,
        random: Random,
    ): Barrage {
        for (roll in 0 until 3) {
            if (!rollLands()) continue
            val low = maxHit * (3 - roll) / 4
            val total = random.nextInt(low, maxHit + low + 1)
            return Barrage(split(roll, total), roll)
        }
        val fail = random.nextInt(5)
        return Barrage(if (fail == 0) listOf(0, 0, 0) else if (fail <= 2) listOf(1, 0, 0) else listOf(1, 1, 0), -1)
    }

    fun split(
        roll: Int,
        total: Int,
    ): List<Int> =
        when (roll) {
            0 -> listOf(total / 2, total / 4, total / 4)
            1 -> listOf(total / 2 - 1, total / 2 - 1, 2)
            else -> listOf(total - 2, 1, 1)
        }.map { it.coerceAtLeast(0) }
}

/**
 * Dragon claws "Slice and Dice" (OSRS; calculator `dClawDist`): four accuracy rolls against slash defence, total damage `low..max + low - 1`
 * with `low = trunc(max × (4 - roll) / 4)`: roll 1 `[t/2, t/4, t/8, t/8 + 1]`, roll 2 `[0, t/2, t/4, t/4 + 1]`, roll 3 `[0, 0, t/2, t/2 + 1]`,
 * roll 4 `[0, 0, 0, t + 1]`; all four fail: 2/3 `[1, 1, 0, 0]`, 1/3 `[0, 0, 0, 0]`. (The calculator lists the landed splats first; the
 * wiki patterns "0-4-2-2", "0-0-3-3" and "0-0-0-5" place the misses first, used here.)
 */
object DragonClaws {
    fun sliceAndDice(
        maxHit: Int,
        rollLands: () -> Boolean,
        random: Random,
    ): List<Int> {
        for (roll in 0 until 4) {
            if (!rollLands()) continue
            val low = maxHit * (4 - roll) / 4
            val t = random.nextInt(low, maxHit + low)
            return when (roll) {
                0 -> listOf(t / 2, t / 4, t / 8, t / 8 + 1)
                1 -> listOf(0, t / 2, t / 4, t / 4 + 1)
                2 -> listOf(0, 0, t / 2, t / 2 + 1)
                else -> listOf(0, 0, 0, t + 1)
            }
        }
        return if (random.nextInt(3) < 2) listOf(1, 1, 0, 0) else listOf(0, 0, 0, 0)
    }
}
