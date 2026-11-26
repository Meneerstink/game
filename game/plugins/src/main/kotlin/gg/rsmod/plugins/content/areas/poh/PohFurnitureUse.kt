package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell
import gg.rsmod.plugins.content.skills.prayer.burying.BoneData

/**
 * CONSTRUCTION-99: what the furniture a player built does (owner 2026-11-26 "volledig ... alles van construction").
 *
 * Sources: Darkan `HouseController` (rev 727) for the take-item lists of larders, shelves, tool stores and weapon racks, the sit
 * animations (`HouseConstants` CHAIR/BENCH/THRONE_EMOTES), the lecterns' tablets (`TabletMaking`: tablet, Magic level, runes,
 * experience, which lectern makes which) and the portal ids; the OSRS Wiki "Chapel" page for the altar bonus (oak 100% .. gilded
 * 250% of the bone's experience, +50% for each lit incense burner, a clean marrentill and a tinderbox per burner) and "Portal
 * chamber" for directing a portal (the teleport's runes x100 and its Magic level).
 */
object PohFurnitureUse {
    // ---- sitting ----

    private val SIT: Map<Int, Int> =
        (13581..13587).zip(listOf(4073, 4075, 4079, 4081, 4083, 4085, 4087)).toMap() +
            (13300..13306).zip(listOf(4089, 4091, 4093, 4095, 4097, 4099, 4101)).toMap() +
            (13665..13671).zip(listOf(4111, 4112, 4113, 4114, 4115, 4116, 4117)).toMap() +
            mapOf(13694 to 4097, 13695 to 4099, 13696 to 4101)

    // ---- take-item furniture (Darkan HouseController.handleLarders / handleShelves / handleTools / handleWeapons) ----

    private val TAKE: Map<Int, IntArray> =
        mapOf(
            13565 to intArrayOf(7738, 1927),
            13566 to intArrayOf(7738, 1927, 1944, 1933),
            13567 to intArrayOf(7738, 1927, 1944, 1933, 6701, 1550, 1957, 1985),
            13545 to intArrayOf(7688, 7702, 7728),
            13546 to intArrayOf(7688, 7702, 7728, 7742),
            13547 to intArrayOf(7688, 7714, 7732, 7742, 1887),
            13548 to intArrayOf(7688, 7714, 7732, 7742, 1923),
            13549 to intArrayOf(7688, 7714, 7732, 7742, 1923, 1887),
            13550 to intArrayOf(7688, 7726, 7735, 7742, 1923, 2313, 1931),
            13551 to intArrayOf(7688, 7726, 7735, 7742, 1923, 2313, 1931, 1949),
            13699 to intArrayOf(8794, 2347, 1755, 1735),
            13700 to intArrayOf(1925, 946, 952, 590),
            13701 to intArrayOf(1757, 1785, 1733),
            13702 to intArrayOf(1592, 1595, 1597, 1599, 11065, 5523),
            13703 to intArrayOf(5341, 5343, 5329, 5331, 5325, 952),
            13381 to intArrayOf(7671, 7673),
            13382 to intArrayOf(7671, 7673, 7675, 7676),
            13383 to intArrayOf(7671, 7673, 7675, 7676, 7679),
        )

    // ---- chapel ----

    /** Altars: three locs each (no burner lit, one, both); OSRS Wiki "Chapel" bonus with no burner lit. */
    private val ALTAR_BASES = (13179..13197 step 3).toList()
    private val ALTAR_BONUS = listOf(1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5)
    val ALTARS: Set<Int> = (13179..13199).toSet()

    fun altarBase(id: Int): Int = 13179 + ((id - 13179) / 3) * 3

    /** Unlit -> lit incense burners (oak, mahogany, marble). */
    private val BURNERS = mapOf(13208 to 13209, 13210 to 13211, 13212 to 13213)
    private val LIT_BURNERS = BURNERS.values.toSet()

    /** Unlit -> lit fireplaces (clay, limestone, marble). */
    private val FIREPLACES = mapOf(13609 to 13610, 13611 to 13612, 13613 to 13614)

    // ---- portal chamber ----

    /** Portal frame -> its first focused portal (Varrock .. Kharyrll), teak / mahogany / marble. */
    val PORTAL_FRAMES = mapOf(13636 to 13615, 13637 to 13622, 13638 to 13629)

    private class Destination(val name: String, val spell: TeleportSpell, val level: Int, val runes: List<Pair<Int, Int>>)

    private val DESTINATIONS =
        listOf(
            Destination("Varrock", TeleportSpell.VARROCK, 25, listOf(Items.LAW_RUNE to 1, Items.AIR_RUNE to 3, Items.FIRE_RUNE to 1)),
            Destination("Lumbridge", TeleportSpell.LUMBRIDGE, 31, listOf(Items.LAW_RUNE to 1, Items.AIR_RUNE to 3, Items.EARTH_RUNE to 1)),
            Destination("Falador", TeleportSpell.FALADOR, 37, listOf(Items.LAW_RUNE to 1, Items.AIR_RUNE to 3, Items.WATER_RUNE to 1)),
            Destination("Camelot", TeleportSpell.CAMELOT, 45, listOf(Items.LAW_RUNE to 1, Items.AIR_RUNE to 5)),
            Destination("Ardougne", TeleportSpell.ARDOUGNE, 51, listOf(Items.LAW_RUNE to 2, Items.WATER_RUNE to 2)),
            Destination("Yanille", TeleportSpell.WATCHTOWER, 58, listOf(Items.LAW_RUNE to 2, Items.EARTH_RUNE to 2)),
            Destination("Kharyrll", TeleportSpell.KHARYRLL, 66, listOf(Items.LAW_RUNE to 2, Items.BLOOD_RUNE to 1)),
        )

    /** Every focused portal loc -> its destination index. */
    val PORTALS: Map<Int, Int> = PORTAL_FRAMES.values.flatMap { base -> (0..6).map { base + it to it } }.toMap()

    // ---- lecterns (Darkan TabletMaking) ----

    private class Tablet(val name: String, val item: Int, val level: Int, val runes: List<Pair<Int, Int>>, val xp: Double)

    private val SAPPHIRE = Tablet("Enchant sapphire", 8016, 7, listOf(Items.COSMIC_RUNE to 1, Items.WATER_RUNE to 1), 13.2)
    private val EMERALD = Tablet("Enchant emerald", 8017, 27, listOf(Items.COSMIC_RUNE to 1, Items.AIR_RUNE to 3), 27.8)
    private val RUBY = Tablet("Enchant ruby", 8018, 49, listOf(Items.COSMIC_RUNE to 1, Items.FIRE_RUNE to 5), 44.3)
    private val DIAMOND = Tablet("Enchant diamond", 8019, 57, listOf(Items.COSMIC_RUNE to 1, Items.EARTH_RUNE to 10), 50.3)
    private val DRAGONSTONE = Tablet("Enchant dragonstone", 8020, 68, listOf(Items.COSMIC_RUNE to 1, Items.WATER_RUNE to 15, Items.EARTH_RUNE to 15), 58.5)
    private val ONYX = Tablet("Enchant onyx", 8021, 87, listOf(Items.COSMIC_RUNE to 1, Items.EARTH_RUNE to 20, Items.FIRE_RUNE to 20), 72.8)
    private val VARROCK = Tablet("Varrock teleport", 8007, 25, listOf(Items.AIR_RUNE to 3, Items.LAW_RUNE to 1, Items.FIRE_RUNE to 1), 35.0)
    private val LUMBRIDGE = Tablet("Lumbridge teleport", 8008, 31, listOf(Items.AIR_RUNE to 3, Items.EARTH_RUNE to 1, Items.LAW_RUNE to 1), 41.0)
    private val FALADOR = Tablet("Falador teleport", 8009, 37, listOf(Items.AIR_RUNE to 3, Items.WATER_RUNE to 1, Items.LAW_RUNE to 1), 48.0)
    private val CAMELOT = Tablet("Camelot teleport", 8010, 45, listOf(Items.AIR_RUNE to 5, Items.LAW_RUNE to 1), 55.5)
    private val ARDOUGNE = Tablet("Ardougne teleport", 8011, 51, listOf(Items.WATER_RUNE to 2, Items.LAW_RUNE to 2), 63.5)
    private val WATCHTOWER = Tablet("Watchtower teleport", 8012, 58, listOf(Items.EARTH_RUNE to 2, Items.LAW_RUNE to 2), 68.0)
    private val HOUSE = Tablet("Teleport to house", 8013, 40, listOf(Items.AIR_RUNE to 1, Items.EARTH_RUNE to 1, Items.LAW_RUNE to 1), 30.0)
    private val BANANAS = Tablet("Bones to bananas", 8014, 15, listOf(Items.WATER_RUNE to 2, Items.EARTH_RUNE to 2, Items.NATURE_RUNE to 1), 25.0)
    private val PEACHES = Tablet("Bones to peaches", 8015, 60, listOf(Items.WATER_RUNE to 4, Items.EARTH_RUNE to 4, Items.NATURE_RUNE to 2), 35.5)

    /** Lectern loc (oak 13642 .. mahogany demon 13648) -> its tablets (Darkan TabletMaking.ENABLED_SLOTS). */
    private val LECTERNS: Map<Int, List<Tablet>> =
        mapOf(
            13642 to listOf(SAPPHIRE, VARROCK),
            13643 to listOf(SAPPHIRE, VARROCK, FALADOR, LUMBRIDGE),
            13644 to listOf(SAPPHIRE, EMERALD, VARROCK, BANANAS),
            13645 to listOf(SAPPHIRE, VARROCK, FALADOR, LUMBRIDGE, CAMELOT, ARDOUGNE),
            13646 to listOf(SAPPHIRE, EMERALD, RUBY, DIAMOND, VARROCK, BANANAS),
            13647 to listOf(VARROCK, LUMBRIDGE, FALADOR, CAMELOT, ARDOUGNE, WATCHTOWER, HOUSE),
            13648 to listOf(SAPPHIRE, EMERALD, RUBY, DIAMOND, DRAGONSTONE, ONYX, VARROCK, BANANAS, PEACHES),
        )

    /** Every loc this file gives an option to. */
    fun handles(id: Int): Boolean =
        id in SIT || id in TAKE || id in BURNERS || id in FIREPLACES || id in LECTERNS || id in PORTALS || id == 13639 || id == 13640 || id == 13641 ||
            id == 13308 || id == 13309 || (id in ALTARS && id != PlayerHouse.ALTAR) || id in 13597..13599

    /** A furniture option inside a house; true when handled. */
    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val id = obj.id
        when {
            option == "sit-on" && id in SIT -> sit(player, obj, SIT.getValue(id))
            option == "search" && id in TAKE -> player.queue { take(this, TAKE.getValue(id)) }
            option == "search" && id in 13597..13599 -> player.message("You search the bookcase but find nothing of interest.")
            option == "light" && id in BURNERS -> lightBurner(player, obj)
            option == "light" && id in FIREPLACES -> lightFireplace(player, obj)
            option == "pray" && id in ALTARS && id != PlayerHouse.ALTAR -> pray(player)
            option == "study" && id in LECTERNS -> {
                if (PlayerHouse.isBuildMode(player)) {
                    player.message("You cannot do this while in building mode.")
                } else {
                    player.queue { study(this, LECTERNS.getValue(id)) }
                }
            }
            option == "enter" && id in PORTALS && id !in setOf(PlayerHouse.VARROCK_PORTAL, PlayerHouse.FALADOR_PORTAL, PlayerHouse.CAMELOT_PORTAL) ->
                enterPortal(player, PORTALS.getValue(id))
            option == "direct-portal" && (id == 13639 || id == 13640 || id == 13641) -> player.queue { directPortal(this, obj) }
            option == "ring" && (id == 13308 || id == 13309) -> {
                if (PlayerHouse.ownsHouseAt(player)) PohServant.call(player) else player.message("I'd better not do this...")
            }
            else -> return false
        }
        return true
    }

    private fun sit(
        player: Player,
        chair: GameObject,
        anim: Int,
    ) {
        val origin = player.tile
        player.queue {
            terminateAction = {
                player.animate(-1)
                if (player.tile == chair.tile) player.moveTo(origin)
            }
            player.moveTo(chair.tile)
            // Facing as the chair: rotation 0 west .. 3 south of a type-10/11 loc faces the opposite way.
            val face =
                when (chair.rot) {
                    0 -> chair.tile.transform(0, -1)
                    1 -> chair.tile.transform(-1, 0)
                    2 -> chair.tile.transform(0, 1)
                    else -> chair.tile.transform(1, 0)
                }
            player.faceTile(face)
            wait(1)
            // Darkan SitChair: the sitting animation every cycle while the player stays on the chair.
            repeat(1000) {
                if (player.tile != chair.tile) return@queue
                player.animate(anim)
                wait(1)
            }
        }
    }

    private suspend fun take(
        task: QueueTask,
        items: IntArray,
    ) {
        val player = task.player
        val names = items.map { player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, it).name }
        val pieces = items.mapIndexed { i, item -> PohBuild.Piece(item, names[i], 0, 0.0, emptyList()) }
        val picked = PohBuild.choose(task, pieces, "Take what?") { it.name } ?: return
        if (player.inventory.freeSlotCount == 0) {
            player.message("You don't have enough inventory space.")
            return
        }
        player.inventory.add(picked.obj, 1)
        player.message("You take ${if (picked.name.lowercase().endsWith("s")) "some" else "a"} ${picked.name.lowercase()}.")
    }

    private fun objectsInRoom(
        player: Player,
        at: Tile,
    ): List<GameObject> {
        val chunk = player.world.chunks.get(at.chunkCoords, createIfNeeded = false) ?: return emptyList()
        return chunk.getEntities<GameObject>(EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).filter { it.tile.height == at.height }
    }

    /** The room's altar shows how many incense burners are lit (its three locs). */
    private fun refreshAltar(
        player: Player,
        at: Tile,
    ) {
        val world = player.world
        val objs = objectsInRoom(player, at)
        val lit = objs.count { it.id in LIT_BURNERS }.coerceAtMost(2)
        objs.filter { it.id in ALTARS }.forEach { altar ->
            val id = altarBase(altar.id) + lit
            if (id != altar.id) world.spawn(DynamicObject(id, altar.type, altar.rot, altar.tile))
        }
    }

    private fun lightBurner(
        player: Player,
        burner: GameObject,
    ) {
        if (!player.inventory.contains(Items.TINDERBOX_590)) {
            player.message("You need a tinderbox to light the burner.")
            return
        }
        if (!player.inventory.contains(Items.CLEAN_MARRENTILL)) {
            player.message("You need a clean marrentill to burn in the incense burner.")
            return
        }
        val world = player.world
        player.inventory.remove(Items.CLEAN_MARRENTILL, 1)
        player.animate(3687)
        player.playSound(Sfx.FIRE_LIT)
        val lit = DynamicObject(BURNERS.getValue(burner.id), burner.type, burner.rot, burner.tile)
        world.spawn(lit)
        player.message("You burn some marrentill in the incense burner.")
        refreshAltar(player, burner.tile)
        // OSRS Wiki "Incense burner": burns about two minutes, longer with a higher Firemaking level.
        val ticks = 200 + player.skills.getMaxLevel(Skills.FIREMAKING)
        val unlitId = burner.id
        world.queue {
            wait(ticks)
            val still = PohRooms.objectsAt(world, lit.tile).firstOrNull { it.id == lit.id } ?: return@queue
            world.spawn(DynamicObject(unlitId, still.type, still.rot, still.tile))
            if (player.isOnline) refreshAltar(player, still.tile)
        }
    }

    private fun lightFireplace(
        player: Player,
        fireplace: GameObject,
    ) {
        val logs = intArrayOf(Items.LOGS, Items.OAK_LOGS, Items.WILLOW_LOGS, Items.MAPLE_LOGS, Items.YEW_LOGS, Items.MAGIC_LOGS).firstOrNull { player.inventory.contains(it) }
        if (!player.inventory.contains(Items.TINDERBOX_590) || logs == null) {
            player.message("You need some logs and a tinderbox to light the fireplace.")
            return
        }
        val world = player.world
        player.inventory.remove(logs, 1)
        player.animate(3658)
        player.playSound(Sfx.FIRE_LIT)
        val lit = DynamicObject(FIREPLACES.getValue(fireplace.id), fireplace.type, fireplace.rot, fireplace.tile)
        world.spawn(lit)
        player.addXp(Skills.FIREMAKING, 40.0, checkBrawlingGloves = true)
        val unlitId = fireplace.id
        world.queue {
            wait(500)
            val still = PohRooms.objectsAt(world, lit.tile).firstOrNull { it.id == lit.id } ?: return@queue
            world.spawn(DynamicObject(unlitId, still.type, still.rot, still.tile))
        }
    }

    private fun pray(player: Player) {
        player.queue {
            player.animate(gg.rsmod.plugins.api.cfg.Anims.ALTAR_PRAY)
            player.playSound(Sfx.PRAYER_RECHARGE)
            gg.rsmod.plugins.content.mechanics.prayer.Prayers.rechargePrayerPoints(player)
            player.filterableMessage("You recharge your Prayer points.")
        }
    }

    /** Bones on a built altar: the OSRS Wiki "Chapel" bonus for this altar and the burners lit around it. */
    fun offerBones(
        player: Player,
        altar: GameObject,
        bone: Int,
    ) {
        val data = BoneData.boneDefinitions[bone] ?: return
        val base = altarBase(altar.id)
        val bonus = ALTAR_BONUS[ALTAR_BASES.indexOf(base)] + 0.5 * (altar.id - base)
        if (!player.inventory.remove(bone).hasSucceeded()) return
        player.animate(3705)
        player.graphic(624)
        player.playSound(Sfx.PRAYER_RECHARGE)
        player.filterableMessage("The gods are pleased with your offering.")
        player.addXp(Skills.PRAYER, data.experience * bonus, checkBrawlingGloves = true)
    }

    private suspend fun study(
        task: QueueTask,
        tablets: List<Tablet>,
    ) {
        val player = task.player
        val pieces = tablets.map { PohBuild.Piece(it.item, it.name, it.level, it.xp, emptyList()) }
        val picked = PohBuild.choose(task, pieces, "Make which tablet?") ?: return
        val tablet = tablets[pieces.indexOf(picked)]
        val amount =
            when (task.options("1", "5", "10", "All", title = "How many?")) {
                1 -> 1
                2 -> 5
                3 -> 10
                4 -> 28
                else -> return
            }
        val runes = tablet.runes.map { (id, qty) -> Item(id, qty) }
        repeat(amount) {
            if (!player.inventory.contains(Items.SOFT_CLAY)) {
                player.message("You need some soft clay to make tablets.")
                return
            }
            if (!MagicSpells.canCast(player, tablet.level, runes)) return
            MagicSpells.removeRunes(player, runes, -1)
            player.inventory.remove(Items.SOFT_CLAY, 1)
            player.animate(3652)
            task.wait(3)
            player.inventory.add(tablet.item, 1)
            player.addXp(Skills.MAGIC, tablet.xp, checkBrawlingGloves = true)
        }
    }

    private fun enterPortal(
        player: Player,
        index: Int,
    ) {
        val spell = DESTINATIONS[index].spell
        player.canTeleport(TeleportType.MODERN) {
            player.moveTo(gg.rsmod.game.model.collision.Landing.pick(player.world.collision, spell.endArea))
        }
    }

    /**
     * The teleport focus: direct one of the room's portal frames (or portals) to a destination - the teleport's Magic level and
     * 100 times its runes (OSRS Wiki "Portal chamber"). Saved with the furniture ("KEY#destination").
     */
    private suspend fun directPortal(
        task: QueueTask,
        focus: GameObject,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player)) {
            player.message("Only the owner of the house can direct the portals.")
            return
        }
        if (!PlayerHouse.isBuildMode(player)) {
            // OSRS directs portals outside building mode as well; the house reloads after a change.
        }
        val room = PohRooms.builtAt(player, focus.tile) ?: return
        val corner = PlayerHouse.cellCorner(focus.tile) ?: return
        val style = PlayerHouse.styleOf(player)
        val built = PohRooms.furniture(player)
        val portals =
            PohRooms.spots(player.world, style, room, corner).filter { s ->
                built[s.key(room)]?.let { PohRooms.decoKey(it) } in setOf("TEAK_PORTAL", "MAHOGANY_PORTAL", "MARBLE_PORTAL")
            }
        if (portals.isEmpty()) {
            task.messageBox("There are no portal frames in this room to direct.")
            return
        }
        val which =
            if (portals.size == 1) {
                portals[0]
            } else {
                val labels = portals.mapIndexed { i, s -> "Portal ${i + 1}" + (PohRooms.variantOf(built[s.key(room)]!!)?.let { " (${DESTINATIONS[it].name})" } ?: "") }
                portals.getOrNull(task.options(*labels.toTypedArray(), title = "Direct which portal?") - 1) ?: return
            }
        val dests = DESTINATIONS.map { PohBuild.Piece(0, it.name, it.level, 0.0, it.runes.map { (r, q) -> r to q * 100 }) }
        val picked = PohBuild.choose(task, dests, "Direct the portal to") ?: return
        val dest = DESTINATIONS[dests.indexOf(picked)]
        val runes = dest.runes.map { (id, qty) -> Item(id, qty * 100) }
        if (!MagicSpells.canCast(player, dest.level, runes)) return
        MagicSpells.removeRunes(player, runes, -1)
        val key = PohRooms.decoKey(built.getValue(which.key(room)))
        PohRooms.setFurniture(player, which.key(room), "$key#${DESTINATIONS.indexOf(dest)}")
        player.playSound(Sfx.POH_BUILD_WOOD)
        task.messageBox("The portal now leads to ${dest.name}.")
        PlayerHouse.rebuild(player)
    }
}
