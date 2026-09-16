package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows
import kotlin.math.floor

/**
 * OSRS-IMPORT Bow of Faerdhinen and crystal armour (OSRS Wiki "Bow of Faerdhinen" and "Crystal equipment#Crystal armour" raw
 * wikitext, fetched 2026-09-14):
 * - Bow: "Requiring level 80 Ranged and 70 Agility to wield"; "In order to add charges to the bow, crystal shards must be used on
 *   it (this can be done before it is inactive as well), with each shard granting 100 charges, up to a maximum of 20,000";
 *   "one charge being depleted for each hit, whether it is a successful one or not; once the bow's charges have been fully
 *   depleted, it will become inactive and lose its stat bonuses"; "does not require any arrows to use, as it generates its own
 *   when fired"; the corrupted bow "stays charged permanently"; "On unprotected death in PvP, the inactive bow is dropped, and all
 *   shards used to charge it will be lost. In a PvM or protected on PvP death, all current charges are kept."
 * - Armour: helm "5% accuracy bonus" / "2.5% damage bonus", body "15%" / "7.5%", legs "10%" / "5%" ("30% accuracy" and "15%
 *   damage" for the full set); "Each shard granting 100 charges", maximum "20,000"; "One charge is depleted for each successful
 *   hit that is received from combat" (not for damage negated by protection prayers or from non-monster sources).
 * - Stacking (wiki DPS calculator `isWearingCrystalBow`: "Crystal bow" or any "Bow of Faerdhinen" name, inactive included):
 *   accuracy trunc(roll × (20 + n) / 20) and max hit trunc(max × (40 + n) / 40) with helm 1, legs 2, body 3, applied before the
 *   Salve / Slayer factors ("would be 37 if placed after slayer helm", tested in-game).
 * Uncharge/Revert (OSRS Wiki "Bow of faerdhinen#Reverting", "Crystal equipment#Reverting", "Crystal bow#Reverting", fetched
 * 2026-09-16): the Bow of Faerdhinen's "Uncharge" only discharges it to the inactive item, shards lost, no seed (a direct
 * seed reversion needs a Prifddinas singing bowl, not implemented); crystal armour's "Revert" returns exactly one Crystal
 * armour seed per piece, charges lost; the classic Crystal bow's "Revert" returns exactly one Crystal seed.
 *
 * SEED SINGING (owner document `cRYSTAL.rtf`, 2026-09-16, quoting OSRS Wiki "Crystal equipment#Creation" and "Bow of
 * Faerdhinen#Creation" verbatim - the singing bowl gap above is now closed by [ARMOUR_CREATION]/[BowfaCreation]):
 * - Helm: 50 shards + 1 crystal armour seed, 70 Smithing + 70 Crafting (boostable), 2,500 XP in both, starts at 2,500 charges.
 * - Legs: 100 shards + 2 seeds, 72 Smithing + 72 Crafting (boostable), 5,000 XP in both, starts at 2,500 charges.
 * - Body: 150 shards + 3 seeds, 74 Smithing + 74 Crafting (boostable), 7,500 XP in both, starts at 2,500 charges.
 * - Bow of Faerdhinen: 100 shards + 1 enhanced crystal weapon seed, requires completion of Song of the Elves (stubbed
 *   completed - see [gg.rsmod.plugins.content.items.osrs.QuestStubs] - not built as a real quest, per owner instruction),
 *   82 Smithing + 82 Crafting (boostable), 5,000 XP in both, starts at 10,000 charges.
 * - If the player doesn't meet the Smithing/Crafting requirement, "Conwenna or Reese" can sing it for an extra 60 shards
 *   (50 for the bow), no XP granted. Conwenna is not present anywhere in this cache (SOURCE_GAP, not invented); Reese
 *   (NPC 9623) is, so only her assist route is wired.
 * - Upgrading: bow + 2,000 shards at the singing bowl -> Bow of Faerdhinen (c), 82 Smithing[b]/Crafting[b], no XP; Reese's
 *   fee for this is an extra 1,000 shards. Passive corruption progress from consuming charges ("Check" option, 1% per
 *   2,000 charges used) is SOURCE_GAP/NOT BUILT - only the direct 2,000-shard singing-bowl route is implemented.
 * - Reverting the bow to its seed: inactive bow + 250 shards at the singing bowl -> enhanced crystal weapon seed (the
 *   existing plain "Uncharge" stays a shard-losing discharge only, per the Wiki's "not recommended" framing).
 * SOURCE_GAP (not built, recorded): Bow of Faerdhinen (c)'s own Uncharge result (stated to stay charged permanently, so
 * discharging it is unstated); crystal/Elven-Clan armour recolouring via Lliann's Wares and the crystal of Meilyr revert
 * (needs colour-variant item ids this cache does not carry - not invented). "Non-monster sources" is read as: only NPC
 * hits use armour charges.
 */
object CrystalEquipment {
    const val CHARGES_PER_SHARD = 100
    const val MAX_CHARGES = 20_000

    data class Piece(
        val active: Int,
        val inactive: Int,
        /** Crystal armour weight in the bonus formula: helm 1, legs 2, body 3. */
        val weight: Int,
    )

    val ARMOUR =
        listOf(
            Piece(Items.CRYSTAL_HELM, Items.CRYSTAL_HELM_INACTIVE, 1),
            Piece(Items.CRYSTAL_LEGS, Items.CRYSTAL_LEGS_INACTIVE, 2),
            Piece(Items.CRYSTAL_BODY, Items.CRYSTAL_BODY_INACTIVE, 3),
        )

    val BOWFA: Set<Int> = setOf(Items.BOW_OF_FAERDHINEN, Items.BOW_OF_FAERDHINEN_INACTIVE, Items.BOW_OF_FAERDHINEN_C)

    /** Charged item -> inactive item, for everything that uses crystal shard charges. */
    val INACTIVE_FOR: Map<Int, Int> =
        mapOf(Items.BOW_OF_FAERDHINEN to Items.BOW_OF_FAERDHINEN_INACTIVE, Items.CRYSTAL_BOW_OSRS to Items.CRYSTAL_BOW_OSRS_INACTIVE) +
            ARMOUR.associate { it.active to it.inactive }

    val CHARGED_FOR: Map<Int, Int> = INACTIVE_FOR.entries.associate { (charged, inactive) -> inactive to charged }

    /**
     * "Revert" target seed for crystal armour, active or inactive. The classic Crystal bow's inactive-only Revert is a
     * separate, already-bound route in `osrs_bows.plugin.kts`; its charged-bow Revert stays an open SOURCE_GAP (see
     * [crystal.plugin.kts's][gg.rsmod.plugins.content.items.osrs] header) rather than being folded in here unsourced.
     */
    val REVERT_SEED: Map<Int, Int> =
        ARMOUR.flatMap { listOf(it.active to Items.CRYSTAL_ARMOUR_SEED, it.inactive to Items.CRYSTAL_ARMOUR_SEED) }.toMap()

    fun isCrystalBow(weaponId: Int?): Boolean = weaponId != null && (weaponId in Bows.CRYSTAL_BOWS || weaponId in BOWFA)

    /** Sum of the worn active crystal armour weights (0..6). */
    fun armourWeight(player: Player): Int {
        val worn = EquipmentType.values().mapNotNull { player.getEquipment(it)?.id }.toSet()
        return ARMOUR.filter { it.active in worn }.sumOf { it.weight }
    }

    fun applyAccuracy(
        player: Player,
        roll: Double,
    ): Double = if (isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) accuracy(roll, armourWeight(player)) else roll

    fun applyDamage(
        player: Player,
        maxHit: Double,
    ): Double = if (isCrystalBow(player.getEquipment(EquipmentType.WEAPON)?.id)) damage(maxHit, armourWeight(player)) else maxHit

    fun accuracy(
        roll: Double,
        weight: Int,
    ): Double = floor(roll * (20 + weight) / 20.0)

    fun damage(
        maxHit: Double,
        weight: Int,
    ): Double = floor(maxHit * (40 + weight) / 40.0)

    fun charges(item: Item): Int = if (item.id in INACTIVE_FOR) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    /** The charged item holding [charges], or the inactive item at 0. */
    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val charged = CHARGED_FOR[item.id] ?: item.id.takeIf { it in INACTIVE_FOR } ?: return item
        val id = if (charges > 0) charged else INACTIVE_FOR.getValue(charged)
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Shards [available] add to [item] (100 charges each, never above 20,000). */
    fun shardsToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, (MAX_CHARGES - charges(item)) / CHARGES_PER_SHARD).coerceAtLeast(0)

    /** "one charge being depleted for each hit, whether it is a successful one or not" (the corrupted bow never). */
    fun afterBowShot(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        // OSRS-IMPORT bows: the crystal bow degrades one charge per shot too ("will last for ... 2,500 shots", "Crystal shards can be
        // used to recharge the weapon to a maximum of 20,000 charges, with each shard providing 100 charges").
        if (weapon.id != Items.BOW_OF_FAERDHINEN && weapon.id != Items.CRYSTAL_BOW_OSRS) return
        player.equipment[EquipmentType.WEAPON.id] = withCharges(weapon, charges(weapon) - 1)
    }

    /** "One charge is depleted for each successful hit that is received from combat", per worn active piece. */
    fun onHitReceived(player: Player) {
        EquipmentType.values().forEach { type ->
            val item = player.getEquipment(type) ?: return@forEach
            if (ARMOUR.none { it.active == item.id }) return@forEach
            player.equipment[type.id] = withCharges(item, charges(item) - 1)
        }
    }

    /** Singing bowl creation recipe for one crystal armour piece (see the class doc's "SEED SINGING" section). */
    data class CreationRecipe(
        val active: Int,
        val shards: Int,
        val seeds: Int,
        val smithing: Int,
        val crafting: Int,
        val xp: Double,
        /** Extra shards Reese charges instead of the level requirement. */
        val npcFeeShards: Int,
        val startCharges: Int,
    )

    val ARMOUR_CREATION: Map<Int, CreationRecipe> =
        mapOf(
            Items.CRYSTAL_HELM to CreationRecipe(Items.CRYSTAL_HELM, shards = 50, seeds = 1, smithing = 70, crafting = 70, xp = 2500.0, npcFeeShards = 60, startCharges = 2500),
            Items.CRYSTAL_LEGS to CreationRecipe(Items.CRYSTAL_LEGS, shards = 100, seeds = 2, smithing = 72, crafting = 72, xp = 5000.0, npcFeeShards = 60, startCharges = 2500),
            Items.CRYSTAL_BODY to CreationRecipe(Items.CRYSTAL_BODY, shards = 150, seeds = 3, smithing = 74, crafting = 74, xp = 7500.0, npcFeeShards = 60, startCharges = 2500),
        )

    /** Bow of Faerdhinen singing-bowl creation, corruption and seed-revert constants (see the class doc). */
    object BowfaCreation {
        const val SHARDS = 100
        const val SMITHING = 82
        const val CRAFTING = 82
        const val XP = 5000.0
        const val NPC_FEE_SHARDS = 50
        const val START_CHARGES = 10_000
        const val CORRUPT_SHARDS = 2000
        const val CORRUPT_NPC_FEE_SHARDS = 1000
        const val REVERT_SHARDS = 250
    }
}
