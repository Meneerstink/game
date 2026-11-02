package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * N522 bundle 3b: the two revenant items the OSRS revenant drop table needs.
 *
 * Bracelet of ethereum - OSRS Wiki (2026-11-02): holds up to 16,000 revenant ether; worn charged, revenant damage is reduced by 75%
 * and every revenant attack uses 1 charge, revenants are tolerant; toggled absorption puts the ether of killed revenants straight
 * into it; dismantling the uncharged bracelet gives 250 ether. Messages and flows: Kronos-184 item/actions/impl/jewellery/
 * BraceletOfEthereum.java ("Revenant ether: X%, N ether", "The bracelet can't hold anymore ether.", "Your bracelet has ran out of
 * charges!", "Bracelet of Ethereum absorption is now toggled on/off!", the dismantle dialogue). Not ported: the bank
 * "Configure-Charges" option and the death rule (bracelet always lost, ether to the killer) - that is the K01 death system.
 *
 * Amulet of avarice - OSRS Wiki: worn, the player is skulled and the skull cannot be removed; taken off, it acts like a normal PK
 * skull; +20% accuracy and damage against revenants (formulas: [avariceBoost]); drops in the Revenant Caves are noted except the
 * secondary (blighted) roll and the uniques ([RevenantCaveRevenants.table]). Not ported: the equip warning (its text is not on the
 * wiki or in a donor) and the Forinthry Surge (needs the Revenant maledictus).
 */
object RevenantGear {
    const val MAX_ETHER = 16_000
    const val DISMANTLE_ETHER = 250
    const val DAMAGE_FACTOR = 0.25

    /** Toggled ether absorption (persists like Kronos' autoCollectEther). */
    val ABSORB_ATTR = AttributeKey<Boolean>("bracelet_of_ethereum_absorb")

    private const val GREEN = "<col=006400>"
    private const val RED = "<col=ff0000>"

    // ------------------------------------------------------------------ amulet of avarice

    fun wearsAvarice(player: Player): Boolean = player.equipment[EquipmentType.AMULET.id]?.id == Items.AMULET_OF_AVARICE

    /** Every revenant: the OSRS cave ones and the rev-667 ones elsewhere in the Wilderness. */
    fun isRevenant(target: Pawn): Boolean =
        target is Npc && (target.id in RevenantCaveRevenants.BY_ID || target.id == RevenantMaledictus.NPC || target.id in gg.rsmod.plugins.content.areas.wilderness.Revenants.ids)

    fun avariceBoost(player: Player, target: Pawn): Boolean = wearsAvarice(player) && isRevenant(target)

    /** N522 Forinthry Surge (Revenant maledictus): "an additional 15% accuracy and damage buff against all revenants" (OSRS Wiki). */
    fun surgeBoost(player: Player, target: Pawn): Boolean = RevenantMaledictus.surge(player) && isRevenant(target)

    /** Donor AmuletOfAvaricePlugin "Check": "N minutes and M seconds" (each part only when above 0; 1 tick = 0.6 s). */
    fun surgeTimeText(ticks: Int): String {
        val total = ticks * 6 / 10
        val minutes = total / 60
        val seconds = total % 60
        return buildString {
            if (minutes > 0) append("$minutes minutes")
            if (minutes > 0 && seconds > 0) append(" and ")
            if (seconds > 0 || minutes == 0) append("$seconds seconds")
        }
    }

    /** Re-arms the skull every tick while the amulet is worn ([refreshAvariceSkull]); not saved, re-armed at login. */
    val AVARICE_TIMER = gg.rsmod.game.model.timer.TimerKey()

    /** Worn: skulled, and the skull is held at its full duration for as long as the amulet is worn. */
    fun onAvariceEquip(player: Player) {
        player.timers[SKULL_ICON_DURATION_TIMER] = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.SKULL_DURATION_CYCLES
        gg.rsmod.plugins.content.mechanics.pvp.RiskSkull.refresh(player)
        player.timers[AVARICE_TIMER] = 1
    }

    /** Every tick while worn: the skull back to its full duration. Taken off, the timer stops and the skull runs out normally. */
    fun refreshAvariceSkull(player: Player) {
        if (!wearsAvarice(player)) return
        player.timers[SKULL_ICON_DURATION_TIMER] = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.SKULL_DURATION_CYCLES
        player.timers[AVARICE_TIMER] = 1
    }

    // ------------------------------------------------------------------ bracelet of ethereum

    fun ether(item: Item): Int = if (item.id == Items.BRACELET_OF_ETHEREUM) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun wornBracelet(player: Player): Item? =
        player.equipment[EquipmentType.GLOVES.id]?.takeIf { it.id == Items.BRACELET_OF_ETHEREUM || it.id == Items.BRACELET_OF_ETHEREUM_UNCHARGED }

    fun wearsCharged(player: Player): Boolean = wornBracelet(player)?.let { ether(it) > 0 } == true

    fun absorbsEther(player: Player): Boolean = wornBracelet(player) != null && player.attr[ABSORB_ATTR] == true

    fun checkMessage(amount: Int): String {
        val pct = if (amount == 0) "0.0" else String.format(java.util.Locale.ROOT, "%.1f", amount * 100.0 / MAX_ETHER)
        return "Revenant ether: $GREEN$pct%, ${String.format(java.util.Locale.ROOT, "%,d", amount)} ether"
    }

    fun check(player: Player, item: Item) = player.message(checkMessage(ether(item)))

    fun toggleAbsorption(player: Player) {
        val on = player.attr[ABSORB_ATTR] != true
        player.attr[ABSORB_ATTR] = on
        player.message("${GREEN}Bracelet of Ethereum absorption is now toggled ${if (on) "on" else "off"}!")
    }

    /** Ether used on a bracelet (inventory slot [slot]): as much as fits, the bracelet becomes the charged one. */
    fun charge(player: Player, slot: Int) {
        val bracelet = player.inventory[slot] ?: return
        val have = player.inventory.getItemCount(Items.REVENANT_ETHER)
        val room = MAX_ETHER - ether(bracelet)
        if (room <= 0) {
            player.message("The bracelet can't hold anymore ether.")
            return
        }
        val add = minOf(room, have)
        if (add <= 0 || !player.inventory.remove(Items.REVENANT_ETHER, add).hasSucceeded()) return
        val charged = Item(Items.BRACELET_OF_ETHEREUM).apply { attr[ItemAttribute.CHARGES] = ether(bracelet) + add }
        player.inventory[slot] = charged
        check(player, charged)
    }

    /** Uncharge: the ether back into the inventory, the bracelet uncharged (Kronos). */
    fun uncharge(player: Player, slot: Int) {
        val bracelet = player.inventory[slot]?.takeIf { it.id == Items.BRACELET_OF_ETHEREUM } ?: return
        val amount = ether(bracelet)
        if (amount > 0 && !player.inventory.add(Items.REVENANT_ETHER, amount).hasSucceeded()) {
            player.message("You do not have enough space in your inventory to do this.")
            return
        }
        player.inventory[slot] = Item(Items.BRACELET_OF_ETHEREUM_UNCHARGED)
    }

    fun dismantle(player: Player, slot: Int) {
        if (player.inventory[slot]?.id != Items.BRACELET_OF_ETHEREUM_UNCHARGED) return
        player.inventory.remove(Items.BRACELET_OF_ETHEREUM_UNCHARGED, 1, beginSlot = slot)
        player.inventory.add(Items.REVENANT_ETHER, DISMANTLE_ETHER)
        player.message("You dismantle your bracelet and collect $DISMANTLE_ETHER revenant ethers.")
    }

    /** A revenant attack on a player wearing a charged bracelet: 75% less damage, 1 charge used ([maxHit] scaled). */
    fun revenantMaxHitAgainst(player: Player, maxHit: Int): Double {
        val bracelet = wornBracelet(player) ?: return maxHit.toDouble()
        val left = ether(bracelet)
        if (left <= 0) return maxHit.toDouble()
        if (left - 1 <= 0) {
            player.equipment[EquipmentType.GLOVES.id] = Item(Items.BRACELET_OF_ETHEREUM_UNCHARGED)
            player.message("${RED}Your bracelet has ran out of charges!")
        } else {
            bracelet.attr[ItemAttribute.CHARGES] = left - 1
        }
        return maxHit * DAMAGE_FACTOR
    }

    /** Absorbed ether goes into the worn bracelet up to 16,000; what does not fit is dropped at [tile] like a normal drop. */
    fun absorbEther(player: Player, amount: Int, tile: gg.rsmod.game.model.Tile) {
        val bracelet = wornBracelet(player)
        val room = if (bracelet == null) 0 else MAX_ETHER - ether(bracelet)
        val add = minOf(room, amount)
        if (add > 0) {
            val charged = Item(Items.BRACELET_OF_ETHEREUM).apply { attr[ItemAttribute.CHARGES] = ether(bracelet!!) + add }
            player.equipment[EquipmentType.GLOVES.id] = charged
        }
        val rest = amount - add
        if (rest > 0) player.world.spawn(gg.rsmod.game.model.entity.GroundItem(Items.REVENANT_ETHER, rest, tile, player))
    }
}
