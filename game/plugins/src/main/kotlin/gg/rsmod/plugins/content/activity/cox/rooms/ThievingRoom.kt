package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The Thieving room (CREATURE_KEEPER). Wiki (Chest (Chambers of Xeric); Corrupted scavenger; Gaining points; Strategies, Thieving): opening
 * tries every tick, 39% at Thieving 1 to 61% at 99 (+21 points with a lockpick or hair clip); a chest that isn't poisoned holds 0..Thieving/25
 * grubs (at least 1, 2 at 95+); three chests hold poison (1-3 to anyone next to it); one holds psykk bats (one per raider, 5-25), once;
 * the scavenger needs 16 grubs per raider (30 solo); each grub deposited gives 115 points, up to max(4500, total Thieving / 6 x 150);
 * hunger comes back after 60 seconds without food; asleep it lets the party through.
 * tsps rsprox (3389): opening = open-chest anim, the tick after sound 52 + 'Some cavern grubs have hatched; you take them.' + the hatched chest
 * (29745), locked again ~12 ticks later; a full stack gets a message box; depositing = 832 + sound 3637, the trough fills (29874) the tick
 * after; it eats every 3 ticks (7496); fed, it walks aside, yawns 7 ticks later (7497, 'Yawwwwwn!', sound 1734), sleeps (7603) at 14 and
 * snores (gfx 277, sound 869) every 8 ticks from 21.
 * ONZEKER (tsps): the scavenger's tiles + bed, eating min(5, grubs/6) at once, poison chests (gfx 184, relock 10), the bats' chest 29743,
 * XP 40/60/73 by grubs. LATER: the clearing batch of supplies (tsps 'not from a source' - not built), the keeper's journal.
 */
class ThievingRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private class Opened(var obj: GameObject?, val relockAt: Int?)
    private val chests = HashMap<Pair<Int, Int>, Opened>()
    private var batChest: Pair<Int, Int>? = null
    private val poison = HashSet<Pair<Int, Int>>()
    private var scavenger: Npc? = null
    private var trough = 0
    private var troughLoc: GameObject? = null
    private var eaten = 0
    private var hunger = 0
    private var needed = 30
    private var fed = false
    private var grubPoints = 0
    private var grubCap = 4500

    override fun load() {
        val all = roomLocs(29742).shuffled()
        batChest = all.firstOrNull()?.let { it.tile.x to it.tile.z }
        all.drop(1).take(3).forEach { poison += it.tile.x to it.tile.z }
        val at = SCAVENGER[variant][laid.rotation]
        scavenger = spawn(7602, tile(at.x, at.z, 2))
        scavenger?.let { s -> for (dx in 0 until s.getSize()) for (dz in 0 until s.getSize()) blockTile(s.tile.transform(dx, dz), true) }
        val n = scaling().size
        needed = if (n == 1) 30 else 16 * n
        val members = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        grubCap = maxOf(4500, members.sumOf { it.skills.getMaxLevel(Skills.THIEVING) } / 6 * 150)
    }

    override fun passRefusal(p: Player, passage: Tile): String? {
        val s = scavenger ?: return null
        if (fed || passage.getDistance(s.tile) > 7) return null
        return "You need to feed the corrupted scavenger to pass to the next room."
    }

    override fun onTick() {
        for ((k, c) in chests.toList()) {
            val at = c.relockAt ?: continue
            if (tick >= at) { swap(c.obj, 29742); chests.remove(k) }
        }
        val s = scavenger ?: return
        if (fed || tick % 3 != 0) return
        if (trough > 0) {
            val amount = maxOf(1, minOf(5, trough / 6, trough))
            trough -= amount; eaten += amount; hunger = 0
            animate(s, 7496)
            if (trough <= 0) troughLoc = swap(troughLoc, 29746)
            if (eaten >= needed) fallAsleep(s)
            return
        }
        hunger += 3
        if (hunger >= 100 && eaten > 0) eaten--
    }

    private fun swap(obj: GameObject?, osrs: Int): GameObject? {
        obj ?: return null
        val t = Tile(obj.tile); val rot = obj.rot; val type = obj.type
        world.remove(obj)
        val id = CoxIds.loc(osrs) ?: return null
        val o = gg.rsmod.game.model.entity.DynamicObject(id, type, rot, t)
        world.spawn(o)
        return o
    }

    private fun fallAsleep(s: Npc) {
        fed = true
        completed = true
        for (dx in 0 until s.getSize()) for (dz in 0 until s.getSize()) blockTile(s.tile.transform(dx, dz), false)
        val bed = SCAVENGER[variant][4]
        later(1) { s.animate(-1); s.walkTo(tile(bed.x, bed.z, 2), detectCollision = false) }
        later(7) { if (s.isSpawned()) { s.forceChat("Yawwwwwn!"); animate(s, 7497); areaSound(s.tile, 1734, radius = 5, delay = 10) } }
        later(14) { if (s.isSpawned()) { CoxIds.npc(7603)?.let { s.setTransmogId(it) }; s.animate(-1) } }
        fun snore() { if (!s.isSpawned() || closed) return; gfxOn(s, 277); areaSound(s.tile, 869, radius = 8); later(8) { snore() } }
        later(21) { snore() }
    }

    /** Op 'Deposit' on the trough: every grub held (tsps; Wiki 115 points a grub, capped). */
    fun deposit(p: Player, obj: GameObject) {
        if (fed) { p.message("That's not going to help now."); return }
        val grubs = CoxIds.item(20885) ?: return
        val held = p.inventory.getItemCount(grubs)
        if (held <= 0) { p.message("You need some cavern grubs to feed the scavenger."); return }
        val amount = minOf(28, held)
        p.inventory.remove(grubs, held)
        animatePlayer(p, 832)
        areaSound(p.tile, 3637, radius = 5)
        trough += amount
        if (troughLoc == null) troughLoc = obj
        later(1) { if (trough > 0 && troughLoc?.id != CoxIds.loc(29874)) troughLoc = swap(troughLoc, 29874) }
        val points = maxOf(0, minOf(grubCap - grubPoints, amount * 115))
        grubPoints += points
        raid.addPoints(p.username, points)
    }

    /** Op on a chest: one try a tick until it opens (Wiki chance), then grubs / poison / bats. */
    fun search(p: Player, obj: GameObject) {
        val key = obj.tile.x to obj.tile.z
        if (chests.containsKey(key)) { p.message("The chest is already opened."); return }
        val grubs = CoxIds.item(20885) ?: return
        if (p.inventory.freeSlotCount <= 0 && !p.inventory.contains(grubs)) { p.queue { messageBox("You wouldn't have space to take anything from the chest.") }; return }
        p.queue {
            while (!chests.containsKey(key) && !closed) {
                animatePlayer(p, 536); play(p, 2402)
                val lockpick = p.inventory.contains(1523) || p.inventory.contains(11682)
                val level = p.skills.getCurrentLevel(Skills.THIEVING).coerceIn(1, 99)
                wait(1)
                if (world.randomDouble() < 0.39 + (level - 1) * 0.22 / 98 + if (lockpick) 0.21 else 0.0) { open(p, obj, key); break }
            }
        }
    }

    private fun open(p: Player, obj: GameObject, key: Pair<Int, Int>) {
        if (key == batChest) {
            batChest = null
            chests[key] = Opened(swap(obj, 29743), null)
            play(p, 2655)
            p.message("The chest contained some nicely cooked bats.")
            CoxIds.item(20883)?.let { p.inventory.add(it, maxOf(5, minOf(25, scaling().size))) }
            return
        }
        areaSound(obj.tile, 52, radius = 5)
        if (key in poison) {
            raiders().filter { it.tile.getDistance(p.tile) <= 1 }.forEach { damage(null, it, world.random(1..3)) }
            play(p, 2138); gfxAt(184, Tile(obj.tile))
            chests[key] = Opened(swap(obj, 29743), tick + 10)
            return
        }
        val grubs = CoxIds.item(20885) ?: return
        val held = p.inventory.getItemCount(grubs)
        if (held >= 28) { p.queue { messageBox("You don't have space to take any more grubs from the chest.") }; return }
        val level = p.skills.getCurrentLevel(Skills.THIEVING)
        val found = maxOf(if (level >= 95) 2 else 1, world.random(0..level / 25))
        val amount = minOf(found, 28 - held)
        p.message("Some cavern grubs have hatched; you take them.")
        p.inventory.add(grubs, amount)
        p.addXp(Skills.THIEVING, listOf(40.0, 60.0, 73.0, 73.0)[(amount - 1).coerceIn(0, 3)])
        chests[key] = Opened(swap(obj, 29745), tick + 12)
    }

    companion object {
        /** By version: where the scavenger stands by the room's turn (0-3), then where it sleeps (tsps). */
        val SCAVENGER = listOf(
            listOf(Tile(3271, 5390), Tile(3272, 5390), Tile(3272, 5391), Tile(3271, 5391), Tile(3271, 5394)),
            listOf(Tile(3318, 5397), Tile(3319, 5397), Tile(3319, 5398), Tile(3318, 5398), Tile(3321, 5398)),
            listOf(Tile(3350, 5397), Tile(3351, 5397), Tile(3351, 5398), Tile(3350, 5398), Tile(3351, 5394)),
        )
    }
}
