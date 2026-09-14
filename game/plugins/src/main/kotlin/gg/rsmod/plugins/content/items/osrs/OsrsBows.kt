package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import kotlin.math.floor

/**
 * OSRS-IMPORT batch bows - Craw's bow and Webweaver bow (OSRS Wiki raw wikitext 2026-09-14, "Craw's bow" / "Webweaver bow"):
 * - "It has to be charged with revenant ether to be fired, but it does provide its own ammo"; "it must first be activated with
 *   1,000 revenant ether"; "The 1,000 ether used to activate the bow does not count towards the ammo usage of the bow, thus
 *   additional ether must be added (up to 16,000) to fire it"; "uses 1 revenant ether per shot"; Uncharge: "all the revenant ether
 *   will return to your inventory".
 * - Passive: "an additional 50% ranged accuracy and damage boost is applied when attacking any NPC in the Wilderness, consuming 1
 *   ether per attack whether the player hits or not". Wiki DPS calculator (`BaseCalc.isRevWeaponBuffApplicable`, charged version in
 *   the Wilderness): attack roll trunc(x 3 / 2) and max hit trunc(x 3 / 2) after the Twisted bow / Salve step.
 * - Craw's bow (u): "dismantle an uncharged bow to receive 7,500 revenant ether". Webweaver bow (u): Craw's bow (u) + Fangs of
 *   Venenatis, 85 Fletching (boostable), 0 experience.
 * - Swarm (Webweaver): 50 % energy, "hits an enemy four times in succession with doubled accuracy, each dealing up to 40%, rounded
 *   up, of the player's max hit" (calculator: max - trunc(max x 6 / 10)); "consumes one charge when using the special attack".
 */
object RevenantBows {
    const val ACTIVATION_ETHER = 1_000
    const val MAX_ETHER = 16_000
    const val CRAWS_DISMANTLE_ETHER = 7_500
    const val WEBWEAVER_FLETCHING = 85
    const val SWARM_ENERGY = 50
    const val SWARM_HITS = 4
    const val SWARM_ACCURACY = 2.0

    /** ADAPTED: no sourced wording. */
    const val NO_ETHER_MESSAGE = "Your bow has no revenant ether to fire."

    val CHARGED_FOR = mapOf(Items.CRAWS_BOW_U to Items.CRAWS_BOW, Items.WEBWEAVER_BOW_U to Items.WEBWEAVER_BOW)
    val UNCHARGED_FOR = CHARGED_FOR.entries.associate { (uncharged, charged) -> charged to uncharged }
    val ALL: Set<Int> = CHARGED_FOR.keys + CHARGED_FOR.values

    fun ether(item: Item): Int = if (item.id in UNCHARGED_FOR) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    /** Ether taken from [available] when used on [item]: 1,000 to activate an uncharged bow, then ammo ether up to 16,000. */
    fun etherToTake(
        item: Item,
        available: Int,
    ): Int =
        if (item.id in CHARGED_FOR) {
            if (available < ACTIVATION_ETHER) 0 else ACTIVATION_ETHER + minOf(available - ACTIVATION_ETHER, MAX_ETHER)
        } else {
            minOf(available, MAX_ETHER - ether(item)).coerceAtLeast(0)
        }

    fun charge(
        item: Item,
        taken: Int,
    ): Item =
        if (item.id in CHARGED_FOR) {
            withEther(Item(CHARGED_FOR.getValue(item.id), item.amount).copyAttr(item), taken - ACTIVATION_ETHER)
        } else {
            withEther(item, ether(item) + taken)
        }

    /** Uncharge: the uncharged bow and every ether (ammo + activation) back. */
    fun uncharge(item: Item): Pair<Item, Int> {
        val uncharged = Item(UNCHARGED_FOR.getValue(item.id), item.amount).copyAttr(item).also { it.attr.remove(ItemAttribute.CHARGES) }
        return uncharged to ether(item) + ACTIVATION_ETHER
    }

    private fun withEther(
        item: Item,
        ether: Int,
    ): Item = Item(item.id, item.amount).copyAttr(item).also { it.attr[ItemAttribute.CHARGES] = ether.coerceIn(0, MAX_ETHER) }

    fun canFire(weapon: Item?): Boolean = weapon != null && weapon.id in UNCHARGED_FOR && ether(weapon) > 0

    fun wildernessBuff(
        player: Player,
        target: Pawn,
    ): Boolean = target is Npc && player.getEquipment(EquipmentType.WEAPON)?.id in UNCHARGED_FOR && player.tile.getWildernessLevel() > 0

    /** One ether per attack, hit or miss; an empty bow stays charged (activated). */
    fun afterShot(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id in UNCHARGED_FOR } ?: return
        player.equipment[EquipmentType.WEAPON.id] = withEther(weapon, ether(weapon) - 1)
    }

    fun swarmMaxHit(maxHit: Double): Double = maxHit - floor(maxHit * 6 / 10)
}

/**
 * Venator bow (OSRS Wiki "Venator bow" 2026-09-14): "made by combining five venator shards"; "can fire any type of arrow, including
 * dragon arrows"; charged with ancient essence, "Each ancient essence adds 1 charge ... up to 50,000 charges"; "One charge is deducted
 * per attack ... regardless of how many bounces occur"; "Un-charging the bow returns all essence"; "The fully charged bow will last
 * ... before reverting to its uncharged form". Passive: in a multicombat area "the arrow will try to bounce to another target near
 * the first. Then, the attack can bounce again, either back to the first target or to a third one. Each hit rolls accuracy
 * independently, and the second and third hits both have a max hit equal to two-thirds of the original max hit, rounded down";
 * "only hit targets within two tiles of the original target's centre"; "Bounces can still occur if an attack misses or one of the
 * targets dies". ADAPTED: the per-size centre/SW tile rules are approximated by a 2-tile square around the bouncing target.
 */
object VenatorBow {
    const val MAX_ESSENCE = 50_000
    const val SHARDS_PER_BOW = 5
    const val BOUNCE_RADIUS = 2
    const val BOUNCES = 2

    fun essence(item: Item): Int = if (item.id == Items.VENATOR_BOW) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun essenceToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, MAX_ESSENCE - essence(item)).coerceAtLeast(0)

    fun withEssence(
        item: Item,
        essence: Int,
    ): Item {
        val id = if (essence > 0) Items.VENATOR_BOW else Items.VENATOR_BOW_UNCHARGED
        return Item(id, item.amount).copyAttr(item).also {
            if (essence > 0) it.attr[ItemAttribute.CHARGES] = essence.coerceAtMost(MAX_ESSENCE) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    fun active(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.let { essence(it) > 0 } == true

    fun bounceMaxHit(originalMaxHit: Double): Double = floor(originalMaxHit * 2 / 3)

    fun afterShot(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == Items.VENATOR_BOW } ?: return
        player.equipment[EquipmentType.WEAPON.id] = withEssence(weapon, essence(weapon) - 1)
    }
}

/**
 * Scorching bow (OSRS Wiki "Scorching bow" 2026-09-14): "able to fire arrows up to dragon arrows, has a 30% accuracy and damage
 * bonus against demonic creatures" (calculator: trackAddFactor(x, demonbaneFactor(30)) on the attack roll and max hit). Made by
 * "using a tormented synapse on a magic longbow (u)", "level 74 Fletching (boostable)"; "The first scorching bow created on an account
 * grants 730 Fletching experience, whilst subsequent ones grant 73"; below the level "You attempt to string the bow, but burn yourself
 * in the process. You'll need a Fletching level of 74 to craft a scorching bow." dealing 4 damage that cannot kill, and below 6
 * hitpoints "I'm not sure I want to try that when I'm this injured."; "Revert ... returning only the tormented synapse".
 * Scorching shackles: 25 % energy; "regardless of whether it is a successful hit or not, will bind demons for 20 ticks (12 seconds)
 * and deal 1 burn damage in the process. It will deal an additional 1 burn damage every 4 ticks (2.4 seconds), for a total of 5 burn
 * damage"; against others "Scorching shackles won't work against a non-demon enemy."
 */
object ScorchingBow {
    const val DEMONBANE_PERCENT = 30
    const val SHACKLES_ENERGY = 25
    const val BIND_TICKS = 20
    const val BURN_HITS = 5
    const val BURN_INTERVAL_TICKS = 4
    const val FLETCHING_LEVEL = 74
    const val EXPERIENCE = 73.0
    const val FIRST_EXPERIENCE = 730.0
    const val FAIL_DAMAGE = 4
    const val FAIL_MIN_HITPOINTS = 6
    const val FAIL_MESSAGE = "You attempt to string the bow, but burn yourself in the process. You'll need a Fletching level of 74 to craft a scorching bow."
    const val INJURED_MESSAGE = "I'm not sure I want to try that when I'm this injured."
    const val NOT_DEMON_MESSAGE = "Scorching shackles won't work against a non-demon enemy."

    fun isDemon(target: Pawn): Boolean = target is Npc && target.isSpecies(NpcSpecies.DEMON)

    fun demonbane(
        player: Player,
        target: Pawn,
    ): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id == Items.SCORCHING_BOW && isDemon(target)
}

/**
 * Tonalztics of Ralos (OSRS Wiki 2026-09-14): one-handed thrown weapon, "not stackable and effectively provides unlimited ammo";
 * "When charged, the weapon will hit twice, with two independent damage rolls and an attack range of 7 tiles (9 on longrange).
 * Uncharged, the weapon hits a target once for 0-75% of the player's maximum ranged hit, with ... an attack range of 6 tiles (8 on
 * longrange). It can hold a maximum of 20,000 charges. One charge is consumed per throw, whether or not the special attack is used"
 * (calculator: max hit trunc(x 3 / 4) for both versions; two independent hits when charged). "Each charge requires 1 Sunfire splinter".
 * Division: 50 % energy, "increases accuracy by 50% and reduces the target's Defence level by 1/8 of the target's Magic level upon a
 * successful hit. This effect occurs for each individual hit".
 */
object Tonalztics {
    const val MAX_CHARGES = 20_000
    const val DIVISION_ENERGY = 50
    const val DIVISION_ACCURACY = 1.5

    val ALL = setOf(Items.TONALZTICS_OF_RALOS_UNCHARGED, Items.TONALZTICS_OF_RALOS)

    fun isTonalztics(itemId: Int?): Boolean = itemId in ALL

    fun charges(item: Item): Int = if (item.id == Items.TONALZTICS_OF_RALOS) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun splintersToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, MAX_CHARGES - charges(item)).coerceAtLeast(0)

    /** ADAPTED: at 0 charges the weapon returns to its uncharged form (no sourced empty-charged state). */
    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val id = if (charges > 0) Items.TONALZTICS_OF_RALOS else Items.TONALZTICS_OF_RALOS_UNCHARGED
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    fun hits(weapon: Item?): Int = if (weapon != null && charges(weapon) > 0) 2 else 1

    fun maxHit(maxHit: Double): Double = floor(maxHit * 3 / 4)

    fun divisionDrain(targetMagicLevel: Int): Int = targetMagicLevel / 8

    fun afterThrow(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == Items.TONALZTICS_OF_RALOS } ?: return
        player.equipment[EquipmentType.WEAPON.id] = withCharges(weapon, charges(weapon) - 1)
    }
}
