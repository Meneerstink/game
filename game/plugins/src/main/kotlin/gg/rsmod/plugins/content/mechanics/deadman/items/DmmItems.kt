package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * PROJECT48 DMM-ITEMS group A: the nut items of Deadman: Armageddon (OSRS Wiki pages of each item, read 21-11). The item ids are the
 * ones the importer gave (p48-items, ITEMS.tsv); Items.kt is not regenerated for them, so the ids live here.
 */
object DmmItems {
    const val ROYAL_SEED_POD = 25465
    const val ESCAPE_CRYSTAL = 25466
    const val CRYSTAL_OF_ECHOES = 25467
    const val ETERNAL_TELEPORT_CRYSTAL = 25468
    const val RING_OF_ENDURANCE = 25469
    const val RING_OF_ENDURANCE_UNCHARGED = 25470
    const val AMULET_OF_ETERNAL_GLORY = 25471
    const val ASH_SANCTIFIER = 25476
    const val IMBUED_HEART = 25477
    const val SATURATED_HEART = 25478
    const val BOOK_OF_THE_DEAD = 25479
    const val WILDERNESS_SWORD_1 = 25480
    const val WILDERNESS_SWORD_3 = 25482
    const val WILDERNESS_SWORD_4 = 25483

    /** Wilderness sword 3 + 4 (wiki): teleport to the Fountain of Rune; sword 4 without limit, sword 3 once per (real-world) day. */
    private val FOUNTAIN_OF_RUNE = Tile(3375, 3894, 0)
    private val SWORD_DAY = AttributeKey<Int>(persistenceKey = "wild_sword_day")

    fun wildernessSwordTeleport(
        player: Player,
        sword: Int,
    ) {
        player.canTeleport(TeleportType.JEWELRY) {
            if (sword == WILDERNESS_SWORD_3) {
                val today = java.time.LocalDate.now().toEpochDay().toInt()
                if (player.attr[SWORD_DAY] == today) {
                    player.message("You have already used the sword's teleport today. It recharges at midnight.")
                    return@canTeleport
                }
                player.attr[SWORD_DAY] = today
            }
            player.teleport(FOUNTAIN_OF_RUNE, TeleportType.JEWELRY)
        }
    }

    private val GRAND_TREE = Tile(2465, 3497, 0)

    /** Royal seed pod: "not consumed upon use and never runs out of charges"; works up to level 30 Wilderness (TeleportType.GRAND_SEED_POD). */
    fun royalSeedPod(player: Player) {
        player.canTeleport(TeleportType.GRAND_SEED_POD) {
            player.message("The Royal seed pod teleports you to the Grand Tree.")
            player.teleport(GRAND_TREE, TeleportType.GRAND_SEED_POD)
        }
    }

    /**
     * Escape crystal Teleport: acts like a respawn teleport (the player's respawn point, else the home), consumes the crystal, works up
     * to level 30 Wilderness (wiki: one of the few items that does). Not usable while it is worn as a ring of life (wiki), that mode is
     * the equipped toggle which is not built.
     */
    fun escapeCrystal(player: Player) {
        if (player.getEquipment(EquipmentType.RING)?.id == ESCAPE_CRYSTAL) {
            player.message("Remove the crystal from your ring slot to teleport with it.")
            return
        }
        player.canTeleport(TeleportType.JEWELRY) {
            val slot = player.inventory.getItemIndex(ESCAPE_CRYSTAL, skipAttrItems = false)
            if (slot == -1 || !player.inventory.remove(ESCAPE_CRYSTAL, beginSlot = slot).hasSucceeded()) return@canTeleport
            val to = player.attr[RESPAWN_TILE_ATTR]?.let { Tile.from30BitHash(it) } ?: player.world.gameContext.home
            player.message("The escape crystal shatters and teleports you away.")
            player.teleport(to, TeleportType.JEWELRY)
        }
    }

    /** Crystal of Echoes (wiki, Grid Master): stores one tile + hitpoints, prayer and special attack energy; no cooldown; ignores Wilderness limits. */
    private val MEM_X = AttributeKey<Int>(persistenceKey = "echo_x")
    private val MEM_Z = AttributeKey<Int>(persistenceKey = "echo_z")
    private val MEM_H = AttributeKey<Int>(persistenceKey = "echo_h")
    private val MEM_HP = AttributeKey<Int>(persistenceKey = "echo_hp")
    private val MEM_PRAYER = AttributeKey<Int>(persistenceKey = "echo_prayer")
    private val MEM_SPEC = AttributeKey<Int>(persistenceKey = "echo_spec")

    fun saveMemory(player: Player) {
        player.attr[MEM_X] = player.tile.x
        player.attr[MEM_Z] = player.tile.z
        player.attr[MEM_H] = player.tile.height
        player.attr[MEM_HP] = player.getCurrentLifepoints()
        player.attr[MEM_PRAYER] = player.skills.getCurrentLevel(Skills.PRAYER)
        player.attr[MEM_SPEC] = AttackTab.getEnergy(player)
        player.message("You store the memory of this place in the crystal.")
    }

    fun checkMemory(player: Player) {
        val x = player.attr[MEM_X]
        if (x == null) {
            player.message("The crystal holds no memory yet.")
            return
        }
        player.message("The crystal remembers ${player.attr[MEM_HP]} hitpoints, ${player.attr[MEM_PRAYER]} prayer points and ${player.attr[MEM_SPEC]}% special attack energy.")
    }

    fun teleportBack(player: Player) {
        val x = player.attr[MEM_X]
        val z = player.attr[MEM_Z]
        if (x == null || z == null) {
            player.message("The crystal holds no memory yet.")
            return
        }
        player.canTeleport(TeleportType.QUEST_RETURN) {
            player.teleport(Tile(x, z, player.attr[MEM_H] ?: 0), TeleportType.QUEST_RETURN)
            player.attr[MEM_HP]?.let { player.setCurrentLifepoints(it) }
            player.attr[MEM_PRAYER]?.let { player.skills.setCurrentLevel(Skills.PRAYER, it) }
            player.attr[MEM_SPEC]?.let { AttackTab.setEnergy(player, it.coerceIn(0, 100)) }
        }
    }

    /** Eternal teleport crystal: Lletya and Prifddinas do not exist on this server (no map), so it only says so. */
    fun eternalCrystal(player: Player) {
        player.message("Lletya and Prifddinas are not part of this world.")
    }

    /** Amulet of eternal glory: the glory amulet's four teleports with no charges to lose; works up to level 30 Wilderness (JEWELRY). */
    val ETERNAL_GLORY_TELEPORTS =
        linkedMapOf(
            "Edgeville" to Tile(3086, 3503, 0),
            "Karamja" to Tile(2917, 3175, 0),
            "Draynor Village" to Tile(3104, 3249, 0),
            "Al Kharid" to Tile(3293, 3162, 0),
        )

    fun eternalGlory(player: Player, to: Tile) {
        player.canTeleport(TeleportType.JEWELRY) {
            player.teleport(to, TeleportType.JEWELRY)
        }
    }

    /** Imbued heart (wiki): Magic +1 + 10% of the level (rounded down), once per 7 minutes; Saturated: +4 + 10%, 5 minutes. */
    val HEART_COOLDOWN = TimerKey(persistenceKey = "dmm_heart_cooldown", tickOffline = false, resetOnDeath = true)
    const val IMBUED_COOLDOWN_TICKS = 700
    const val SATURATED_COOLDOWN_TICKS = 500

    fun heartBoost(
        level: Int,
        saturated: Boolean,
    ): Int = level / 10 + if (saturated) 4 else 1

    fun invigorate(player: Player, saturated: Boolean) {
        if (player.timers.has(HEART_COOLDOWN)) {
            val minutes = (player.timers[HEART_COOLDOWN] + 99) / 100
            player.message("The heart is still drained of its power. Judging by how it feels, it will be ready in around $minutes minutes.")
            return
        }
        val base = player.skills.getMaxLevel(Skills.MAGIC)
        val boost = heartBoost(base, saturated)
        player.skills.alterCurrentLevel(Skills.MAGIC, boost, boost)
        player.timers[HEART_COOLDOWN] = if (saturated) SATURATED_COOLDOWN_TICKS else IMBUED_COOLDOWN_TICKS
        player.message("You feel your magical power surge.")
    }
    /** Ring of wealth (i): 25487 uncharged, 25488 (i5) .. 25492 (i1); each teleport uses a charge (same destinations as the plain ring). */
    const val RING_OF_WEALTH_I = 25487
    val RING_OF_WEALTH_I_CHARGED = intArrayOf(25488, 25489, 25490, 25491, 25492)

    fun wealthTeleport(
        player: Player,
        ringId: Int,
        to: Tile,
        equipped: Boolean,
    ) {
        player.canTeleport(TeleportType.JEWELRY) {
            val next = if (ringId == 25492) RING_OF_WEALTH_I else ringId + 1
            if (equipped) {
                if (player.getEquipment(EquipmentType.RING)?.id != ringId) return@canTeleport
                player.equipment[EquipmentType.RING.id] = gg.rsmod.game.model.item.Item(
                    gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilJeweller.keep(player, ringId, next),
                )
            } else {
                val slot = player.inventory.getItemIndex(ringId, skipAttrItems = false)
                if (slot == -1 || !player.inventory.remove(ringId, beginSlot = slot).hasSucceeded()) return@canTeleport
                player.inventory.add(gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilJeweller.keep(player, ringId, next), 1)
            }
            val left = 25493 - next
            player.message(if (next == RING_OF_WEALTH_I) "You use your ring of wealth's last charge." else "Your ring of wealth has $left charges left.")
            player.teleport(to, TeleportType.JEWELRY)
        }
    }
}
