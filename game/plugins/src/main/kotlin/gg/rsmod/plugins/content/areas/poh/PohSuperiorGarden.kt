package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.travel.FairyRings
import gg.rsmod.plugins.content.mechanics.travel.SpiritTrees

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C6): the OSRS-only Superior garden's pieces (OSRS Wiki "Superior garden" and the pages of its
 * pieces). The room, its hotspots and pieces are data ([PohRooms.RoomType.SUPERIOR_GARDEN], [PohFurnitureData] "SG_" rows); this
 * file gives the pieces their options:
 *
 * - Pools: "Drink" restores by tier (restoration: special attack; revitalisation: + run energy; rejuvenation: + prayer; fancy:
 *   + lowered stats). The ornate pool is the house's own [PlayerHouse.POOL] (its "Drink" restores everything). "Upgrade" (building
 *   mode) builds the next pool for the OSRS upgrade row ([UPGRADES]): OSRS builds pools only in that order.
 * - Spirit tree "Travel" / "Last-destination", fairy ring "Zanaris" / "Configure" / "Last-destination" / "Favourites": the world's
 *   spirit tree list and fairy ring dial, as the house's spiritual fairy tree ([PlayerHouse.SPIRITUAL_FAIRY_TREE], also the 95 piece).
 *   The obelisk is the house's Wilderness obelisk (14826, `wilderness_obelisk.plugin.kts` offers the destinations).
 * - Topiary "Clip" (secateurs or magic secateurs): the bush or one of the six boss shapes. OSRS needs one kill of that boss; this
 *   server has only the Kalphite Queen of the six, so the others are open to everyone (VERVANGINGEN, dossier CONSTRUCTION-99).
 * - Garden benches: "Sit-on" ([PohFurnitureUse]).
 * - Spirit tree, spirit tree & fairy ring: Farming 83 and the OSRS Farming experience; the topiary bush its Farming experience.
 */
object PohSuperiorGarden {
    private val POOLS = intArrayOf(71320, 71321, 71322, 71323)
    const val SPIRIT_TREE = 71324
    const val FAIRY_RING = 71325

    /** Topiary bush 71327 and its shapes (OSRS 29230-29236): Kraken, Zulrah, Kalphite Queen, Cerberus, Abyssal Sire, Skotizo. */
    private val TOPIARY = (71327..71333).toList()
    private val SHAPES = listOf("Bush", "Kraken", "Zulrah", "Kalphite Queen", "Cerberus", "Abyssal Sire", "Skotizo")

    /** Saved deco key of each topiary shape (the bush is the built piece "SG_TOPIARY"). */
    private fun shapeKey(i: Int) = if (i == 0) "SG_TOPIARY" else "SG_TOPIARY_${SHAPES[i].uppercase().replace(' ', '_')}"

    val TOPIARY_KEYS: Set<String> = SHAPES.indices.map(::shapeKey).toSet()

    /** OSRS Wiki pool upgrade rows (materials of that step and its experience). */
    val UPGRADES: Map<String, PohBuild.Piece> =
        mapOf(
            "SG_REVITALISATION_POOL" to PohBuild.Piece(71321, "Revitalisation pool", 70, 850.0, listOf(Items.STAMINA_POTION_4 to 10)),
            "SG_REJUVENATION_POOL" to PohBuild.Piece(71322, "Rejuvenation pool", 80, 900.0, listOf(Items.PRAYER_POTION_4 to 10)),
            "SG_FANCY_REJUVENATION_POOL" to PohBuild.Piece(71323, "Fancy rejuvenation pool", 85, 1950.0, listOf(Items.SUPER_RESTORE_4 to 10, 8786 to 2)),
            "SG_ORNATE_REJUVENATION_POOL" to
                PohBuild.Piece(PlayerHouse.POOL, "Ornate rejuvenation pool", 90, 3107.0, listOf(Items.ANTI_VENOM_4 to 10, 8784 to 5, Items.BLOOD_RUNE to 1000)),
        )

    /** OSRS Wiki: the spirit tree pieces need Farming 83 and give Farming experience; the topiary bush gives Farming experience. */
    private val FARMING: Map<String, Pair<Int, Double>> =
        mapOf("SG_SPIRIT_TREE" to (83 to 350.0), "SG_SPIRIT_FAIRY" to (83 to 885.0), "SG_TOPIARY" to (1 to 141.0))

    fun handles(id: Int): Boolean = id in POOLS || id == SPIRIT_TREE || id == FAIRY_RING || id in TOPIARY

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val id = obj.id
        when {
            id in POOLS && option == "drink" -> drink(player, POOLS.indexOf(id))
            id in POOLS && option == "upgrade" -> player.queue { PohConstruct.upgradePiece(this, obj) }
            id == SPIRIT_TREE && (option == "travel" || option == "last-destination") -> SpiritTrees.open(player)
            id == FAIRY_RING && option == "zanaris" ->
                player.canTeleport(FairyRings.Source.HOUSE.teleport) { player.teleport(FairyRings.ZANARIS, FairyRings.Source.HOUSE.teleport) }
            id == FAIRY_RING && (option == "configure" || option == "favourites") -> FairyRings.open(player, FairyRings.Source.HOUSE)
            id == FAIRY_RING && option == "last-destination" -> {
                val code = player.attr[FairyRings.LAST_CODE]
                if (code == null) FairyRings.open(player, FairyRings.Source.HOUSE) else FairyRings.travel(player, code, FairyRings.Source.HOUSE)
            }
            id in TOPIARY && option == "clip" -> player.queue { clip(this, obj) }
            else -> return false
        }
        return true
    }

    /** OSRS pool tiers: 0 restoration .. 3 fancy rejuvenation (each restores what the one before it does, and more). */
    private fun drink(
        player: Player,
        tier: Int,
    ) {
        if (!player.lock.canItemInteract()) return
        player.animate(Anims.EAT_FOOD)
        player.playSound(Sfx.LIQUID)
        AttackTab.setEnergy(player, 100)
        if (tier >= 1) player.runEnergy = 100.0
        if (tier >= 2) player.restorePrayer(player.getMaximumPrayerPoints())
        if (tier >= 3) {
            for (skill in 0 until player.skills.maxSkills) {
                if (skill == Skills.CONSTITUTION || skill == Skills.PRAYER) continue
                val base = player.skills.getMaxLevel(skill)
                if (player.skills.getCurrentLevel(skill) < base) player.skills.setCurrentLevel(skill, base)
            }
        }
        player.message(
            when (tier) {
                0 -> "You drink from the pool and feel your special attack restored."
                1 -> "You drink from the pool and feel refreshed."
                2 -> "You drink from the pool and feel revitalised."
                else -> "You drink from the pool and feel rejuvenated."
            },
        )
    }

    /** Called by [PohConstruct.placePiece] before a build: false (with the OSRS message) when the Farming level is too low. */
    internal suspend fun canPlant(
        task: QueueTask,
        deco: PohFurniture.Deco,
    ): Boolean {
        val level = FARMING[deco.key]?.first ?: return true
        if (task.player.skills.getCurrentLevel(Skills.FARMING) >= level) return true
        task.messageBox("You need a Farming level of $level to plant that.")
        return false
    }

    /** The Farming experience of a planted piece (after the Construction experience). */
    internal fun plantXp(
        player: Player,
        deco: PohFurniture.Deco,
    ) {
        val xp = FARMING[deco.key]?.second ?: return
        player.addXp(Skills.FARMING, xp)
    }

    /** A clipped topiary is still the topiary hotspot's piece: Remove and Upgrade see the bush. */
    fun baseDeco(deco: PohFurniture.Deco): PohFurniture.Deco = if (deco.key in TOPIARY_KEYS) PohFurniture.DECOS.getValue("SG_TOPIARY") else deco

    private suspend fun clip(
        task: QueueTask,
        obj: GameObject,
    ) {
        val player = task.player
        if (!PlayerHouse.ownsHouseAt(player)) {
            player.message("It's not your topiary to clip.")
            return
        }
        if (!player.inventory.contains(Items.SECATEURS) && !player.inventory.contains(Items.MAGIC_SECATEURS)) {
            task.messageBox("You need some secateurs to clip the topiary.")
            return
        }
        val (room, spot) = PohRooms.spotAt(player, obj, matchLoc = false) ?: return
        val current = TOPIARY.indexOf(obj.id)
        val choices = SHAPES.indices.filter { it != current }
        val picked = task.options(*choices.map { SHAPES[it] }.toTypedArray(), title = "Clip the topiary into...") - 1
        val shape = choices.getOrNull(picked) ?: return
        if (shape == 3 && KcGate.kalphiteQueen(player) < 1) {
            task.messageBox("You need to have killed the Kalphite Queen to clip it into that shape.")
            return
        }
        player.lock = LockState.FULL
        player.animate(TOPIARY_ANIM)
        task.wait(2)
        val world = player.world
        PohRooms.objectsAt(world, obj.tile).filter { it.id == obj.id }.forEach { world.remove(it) }
        world.spawn(DynamicObject(TOPIARY[shape], obj.type, obj.rot, obj.tile))
        PohRooms.setFurniture(player, spot.key(room), shapeKey(shape))
        player.unlock()
        player.message(if (shape == 0) "You clip the topiary back into a bush." else "You clip the topiary into the shape of ${SHAPES[shape]}.")
    }

    /** The POH planting animation (PohFurniture PLANT_ANIM) - the OSRS clipping animation is not in the 667 cache. */
    private const val TOPIARY_ANIM = 3691

    private object KcGate {
        fun kalphiteQueen(player: Player): Int = runCatching { player.getNpcKillCount(Npcs.KALPHITE_QUEEN_1160) }.getOrDefault(0)
    }
}
