package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The Crab puzzle (CRAB_PUZZLE). Wiki (Jewelled Crab; Strategies, Crab puzzle; Gaining points): an energy focus travels the room, turning at
 * every jewelled crab and taking its colour; brought to the small crystal of its colour it lights it; four lit shatter the dark crystal.
 * The crabs can't be hurt; striking one turns it red (melee), blue (magic) or green (ranged); 'Smash' with a hammer/blunt weapon turns it red
 * and stuns it (50-60 ticks solo, 30-40 for 2-3, 20-30 for 4-5, 10-20 for 6+); points per crystal to whoever's crab sent the orb there.
 * tsps rsprox (3389): the orb fizzles at the end of its path (gfx 119, sound 219), the next comes as it goes a tick later; meeting a crab it
 * takes its colour (sound 3274); at the wrong crystal it bursts as a bolt, on a player as a wave; lighting turns the crystal white (29762,
 * sound 1476), 400 points, the dark crystal brightens a step 1-3 ticks later; four lit: crabs go at 4 ticks, the dark crystal shatters at 5
 * (gfx 1326); struck crabs keep their colour ~12 ticks; smash: player 1755, crab gfx 245, sounds 556/2727; crabs bite with 2368.
 * ONZEKER (tsps): tiles, orb every 5 ticks turning clockwise at each crab (two at once fizzle it), a player in its way takes 25% of their
 * hitpoints (and anyone beside), which crystal needs which colour, bites up to 10.
 */
class CrabsRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val crabs = mutableListOf<Npc>()
    private val colourUntil = HashMap<Npc, Int>()
    private val colourOf = HashMap<Npc, Int>()
    private val crystalTiles = mutableListOf<Tile>()
    private val lit = BooleanArray(4)
    private var dark: GameObject? = null
    private var focus: Npc? = null
    private var focusColour = WHITE_FOCUS
    private var direction = 0
    private var sender: Player? = null
    private var nextFocusAt = 0
    private val lastHitBy = HashMap<Npc, Player>()
    private val nextBite = HashMap<Npc, Int>()

    override fun load() {
        CRABS[variant].forEach { s -> spawn(WHITE_CRAB, tile(s.x, s.z))?.let { crabs += it; colourOf[it] = WHITE_CRAB } }
        SMALL[variant].forEach { crystalTiles += tile(it.x, it.z) }
        val d = DARK[variant]
        dark = placeLoc(29753, tile(d.x, d.z, 2))
        nextFocusAt = tick + 5
    }

    override fun onTick() {
        if (completed) return
        for (c in crabs) {
            val until = colourUntil[c] ?: continue
            if (tick >= until) { colourUntil.remove(c); colourOf[c] = WHITE_CRAB; c.setTransmogId(-1) }
        }
        for (c in crabs) {
            if (colourUntil.containsKey(c)) continue
            val near = raiders().firstOrNull { it.tile.getDistance(c.tile) <= 3 } ?: continue
            if (!meleeReach(c, near.tile)) { c.walkTo(near.tile); continue }
            if (tick >= (nextBite[c] ?: 0)) { nextBite[c] = tick + 4; animate(c, 2368); attack(c, near, 10, CombatClass.MELEE) }
        }
        if (focus == null && tick >= nextFocusAt) sendFocus() else if (focus != null) moveFocus()
    }

    private fun sendFocus() {
        val s = FOCUS[variant]
        focus = spawn(WHITE_FOCUS, tile(s.x, s.z))
        focusColour = WHITE_FOCUS
        direction = (variant + laid.rotation) and 3
        sender = null
    }

    private fun endFocus(how: String) {
        val f = focus ?: return
        focus = null
        nextFocusAt = tick + 1
        val at = Tile(f.tile)
        val burst = BURST[focusColour] ?: BURST.getValue(WHITE_FOCUS)
        when (how) {
            "fizzle" -> { gfxOn(f, burst.first, height = 80); areaSound(at, 219, radius = 6) }
            "bolt" -> { gfxAt(burst.first, at, height = 80, delay = 10); areaSound(at, 1294, radius = 6, delay = 10) }
            "wave" -> { gfxAt(burst.second, at, height = 80, delay = 10); areaSound(at, 223, radius = 6, delay = 10) }
        }
        f.stopMovement()
        later(1) { remove(f) }
    }

    private fun crabAt(x: Int, z: Int) = crabs.firstOrNull { alive(it) && it.tile.x == x && it.tile.z == z }

    private fun moveFocus() {
        val f = focus ?: return
        val here = f.tile
        val crystal = crystalTiles.indexOfFirst { it.x == here.x && it.z == here.z }
        if (crystal >= 0) { reachCrystal(crystal); return }
        raiders().firstOrNull { it.tile.x == here.x && it.tile.z == here.z }?.let { p ->
            val dmg = (minOf(99, p.getCurrentLifepoints()) + 3) / 4
            raiders().filter { it.tile.getDistance(p.tile) <= 1 }.forEach { damage(null, it, dmg) }
            endFocus("wave"); return
        }
        var turns = 0
        while (true) {
            val (dx, dz) = DIRECTIONS[direction]
            val x = here.x + dx; val z = here.z + dz
            val crab = crabAt(x, z)
            if (crab != null) {
                val colour = colourOf[crab] ?: WHITE_CRAB
                if (colour != WHITE_CRAB && focusColour != WHITE_FOCUS + (colour - WHITE_CRAB)) {
                    focusColour = WHITE_FOCUS + (colour - WHITE_CRAB)
                    CoxIds.npc(focusColour)?.let { f.setTransmogId(it) }
                    areaSound(here, 3274, radius = 6)
                }
                lastHitBy[crab]?.let { sender = it }
                if (++turns >= 2) { endFocus("fizzle"); return }
                direction = (direction + 1) and 3
                continue
            }
            val next = Tile(x, z, here.height)
            val atCrystal = crystalTiles.any { it.x == x && it.z == z }
            if (!atCrystal && !world.collision.canTraverse(here, gg.rsmod.game.model.Direction.between(here, next), projectile = false, water = false)) {
                endFocus("fizzle"); return
            }
            f.walkTo(next, detectCollision = false)
            return
        }
    }

    private fun reachCrystal(index: Int) {
        if (lit[index] || focusColour != NEEDS[index]) { endFocus("bolt"); return }
        lit[index] = true
        val t = crystalTiles[index]
        world.getObject(t, gg.rsmod.game.model.collision.ObjectType.INTERACTABLE)?.let { small ->
            val rot = small.rot; world.remove(small)
            CoxIds.loc(29762)?.let { world.spawn(gg.rsmod.game.model.entity.DynamicObject(it, 10, rot, Tile(t))) }
        }
        gfxAt(119, t, height = 80, delay = 10)
        areaSound(t, 1476, radius = 6, delay = 10)
        sender?.takeIf { it.isOnline }?.let { raid.addPoints(it.username, 400) }
        endFocus("used")
        val litCount = lit.count { it }
        // tsps rsprox: the dark crystal brightens a step (RAIDS_LASERCRABS_BIGCRYSTAL_2..5 = 29754-29757) 1-3 ticks later.
        later(if (litCount >= 4) 1 else 3) {
            val d = dark ?: return@later
            val next = CoxIds.loc(29753 + litCount) ?: return@later
            val t = Tile(d.tile); val rot = d.rot
            removeLoc(d)
            dark = gg.rsmod.game.model.entity.DynamicObject(next, 10, rot, t).also { world.spawn(it) }
        }
        if (litCount < 4) return
        completed = true
        later(4) { crabs.toList().forEach { remove(it) } }
        later(5) {
            val d = dark ?: return@later
            listOf(Triple(0, 0, 0), Triple(-1, 1, 5), Triple(-1, 0, 10)).forEach { (dx, dz, delay) -> gfxAt(1326, d.tile.transform(dx, dz), delay = delay) }
            removeLoc(d); dark = null
        }
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, listOfNotNull(dark?.tile))

    /** The crabs take no damage; a hit colours them by the attack's style (Wiki). */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !in crabs) return
        hit.hitmarks.forEach { it.damage = 0 }
        val colour = when (hit.sourceCombatClass) { CombatClass.MAGIC -> BLUE_CRAB; CombatClass.RANGED -> GREEN_CRAB; else -> RED_CRAB }
        colour(npc, colour, 12)
        (hit.source as? Player)?.let { p -> lastHitBy[npc] = p; gg.rsmod.plugins.content.combat.Combat.reset(p) }
    }

    private fun colour(crab: Npc, osrs: Int, ticks: Int) {
        colourOf[crab] = osrs
        CoxIds.npc(osrs)?.let { crab.setTransmogId(it) }
        colourUntil[crab] = tick + ticks
    }

    /** 'Smash' (Wiki): needs a hammer or blunt weapon; red and stunned by party size. */
    fun smash(p: Player, crab: Npc) {
        if (crab !in crabs) return
        val weapon = p.getEquipment(EquipmentType.WEAPON)?.getDef(world.definitions)?.name?.lowercase() ?: ""
        val blunt = Regex("hammer|maul|bludgeon|frying pan|meat tenderiser").containsMatchIn(weapon)
        if (!blunt && !p.inventory.contains(2347)) { p.message("You don't have a suitable weapon equipped for hammering the crab."); return }
        animatePlayer(p, 1755)
        areaSound(crab.tile, 556, radius = 5, delay = 20)
        areaSound(crab.tile, 2727, radius = 5, delay = 30)
        val n = scaling().size
        val ticks = when { n <= 1 -> world.random(50..60); n <= 3 -> world.random(30..40); n <= 5 -> world.random(20..30); else -> world.random(10..20) }
        colour(crab, RED_CRAB, ticks)
        lastHitBy[crab] = p
        gfxOn(crab, 245, height = 100, delay = 30)
        crab.stopMovement()
    }

    companion object {
        const val WHITE_CRAB = 7576
        const val RED_CRAB = 7577
        const val GREEN_CRAB = 7578
        const val BLUE_CRAB = 7579
        const val WHITE_FOCUS = 7580
        /** The orb each small crystal (by index) needs (tsps): white, blue, green, red. */
        val NEEDS = listOf(7580, 7583, 7582, 7581)
        /** East, south, west, north: clockwise. */
        val DIRECTIONS = listOf(1 to 0, 0 to -1, -1 to 0, 0 to 1)
        val BURST = mapOf(7580 to (119 to 160), 7581 to (128 to 157), 7582 to (119 to 166), 7583 to (122 to 163))
        val FOCUS = listOf(Tile(3277, 5353), Tile(3318, 5363), Tile(3346, 5358))
        val CRABS = listOf(
            listOf(Tile(3286, 5354), Tile(3280, 5358), Tile(3281, 5365), Tile(3287, 5361)),
            listOf(Tile(3306, 5358), Tile(3311, 5353), Tile(3313, 5361), Tile(3318, 5356)),
            listOf(Tile(3337, 5356), Tile(3335, 5362), Tile(3341, 5366), Tile(3344, 5361)),
        )
        val DARK = listOf(Tile(3272, 5365), Tile(3308, 5366), Tile(3349, 5366))
        val SMALL = listOf(
            listOf(Tile(3283, 5356), Tile(3279, 5366), Tile(3282, 5362), Tile(3287, 5359)),
            listOf(Tile(3311, 5356), Tile(3320, 5360), Tile(3306, 5361), Tile(3305, 5356)),
            listOf(Tile(3341, 5355), Tile(3337, 5354), Tile(3340, 5363), Tile(3340, 5367)),
        )
    }
}
