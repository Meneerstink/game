package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * Revenant chainmaces and sceptres (owner 2026-09-29: "wilderness weapons, all of them", fully working). OSRS Wiki item pages:
 * - Viggora's chainmace (60 Attack) / Ursine chainmace (70 Attack), Thammaron's sceptre (60 Magic) / Accursed sceptre (70 Magic):
 *   "Activation cost: 1,000 revenant ether" (a one-time deposit), "Maximum capacity: 16,000 additional ether", "consuming 1 ether
 *   per attack whether the player hits or not"; "The ether can be safely removed (including the activation deposit) by selecting
 *   the right-click Uncharge option".
 * - Passive: "An additional 50% melee accuracy and damage boost is applied when attacking any NPC in the Wilderness" (chainmaces);
 *   "an additional 50% magic accuracy and damage boost ... when attacking any NPC in the Wilderness" (sceptres) - "It is also
 *   applied to any combat spells cast when it is equipped".
 * - Sceptres: built-in spell max hit ⌊Magic/3⌋ - 8 (Thammaron's) / Magic/3 - 6 (Accursed); "unlike other powered staves, the
 *   sceptre can attack players outside of minigames"; "Swap" turns it into the "(a)" variant "that can autocast offensive combat
 *   spells" of the standard spellbook and Ancient Magicks.
 * The bows (Craw's, Webweaver) keep their own [RevenantBows]; the charging rules are the same.
 */
object RevenantWeapons {
    const val ACTIVATION_ETHER = 1_000
    const val MAX_ETHER = 16_000

    /** Uncharged -> charged (activated) id. */
    val CHARGED_FOR: Map<Int, Int> =
        mapOf(
            Items.VIGGORAS_CHAINMACE_U to Items.VIGGORAS_CHAINMACE,
            Items.URSINE_CHAINMACE_U to Items.URSINE_CHAINMACE,
            Items.THAMMARONS_SCEPTRE_U to Items.THAMMARONS_SCEPTRE,
            Items.THAMMARONS_SCEPTRE_AU to Items.THAMMARONS_SCEPTRE_A,
            Items.ACCURSED_SCEPTRE_U to Items.ACCURSED_SCEPTRE,
            Items.ACCURSED_SCEPTRE_AU to Items.ACCURSED_SCEPTRE_A,
        )
    val UNCHARGED_FOR: Map<Int, Int> = CHARGED_FOR.entries.associate { (uncharged, charged) -> charged to uncharged }
    val ALL: Set<Int> = CHARGED_FOR.keys + CHARGED_FOR.values

    val CHAINMACES: Set<Int> = setOf(Items.VIGGORAS_CHAINMACE, Items.URSINE_CHAINMACE, Items.VIGGORAS_CHAINMACE_U, Items.URSINE_CHAINMACE_U)
    val SCEPTRES: Set<Int> = ALL - CHAINMACES

    /** "Swap": built-in spell variant <-> autocasting "(a)" variant, charged and uncharged. */
    val SWAP: Map<Int, Int> =
        mapOf(
            Items.THAMMARONS_SCEPTRE to Items.THAMMARONS_SCEPTRE_A,
            Items.THAMMARONS_SCEPTRE_U to Items.THAMMARONS_SCEPTRE_AU,
            Items.ACCURSED_SCEPTRE to Items.ACCURSED_SCEPTRE_A,
            Items.ACCURSED_SCEPTRE_U to Items.ACCURSED_SCEPTRE_AU,
        ).let { forward -> forward + forward.entries.associate { (a, b) -> b to a } }

    fun isCharged(itemId: Int?): Boolean = itemId != null && itemId in UNCHARGED_FOR

    fun ether(item: Item): Int = if (isCharged(item.id)) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    /** Ether taken from [available] when used on [item]: 1,000 to activate an uncharged weapon, then up to 16,000 more. */
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

    /** Uncharge: the uncharged weapon and every ether (the stored ether and the activation deposit) back. */
    fun uncharge(item: Item): Pair<Item, Int> {
        val uncharged = Item(UNCHARGED_FOR.getValue(item.id), item.amount).copyAttr(item).also { it.attr.remove(ItemAttribute.CHARGES) }
        return uncharged to ether(item) + ACTIVATION_ETHER
    }

    fun withEther(
        item: Item,
        ether: Int,
    ): Item = Item(item.id, item.amount).copyAttr(item).also { it.attr[ItemAttribute.CHARGES] = ether.coerceIn(0, MAX_ETHER) }

    /** "Swap" keeps the ether and every other attribute. */
    fun swapped(item: Item): Item? = SWAP[item.id]?.let { Item(it, item.amount).copyAttr(item) }

    /** The +50 % accuracy and damage: a charged weapon with ether, an NPC target, inside the Wilderness. */
    fun wildernessBuff(
        player: Player,
        target: Pawn,
    ): Boolean {
        // The ornate wilderness combat dummy counts as a Wilderness npc (OSRS Wiki "Ornate undead combat dummy").
        val wilderness = player.tile.getWildernessLevel() > 0 || gg.rsmod.plugins.content.areas.poh.PlayerHouse.isWildernessDummy(target)
        if (target !is Npc || !wilderness) return false
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
        return isCharged(weapon.id) && ether(weapon) > 0
    }

    /** "1 ether per attack whether the player hits or not"; an empty weapon stays activated. */
    fun afterAttack(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { isCharged(it.id) } ?: return
        if (ether(weapon) <= 0) return
        player.equipment[EquipmentType.WEAPON.id] = withEther(weapon, ether(weapon) - 1)
    }
}
