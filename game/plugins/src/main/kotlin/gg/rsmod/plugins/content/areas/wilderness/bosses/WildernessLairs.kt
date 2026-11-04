package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*

/**
 * N522 bundle 4a: the OSRS Wilderness boss lairs and the Escape Caves (OsrsRegionImportTool batch silk-chasm).
 *
 * The fee - OSRS Wiki "Web Chasm" / "Escape Caves" / "Silk Chasm": "Entering the boss lairs or the entrances to the Escape Caves requires
 * players to pay a fee of 50,000 coins; this fee does not need to be brought along in the inventory, and can be deducted from the bank.
 * This fee is shared across Callisto's Den, Hunter's End, the Skeletal Tomb, Vet'ion's Rest, the Silk Chasm, the Web Chasm and the
 * Escape Caves. The fee does not need to be paid again, unless the player dies to another player without having left the Wilderness since
 * their most recent visit to a boss lair or the Escape Caves. If this happens, the fee is dropped as loot for the killer" and "Players
 * can decrease the fee by killing the six bosses; each kill in which the player deals the most damage to it reduces the fee by 10,000".
 * The prompt, its options and messages: Near Reality WildernessBossEntrance.java (the OSRS wording is in no source). The Wilderness
 * Diary requirement is left out: this game has no Wilderness Diary (OSRS: medium diary OR a Venenatis boss task).
 *
 * The ways: OSRS map placements (openrs2 2499) - the Silk Chasm entrance 47077 (3321,3794 rot 1), the Escape Caves' four surface caves
 * 47175 (rot 0) and their exits inside (47147 3358,10244 / 47148 3336,10287 and 3382,10287 / 47149 3361,10273); the arrival tiles and
 * the three lair-exit points in the Escape Caves: Near Reality WildernessBossEntrance / WildernessBossExit. "any player who exits within
 * 10 seconds of the previous player will arrive at the same point" (OSRS Wiki "Silk Chasm"). The surface tile when leaving the Escape
 * Caves is the tile south of the 3x3 surface cave (no source gives it).
 */
object WildernessLairs {
    const val BASE_FEE = 50_000
    const val DISCOUNT_PER_KILL = 10_000

    const val SILK_CHASM_ENTRANCE = 63103 // OSRS 47077
    const val ESCAPE_CAVE_SURFACE = 63104 // OSRS 47175
    const val SILK_CHASM_EXIT = 63018 // OSRS 47000
    const val WEB_CHASM_ENTRANCE = 63182 // OSRS 47078 (N522 bundle 4b, Spindel)
    const val WEB_CHASM_EXIT = 63116 // OSRS 47000 in the Web Chasm (1630,11527 plane 2)
    const val ESCAPE_EXIT_1 = 63089 // OSRS 47147
    const val ESCAPE_EXIT_2 = 63090 // OSRS 47148
    const val ESCAPE_EXIT_3 = 63091 // OSRS 47149

    const val ANIM_CRAWL = 2796

    val PAID = AttributeKey<Boolean>(persistenceKey = "wildy_lair_fee_paid")
    val PAID_AMOUNT = AttributeKey<Int>(persistenceKey = "wildy_lair_fee_amount")
    val NO_WARNING = AttributeKey<Boolean>(persistenceKey = "wildy_lair_fee_no_warning")
    val DISCOUNT_KILLS = AttributeKey<Int>(persistenceKey = "wildy_lair_fee_discount")

    /** Visited a lair or the Escape Caves and not left the Wilderness since (the PvP-death condition). */
    val SINCE_VISIT = AttributeKey<Boolean>(persistenceKey = "wildy_lair_since_visit")

    /** Entered the Escape Caves from the surface: the prayer drain (cleared when leaving them). */
    val ESCAPE_DRAIN_TICKS = AttributeKey<Int>()

    data class Placed(val id: Int, val tile: Tile, val rot: Int)

    /**
     * The Escape Caves are imported but closed: the 667 client draws their OSRS square black while the player stands on its floor
     * (plane 0) and only shows it from plane 1 (test client 2026-11-02; clearing the OSRS 0x8 tile flag did not help). Until that is
     * solved the lair exits lead to the surface beside the entrance and the four surface caves are not placed.
     */
    const val ESCAPE_CAVES_OPEN = false

    /**
     * The Web Chasm entrance at its OSRS place 3183,3744 (rot 1, OSRS map square 12602). Until 2026-11-04 the rev-667 volcano made
     * 3176-3190 x 3735-3752 unwalkable and it stood at 3193,3744; the volcano is gone (OsrsRegionImportTool 'wilderness-volcano').
     */
    val WEB_CHASM_ENTRANCE_TILE = Tile(3183, 3744)

    val ESCAPE_SURFACE =
        listOf(
            Placed(ESCAPE_CAVE_SURFACE, Tile(3282, 3774), 0),
            Placed(ESCAPE_CAVE_SURFACE, Tile(3284, 3807), 0),
            Placed(ESCAPE_CAVE_SURFACE, Tile(3320, 3830), 0),
            Placed(ESCAPE_CAVE_SURFACE, Tile(3260, 3832), 0),
        )

    val SURFACE =
        listOf(Placed(SILK_CHASM_ENTRANCE, Tile(3321, 3794), 1), Placed(WEB_CHASM_ENTRANCE, WEB_CHASM_ENTRANCE_TILE, 1)) +
            (if (ESCAPE_CAVES_OPEN) ESCAPE_SURFACE else emptyList())

    /** The Web Chasm's own exit back to the surface, in front of the entrance (donor Near Reality: 3182,3746). */
    val WEB_CHASM_SURFACE_EXIT = Tile(3182, 3746)

    /** While the Escape Caves are closed: the Silk Chasm exit puts the player on the entrance's approach side (OSRS: west of it). */
    val SILK_CHASM_SURFACE_EXIT = Tile(3319, 3795)

    /** Lair entrance -> arrival inside the lair. */
    val LAIR_ARRIVAL = mapOf(SILK_CHASM_ENTRANCE to Tile(3424, 10212, 2), WEB_CHASM_ENTRANCE to Tile(1632, 11556, 2))

    /** Surface cave (by its tile) -> arrival in the Escape Caves, and the exit inside that leads back to it. */
    data class EscapeRoute(val surface: Tile, val arrival: Tile, val exitTile: Tile)

    val ESCAPE_ROUTES =
        listOf(
            EscapeRoute(Tile(3282, 3774), Tile(3359, 10246), Tile(3358, 10244)),
            EscapeRoute(Tile(3284, 3807), Tile(3360, 10273), Tile(3361, 10273)),
            EscapeRoute(Tile(3320, 3830), Tile(3381, 10286), Tile(3382, 10287)),
            EscapeRoute(Tile(3260, 3832), Tile(3338, 10286), Tile(3336, 10287)),
        )

    /** Where a lair exit puts the player in the Escape Caves. */
    val LAIR_EXIT_POINTS = listOf(Tile(3360, 10295), Tile(3341, 10255), Tile(3376, 10255))

    private var lastExitCycle = Int.MIN_VALUE / 2
    private var lastExitPoint = LAIR_EXIT_POINTS[0]

    /** The Escape Caves, OSRS square 13472 (Near Reality EscapeCavesArea 3327-3392 x 10241-10303). */
    fun inEscapeCaves(tile: Tile): Boolean = tile.height == 0 && tile.x in 3327..3392 && tile.z in 10241..10303

    fun surfaceRoute(tile: Tile): EscapeRoute? = ESCAPE_ROUTES.firstOrNull { it.surface.x == tile.x && it.surface.z == tile.z }

    fun exitRoute(tile: Tile): EscapeRoute? = ESCAPE_ROUTES.firstOrNull { it.exitTile.x == tile.x && it.exitTile.z == tile.z }

    /** The surface tile south of the 3x3 surface cave. */
    fun surfaceLanding(route: EscapeRoute): Tile = Tile(route.surface.x + 1, route.surface.z - 1)

    fun fee(player: Player): Int = (BASE_FEE - DISCOUNT_PER_KILL * (player.attr[DISCOUNT_KILLS] ?: 0)).coerceAtLeast(0)

    fun paid(player: Player): Boolean = player.attr[PAID] == true

    /** "each kill in which the player deals the most damage to it reduces the fee by 10,000 coins". */
    fun onTopDamageKill(player: Player) {
        player.attr[DISCOUNT_KILLS] = ((player.attr[DISCOUNT_KILLS] ?: 0) + 1).coerceAtMost(BASE_FEE / DISCOUNT_PER_KILL)
    }

    fun available(player: Player): Long =
        player.inventory.getItemCount(Items.COINS_995).toLong() + player.bank.getItemCount(Items.COINS_995)

    /** Inventory first, else the bank (donor), nothing taken unless all of it can be paid. */
    fun pay(player: Player): Boolean {
        val cost = fee(player)
        if (available(player) < cost) return false
        val fromInventory = minOf(cost, player.inventory.getItemCount(Items.COINS_995))
        if (fromInventory > 0) player.inventory.remove(Item(Items.COINS_995, fromInventory), assureFullRemoval = true)
        val left = cost - fromInventory
        if (left > 0) {
            player.bank.remove(Item(Items.COINS_995, left), assureFullRemoval = true)
            player.message("<col=ef1020>You enter the cave and $cost coins are taken from your bank to pay the entry fee.</col>")
        }
        player.attr[PAID] = true
        player.attr[PAID_AMOUNT] = cost
        player.attr[DISCOUNT_KILLS] = 0
        return true
    }

    /** A lair entrance or an Escape Caves surface cave: enters when paid, otherwise the donor prompt. */
    fun enter(player: Player, enterAction: () -> Unit) {
        val go = {
            player.attr[SINCE_VISIT] = true
            enterAction()
        }
        if (paid(player)) return go()
        val cost = fee(player)
        if (available(player) < cost) {
            player.message("<col=ef1020>You need $cost coins in your inventory or bank to pay the entry fee.</col>")
            return
        }
        if (player.attr[NO_WARNING] == true) {
            if (pay(player)) go()
            return
        }
        player.queue {
            itemMessageBox("You need to pay a $cost coin fee to enter. This can be taken from your inventory or bank.", item = Items.COINS_995, amountOrZoom = 10_000)
            val choice = options("Yes.", "Yes, and don't ask again.", "No.", title = "Pay $cost coins to enter?")
            if (choice != 1 && choice != 2) return@queue
            if (!pay(player)) {
                player.message("<col=ef1020>You need $cost coins in your inventory or bank to pay the entry fee.</col>")
                return@queue
            }
            if (choice == 2) {
                player.attr[NO_WARNING] = true
                player.message("<col=ef1020>You will not be asked again about paying the entry fee.</col>")
            }
            go()
        }
    }

    /** "Check-Fee" (donor text). */
    fun checkFeeText(player: Player): String {
        val discount = (player.attr[DISCOUNT_KILLS] ?: 0) * DISCOUNT_PER_KILL
        return "The base entry fee for these caves is 50,000 coins, which " +
            (if (paid(player)) "you have paid." else "can be paid from your inventory or bank.") +
            " Killing wilderness bosses will reduce the fee by 10,000. You have " +
            (if (discount > 0) "a $discount" else "no") + " discount towards your next fee."
    }

    /**
     * The fee lost by a death: killed by a player without having left the Wilderness since the last lair / Escape Caves visit.
     * @return the coins for the player killer's loot. Clears the fee.
     */
    fun onDeath(victim: Player, playerKiller: Player?): List<Item> {
        if (playerKiller == null || !paid(victim) || victim.attr[SINCE_VISIT] != true) return emptyList()
        val amount = victim.attr[PAID_AMOUNT] ?: BASE_FEE
        victim.attr[PAID] = false
        victim.attr[SINCE_VISIT] = false
        return if (amount > 0) listOf(Item(Items.COINS_995, amount)) else emptyList()
    }

    /** Every cycle (ClientHud.tick): leaving the Wilderness ends the visit; the Escape Caves drain while entered from the surface. */
    fun tick(player: Player) {
        if (player.attr[SINCE_VISIT] == true && runCatching { player.tile.getWildernessLevel() <= 0 }.getOrDefault(false)) {
            player.attr[SINCE_VISIT] = false
        }
        val drain = player.attr[ESCAPE_DRAIN_TICKS] ?: return
        if (!inEscapeCaves(player.tile) || player.isDead()) {
            player.attr.remove(ESCAPE_DRAIN_TICKS)
            return
        }
        val ticks = drain + 1
        player.attr[ESCAPE_DRAIN_TICKS] = ticks
        // Near Reality EscapeCavesArea: the message after the first tick, then max(1, ticks / 20) prayer every 5 ticks.
        if (ticks == 2) {
            player.message("<col=ef1020>You feel a chill down your spine as the air in the cave weakens you, the air seems to be getting thicker over time</col>")
        }
        if (ticks % 5 == 0) {
            val drainBy = maxOf(1, ticks / 20)
            player.alterPrayerPoints(-drainBy)
        }
    }

    fun crawl(player: Player, dest: Tile, message: String? = null, after: () -> Unit = {}) {
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(ANIM_CRAWL)
            wait(2)
            player.moveTo(dest)
            player.animate(-1)
            message?.let { player.filterableMessage(it) }
            after()
        }
    }

    /** A lair exit: one of the three Escape Caves points; within 10 seconds (17 ticks) of the previous exit, the same point. */
    fun lairExitPoint(cycle: Int, roll: () -> Int): Tile {
        if (cycle - lastExitCycle >= 17) lastExitPoint = LAIR_EXIT_POINTS[roll()]
        lastExitCycle = cycle
        return lastExitPoint
    }
}

/**
 * The Escape Caves' grizzly bears and poison spiders: "the spiders in the cave use typeless ranged instead of melee and the bears use
 * typeless melee" (OSRS Wiki "Escape Caves"), max 8 each (infoboxes). No source names a projectile for the spiders: none is shown.
 */
object EscapeCaveMonsters : gg.rsmod.game.model.combat.CombatScript() {
    override val ids = intArrayOf(gg.rsmod.plugins.api.cfg.Npcs.GRIZZLY_BEAR_ESCAPE_CAVES, gg.rsmod.plugins.api.cfg.Npcs.POISON_SPIDER_ESCAPE_CAVES)

    const val MAX_HIT = 8

    override suspend fun handleSpecialCombat(it: gg.rsmod.game.model.queue.QueueTask) {
        val npc = it.npc
        val spider = npc.id == gg.rsmod.plugins.api.cfg.Npcs.POISON_SPIDER_ESCAPE_CAVES
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = if (spider) 7 else 1, projectile = spider)) {
                npc.animate(npc.combatDef.attackAnimation)
                val accuracy =
                    if (spider) gg.rsmod.plugins.content.combat.formula.RangedCombatFormula.getAccuracy(npc, target)
                    else gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula.getAccuracy(npc, target)
                val delay = if (spider) gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile()) else 1
                npc.dealHit(target = target, maxHit = MAX_HIT.toDouble(), landHit = accuracy >= npc.world.randomDouble(), delay = delay, hitType = gg.rsmod.plugins.api.HitType.REGULAR_HIT)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }
}
