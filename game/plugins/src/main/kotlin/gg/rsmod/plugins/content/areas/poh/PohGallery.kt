package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.quests.tab.CollectionLogUi
import gg.rsmod.plugins.content.quests.tab.QuestTab

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C6): the OSRS-only Achievement gallery (OSRS Wiki "Achievement gallery": level 80, 200,000 coins,
 * doors north and south, one per house). Room template: OSRS house square 7535 (OsrsRegionImportTool batch "poh-osrs-7535"); its
 * pieces: OsrsLocImportTool batch "poh-gallery" (71364-71381) plus the house's own Altar of the Occult (62924) and ornate jewellery
 * box (62926). Options:
 *
 * - Ancient / Lunar altar "Venerate": switch to that book, or back to the standard one when already on it ([Spellbooks.select]
 *   keeps the quest and Magic gates). "Upgrade" builds the Altar of the Occult for the materials the altar did not already cost.
 *   The dark (Arceuus) altar does not exist in this revision, so the occult altar's materials include the dark altar's runes.
 * - Basic / fancy jewellery box "Teleport Menu": the house jewellery box screen with 2 / 4 of its 6 rows ([PohTeleportUi]);
 *   "Upgrade" to the next box for the OSRS upgrade row. The ornate box (62926) keeps its own bindings (all six rows).
 * - Adventure log "Read": the collection log (OSRS: boss kill counts and records). Quest list "Read": the quest tab.
 * - Boss lair display "Configure": one of the six boss displays. OSRS needs that boss's jar; this server has only the Kalphite
 *   Queen of the six, so her display needs one KQ kill and the other five are open (VERVANGINGEN). "Jars": the same choice.
 * - Mounted emblem / coins "Admire": the OSRS text.
 */
object PohGallery {
    const val ANCIENT_ALTAR = 71364
    const val LUNAR_ALTAR = 71365
    private val LOGS = 71366..71368
    const val BASIC_BOX = 71369
    const val FANCY_BOX = 71370
    private val LAIRS = (71371..71377).toList()
    const val EMBLEM = 71378
    const val COINS = 71379
    const val QUEST_LIST = 71381

    private val LAIR_NAMES = listOf("Empty", "Kraken", "Zulrah", "Kalphite Queen", "Cerberus", "Abyssal Sire", "Skotizo")

    private fun lairKey(i: Int) = if (i == 0) "GAL_BOSS_LAIR" else "GAL_LAIR_${LAIR_NAMES[i].uppercase().replace(' ', '_')}"

    val LAIR_KEYS: Set<String> = LAIR_NAMES.indices.map(::lairKey).toSet()

    fun handles(id: Int): Boolean =
        id == ANCIENT_ALTAR || id == LUNAR_ALTAR || id in LOGS || id == BASIC_BOX || id == FANCY_BOX || id in LAIRS || id == EMBLEM ||
            id == COINS || id == QUEST_LIST

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val id = obj.id
        when {
            (id == ANCIENT_ALTAR || id == LUNAR_ALTAR) && option == "venerate" -> {
                val book = if (id == ANCIENT_ALTAR) Spellbook.ANCIENT else Spellbook.LUNAR
                Spellbooks.select(player, if (player.getSpellbook() == book) Spellbook.STANDARD else book)
            }
            (id == ANCIENT_ALTAR || id == LUNAR_ALTAR) && option == "upgrade" ->
                player.queue { upgradeTo(this, obj, "GAL_OCCULT_ALTAR") }
            id == BASIC_BOX && option == "teleport menu" -> PohTeleportUi.openJewelleryBox(player, 2)
            id == FANCY_BOX && option == "teleport menu" -> PohTeleportUi.openJewelleryBox(player, 4)
            (id == BASIC_BOX || id == FANCY_BOX) && option == "upgrade" -> player.queue { PohConstruct.upgradePiece(this, obj) }
            id in LOGS && option == "read" -> {
                player.message("You read through your adventure log.")
                CollectionLogUi.open(player)
            }
            id == QUEST_LIST && option == "read" -> {
                player.message("You look over the list of your quests.")
                QuestTab.select(player, QuestTab.Tab.QUESTS)
            }
            id in LAIRS && (option == "configure" || option == "jars") -> player.queue { configure(this, obj) }
            id == EMBLEM && option == "admire" -> player.message("A fine emblem, earned in the fiercest of battles.")
            id == COINS && option == "admire" -> player.message("A hundred million coins - that's quite a pile.")
            else -> return false
        }
        return true
    }

    /** "Upgrade" on an Ancient or Lunar altar: the Altar of the Occult in its place. */
    private suspend fun upgradeTo(
        task: QueueTask,
        obj: GameObject,
        key: String,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player) || !PohBuild.requireBuildMode(player)) return
        val (room, spot) = PohRooms.spotAt(player, obj, matchLoc = false) ?: return
        PohConstruct.upgradeAt(task, room, spot, PohFurniture.DECOS.getValue(key))
    }

    /**
     * The upgrade of [from] to [to] when both are gallery pieces with an OSRS upgrade (the jewellery boxes, an altar to the occult
     * altar): the materials [to] needs that [from] did not cost, and the experience difference (the OSRS upgrade rows).
     */
    fun upgradeCost(
        from: PohFurniture.Deco,
        to: PohFurniture.Deco,
    ): PohBuild.Piece? {
        if (!from.key.startsWith("GAL_") || !to.key.startsWith("GAL_")) return null
        val have = from.materials.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val need =
            to.materials.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                .map { (item, qty) -> item to qty - (have[item] ?: 0) }
                .filter { it.second > 0 }
        return PohBuild.Piece(to.objs.first(), to.name, to.level, (to.xp - from.xp).coerceAtLeast(0.0), need)
    }

    /** A configured boss display is still the boss lair hotspot's piece: Remove sees the display. */
    fun baseDeco(deco: PohFurniture.Deco): PohFurniture.Deco = if (deco.key in LAIR_KEYS) PohFurniture.DECOS.getValue("GAL_BOSS_LAIR") else deco

    private suspend fun configure(
        task: QueueTask,
        obj: GameObject,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player)) {
            player.message("It's not your display to configure.")
            return
        }
        val (room, spot) = PohRooms.spotAt(player, obj, matchLoc = false) ?: return
        val current = LAIRS.indexOf(obj.id)
        val choices = LAIR_NAMES.indices.filter { it != current }
        val picked = task.options(*choices.map { LAIR_NAMES[it] }.toTypedArray(), title = "Display which boss?") - 1
        val shape = choices.getOrNull(picked) ?: return
        if (shape == 3 && runCatching { player.getNpcKillCount(Npcs.KALPHITE_QUEEN_1160) }.getOrDefault(0) < 1) {
            task.messageBox("You need to have killed the Kalphite Queen to display her.")
            return
        }
        player.lock = LockState.FULL
        task.wait(1)
        val world = player.world
        PohRooms.objectsAt(world, obj.tile).filter { it.id == obj.id }.forEach { world.remove(it) }
        world.spawn(DynamicObject(LAIRS[shape], obj.type, obj.rot, obj.tile))
        PohRooms.setFurniture(player, spot.key(room), lairKey(shape))
        player.unlock()
        player.message(if (shape == 0) "You clear the display." else "The display now shows ${LAIR_NAMES[shape]}.")
    }
}
