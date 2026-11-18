package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxRaids
import java.util.concurrent.ConcurrentHashMap

/**
 * The raid's gathering and food (OSRS Wiki 'Chambers of Xeric/Food', 'Chambers of Xeric' Resources/Herblore, Kindling, Rotten sapling, Gourd
 * tree, Herb patch; code help + ONZEKER timings: tsps Resources.ChambersOfXeric.js):
 *  - fishing at the Bubbles ('Fish'): rod + cave worms, a roll every 4 ticks; cooking raw fish/bats on a fire lit from kindling;
 *  - saplings ('Chop', any axe): kindling every 5 ticks of chopping (rsprox: without fail, sound 2070), at most 28; the sapling falls 1 in
 *    max(3, 2 x raiders) (Mod Ash), five times rarer in the Ice Demon's room, and grows back 54-64 ticks later (stump 29764);
 *  - the gourd tree ('Pick' one / 'Pick-lots' a full inventory of empty gourds), the geyser ('Use', or a gourd on it) fills them;
 *  - weeds ('Rake', a rake): 1-3 of a seed type, five times, never the same type twice; herb patches: a seed with a seed dibber, a stage every
 *    12 ticks, grown at 4, three lives when picking (65% + (level - req)/250 to keep one, at most 95%); XP planting/picking golpar 4/10,
 *    buchu 6/15, noxifer 12/30; Farming 27 / 39 / 55;
 *  - old tools ('Take Rake' / 'Take Spade' / 'Take Seed Dibber'), the bronze axe / hammer / tinderbox / iron pickaxe lying about ('Take'),
 *    the energy well ('Touch') restores run energy.
 */
object CoxSupplies {
    data class Catch(val npc: Int, val raw: Int, val level: Int, val xp: Double, val cookXp: Double, val heal: Int)

    /** Fish: raw ids 20855.. (cooked = raw + 1); levels 1/15/30/45/60/75/90; XP 20 + 3 a tier, cooked 10 + 3 a tier; heals 5 + 3 a tier. */
    val FISH = (0..6).map { t -> Catch(-1, 20855 + 2 * t, listOf(1, 15, 30, 45, 60, 75, 90)[t], 20.0 + 3 * t, 10.0 + 3 * t, 5 + 3 * t) }
    /** Bats: npcs 7587-7593, raw ids 20870.. (cooked +1); XP 5 + 4 a tier. */
    val BATS = (0..6).map { t -> Catch(7587 + t, 20870 + 2 * t, listOf(1, 15, 30, 45, 60, 75, 90)[t], 5.0 + 4 * t, 10.0 + 3 * t, 5 + 3 * t) }
    /** Cooked food OSRS id -> heal. */
    val FOOD: Map<Int, Int> = (FISH + BATS).associate { it.raw + 1 to it.heal }
    val RAW: Map<Int, Catch> = (FISH + BATS).associateBy { it.raw }

    /** Per raid: cook -> cooked food (OSRS id) -> pieces not yet eaten; and the pieces eaten that granted points. */
    private val cooked = java.util.concurrent.ConcurrentHashMap<Long, MutableMap<String, MutableMap<Int, Int>>>()
    private val eatenForPoints = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    fun forget(raidId: Long) { cooked.remove(raidId); eatenForPoints.remove(raidId) }

    private fun tierOf(food: Int): Int = (FISH + BATS).firstOrNull { it.raw + 1 == food }?.let { (FISH + BATS).indexOf(it) % 7 + 1 } ?: 1

    /** Wiki 'Cooking Food' points to the cook; the eater's own food first. ONZEKER: the curve between the soft (20 x party) and hard
     *  (40 x party) cap is 'gradually diminishing' without a formula - linear here. Which cook a piece came from is not tracked per item:
     *  the eater's own cooked pieces count first, else the cook with the most left. */
    private fun foodPoints(p: Player, food: Int) {
        val r = raid(p) ?: return
        val ledger = cooked[r.id] ?: return
        val me = p.username.lowercase()
        val cook = if ((ledger[me]?.get(food) ?: 0) > 0) me else ledger.entries.filter { (it.value[food] ?: 0) > 0 }.maxByOrNull { it.value[food]!! }?.key ?: return
        ledger[cook]!!.merge(food, -1, Int::plus)
        val party = maxOf(1, r.points.size)
        val n = eatenForPoints.merge(r.id, 1, Int::plus)!!
        if (n > 40 * party) return
        var points = 4.0 + 8 * tierOf(food)
        if (cook != me) points *= 1.5
        if (n > 20 * party) points *= (40.0 * party - n) / (20.0 * party)
        if (points >= 1) r.addPoints(cook, points.toInt())
    }

    const val KINDLING = 20799
    const val EMPTY_GOURD = 20800
    const val WATER_GOURD = 20801
    const val CAVE_WORMS = 20853
    const val BURNT_FISH = 20854
    const val BURNT_BAT = 20869

    private fun local(osrs: Int) = CoxIds.item(osrs)
    private fun raid(p: Player) = CoxRaids.raidAt(p.tile)
    private fun hasAxe(p: Player) = gg.rsmod.plugins.content.skills.woodcutting.AxeType.values().firstOrNull { p.inventory.contains(it.item) || p.equipment.contains(it.item) }

    // ------------------------------------------------------------------ fishing

    fun fish(p: Player, spot: GameObject) {
        val r = raid(p) ?: return
        val room = CoxRooms.rooms(r).filterIsInstance<ResourcesRoom>().firstOrNull { it.contains(p.tile) }
        if (!p.inventory.contains(307) && !p.inventory.contains(22846)) { p.message("You need a fishing rod to fish here."); return }
        val worms = local(CAVE_WORMS) ?: return
        if (!p.inventory.contains(worms)) { p.message("You need some suitable bait to catch these fish."); return }
        if (p.inventory.freeSlotCount <= 0) { p.message("Your inventory is too full to do that."); return }
        val type = FISH.last { it.level <= p.skills.getCurrentLevel(Skills.FISHING) }
        p.animate(622)
        if (room?.isSnakeSpot(spot.tile) == true) { room.riseSnake(); p.message("This seems like a very bad moment to fish here."); return }
        p.message("You cast out your line...")
        p.queue {
            while (true) {
                wait(4)
                if (room?.isSnakeSpot(spot.tile) == true) { room.riseSnake(); p.message("This seems like a very bad moment to fish here."); break }
                if (p.inventory.freeSlotCount <= 0) { p.message("Your inventory is too full to do that."); break }
                if (!p.inventory.contains(worms)) { p.message("You need some suitable bait to catch these fish."); break }
                p.animate(622)
                val chance = minOf(Math.round((p.skills.getCurrentLevel(Skills.FISHING) - type.level) * 0.6).toInt() + 30, 70)
                if (chance > p.world.random(99)) {
                    p.inventory.remove(worms, 1)
                    local(type.raw)?.let { p.inventory.add(it) }
                    p.addXp(Skills.FISHING, type.xp)
                }
            }
            p.animate(-1)
        }
    }

    // ------------------------------------------------------------------ fires, cooking, eating

    /** Tinderbox on kindling: a fire on the tile (Wiki Kindling: lights a fire for cooking, 48 Firemaking XP). */
    fun lightFire(p: Player) {
        val kindling = local(KINDLING) ?: return
        if (raid(p) == null || !p.inventory.contains(kindling)) return
        if (p.world.getObject(p.tile, gg.rsmod.game.model.collision.ObjectType.INTERACTABLE) != null) { p.message("You can't light a fire here."); return }
        p.inventory.remove(kindling, 1)
        p.animate(733)
        val t = Tile(p.tile)
        p.queue {
            wait(2)
            val fire = DynamicObject(FIRE_LOC, 10, 0, t)
            p.world.spawn(fire)
            // As every firemaking fire: the lighter steps off it (west, else east).
            val west = t.transform(-1, 0)
            p.walkTo(if (p.world.collision.canTraverse(t, gg.rsmod.game.model.Direction.WEST, projectile = false, water = false)) west else t.transform(1, 0), detectCollision = false)
            p.addXp(Skills.FIREMAKING, 48.0)
            p.world.queue { wait(100); if (fire.isSpawned(p.world)) p.world.remove(fire) }
        }
    }

    /** The 667 firemaking fire (Objs.FIRE_2732), so every raider can cook on it. */
    const val FIRE_LOC = 2732

    fun cook(p: Player, rawLocal: Int) {
        val osrs = RAW.keys.firstOrNull { local(it) == rawLocal } ?: return
        val c = RAW.getValue(osrs)
        if (p.skills.getCurrentLevel(Skills.COOKING) < c.level) { p.message("You need a Cooking level of at least ${c.level} to cook this."); return }
        p.queue {
            while (p.inventory.contains(rawLocal)) {
                p.animate(897)
                wait(4)
                if (!p.inventory.contains(rawLocal)) break
                p.inventory.remove(rawLocal, 1)
                // ONZEKER: no burn chance on the Wiki for raid food - always cooked.
                local(osrs + 1)?.let { p.inventory.add(it) }
                raid(p)?.let { r -> cooked.getOrPut(r.id) { java.util.concurrent.ConcurrentHashMap() }.getOrPut(p.username.lowercase()) { mutableMapOf() }.merge(osrs + 1, 1, Int::plus) }
                p.addXp(Skills.COOKING, c.cookXp)
            }
        }
    }

    fun eat(p: Player, osrs: Int, slot: Int) {
        val heal = FOOD[osrs] ?: return
        val id = local(osrs) ?: return
        if (p.inventory[slot]?.id != id || p.timers.has(FOOD_DELAY)) return
        p.inventory.remove(id, 1, beginSlot = slot)
        if (p.getCurrentLifepoints() < p.getMaximumLifepoints()) foodPoints(p, osrs)
        p.animate(Anims.EAT_FOOD)
        p.playSound(2393)
        p.heal(heal)
        p.timers[FOOD_DELAY] = 3
        p.message("You eat the ${p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name.replace(Regex(" \\(\\d\\)$"), "").lowercase()}.")
    }

    // ------------------------------------------------------------------ saplings, gourds, geyser

    fun chop(p: Player, sapling: GameObject) {
        val r = raid(p) ?: return
        val axe = hasAxe(p) ?: run { p.message("You do not have an axe which you have the woodcutting level to use."); return }
        val kindling = local(KINDLING) ?: return
        p.message("You swing your axe...")
        val tile = Tile(sapling.tile); val rot = sapling.rot; val id = sapling.id
        val iceRoom = CoxRooms.rooms(r).any { it is IceDemonRoom && it.contains(p.tile) }
        p.queue {
            var ticks = 0
            while (p.world.getObject(tile, gg.rsmod.game.model.collision.ObjectType.INTERACTABLE)?.id == id) {
                if (ticks % 4 == 0) p.animate(axe.animation)
                wait(1)
                if (++ticks % 5 != 0) continue
                if (p.inventory.getItemCount(kindling) >= 28 || p.inventory.freeSlotCount <= 0 && !p.inventory.contains(kindling)) break
                p.inventory.add(kindling, 1)
                p.playSound(2070)
                // ONZEKER: Woodcutting XP per kindling (Mod Bruno's table) not on hand - none given.
                val n = maxOf(3, 2 * maxOf(1, r.points.size)) * if (iceRoom) 5 else 1
                if (p.world.random(n - 1) == 0) {
                    p.world.getObject(tile, gg.rsmod.game.model.collision.ObjectType.INTERACTABLE)?.let { p.world.remove(it) }
                    val stump = CoxIds.loc(29764)?.let { DynamicObject(it, 10, rot, tile) }
                    stump?.let { p.world.spawn(it) }
                    p.playSound(2734)
                    p.world.queue { wait(p.world.random(54..64)); stump?.let { s -> if (s.isSpawned(p.world)) p.world.remove(s) }; p.world.spawn(DynamicObject(id, 10, rot, tile)) }
                    break
                }
            }
            p.animate(-1)
        }
    }

    fun pickGourds(p: Player, lots: Boolean) {
        val gourd = local(EMPTY_GOURD) ?: return
        if (raid(p) == null) return
        if (p.inventory.freeSlotCount <= 0) { p.message("You don't have enough inventory space."); return }
        p.animate(2280)
        val n = if (lots) p.inventory.freeSlotCount else 1
        p.inventory.add(gourd, n)
    }

    fun fillGourds(p: Player) {
        val empty = local(EMPTY_GOURD) ?: return
        val water = local(WATER_GOURD) ?: return
        val n = p.inventory.getItemCount(empty)
        if (n <= 0) return
        p.animate(832)
        p.inventory.remove(empty, n)
        p.inventory.add(water, n)
    }

    // ------------------------------------------------------------------ weeds and tools

    private val raked = ConcurrentHashMap<String, MutableList<Int>>()

    fun rake(p: Player) {
        val r = raid(p) ?: return
        if (!p.inventory.contains(5341)) return
        val key = "${r.id}:${p.username.lowercase()}"
        val done = raked.getOrPut(key) { mutableListOf() }
        p.queue {
            while (done.size < 5) {
                p.animate(2273)
                wait(4)
                val seeds = listOf(20906, 20909, 20903)
                val last = done.lastOrNull()
                val seed = seeds.filter { it != last }.random()
                done += seed
                local(seed)?.let { p.inventory.add(it, p.world.random(1..3)) }
            }
            p.animate(-1)
        }
    }

    fun takeTool(p: Player, item: Int) {
        if (p.inventory.freeSlotCount <= 0) { p.message("You don't have enough inventory space."); return }
        p.animate(832)
        p.inventory.add(item)
    }

    fun touchWell(p: Player) {
        p.animate(827)
        p.runEnergy = 100.0
    }

    // ------------------------------------------------------------------ herb patches

    data class Herb(val seed: Int, val grimy: Int, val patch: Int, val level: Int, val plantXp: Double, val pickXp: Double, val name: String)
    val HERBS = listOf(
        Herb(20906, 20904, 29998, 27, 4.0, 10.0, "golpar"), Herb(20909, 20907, 29999, 39, 6.0, 15.0, "buchu"),
        Herb(20903, 20901, 29997, 55, 12.0, 30.0, "noxifer"),
    )
    private class Patch(var obj: GameObject, val herb: Herb, var stage: Int, var ticks: Int, var lives: Int)
    private val patches = ConcurrentHashMap<String, Patch>()
    private fun key(o: GameObject) = "${o.tile.x},${o.tile.z},${o.tile.height}"

    private fun swap(w: gg.rsmod.game.model.World, o: GameObject, osrs: Int): GameObject? {
        val id = CoxIds.loc(osrs) ?: return null
        val t = Tile(o.tile); val rot = o.rot; val type = o.type
        w.remove(o)
        return DynamicObject(id, type, rot, t).also { w.spawn(it) }
    }

    fun plant(p: Player, patch: GameObject, seedLocal: Int) {
        if (raid(p) == null) return
        val herb = HERBS.firstOrNull { local(it.seed) == seedLocal } ?: return
        if (patches.containsKey(key(patch))) { p.message("There are already herbs growing there!"); return }
        if (p.skills.getCurrentLevel(Skills.FARMING) < herb.level) { p.message("You need a Farming level of at least ${herb.level} to plant ${herb.name} seeds."); return }
        if (!p.inventory.contains(5343)) { p.message("You need a seed dibber to plant seeds."); return }
        if (CoxIds.loc(herb.patch) == null) return
        p.animate(2291); p.playSound(2432)
        p.queue {
            wait(1)
            if (!p.inventory.contains(seedLocal) || patches.containsKey(key(patch))) return@queue
            p.inventory.remove(seedLocal, 1)
            p.message("You plant a ${herb.name} in the herb patch.")
            p.addXp(Skills.FARMING, herb.plantXp)
            val o = swap(p.world, patch, herb.patch) ?: return@queue
            patches[key(o)] = Patch(o, herb, 0, 0, 3)
        }
    }

    /** Every tick: a stage every 12 ticks until grown (stage 4). */
    fun growTick(w: gg.rsmod.game.model.World) {
        for ((k, pt) in patches.entries.toList()) {
            if (!pt.obj.isSpawned(w)) { patches.remove(k); continue }
            if (pt.stage >= 4 || ++pt.ticks < 12) continue
            pt.ticks = 0; pt.stage++
            swap(w, pt.obj, pt.herb.patch + pt.stage * 3)?.let { pt.obj = it }
        }
    }

    fun pick(p: Player, o: GameObject) {
        val pt = patches[key(o)] ?: return
        val grimy = local(pt.herb.grimy) ?: return
        p.queue {
            while (pt.lives > 0 && patches[key(pt.obj)] === pt) {
                if (p.inventory.freeSlotCount <= 0) { p.message("You don't have enough inventory space."); break }
                p.animate(2282)
                wait(2)
                p.inventory.add(grimy, 1)
                p.addXp(Skills.FARMING, pt.herb.pickXp)
                val keep = minOf(95.0, 65 + (p.skills.getCurrentLevel(Skills.FARMING) - pt.herb.level) / 2.5)
                if (p.world.random(99) >= keep) pt.lives--
            }
            if (pt.lives <= 0) clearPatch(p.world, pt)
            p.animate(-1)
        }
    }

    fun clear(p: Player, o: GameObject) {
        val pt = patches[key(o)] ?: return
        if (!p.inventory.contains(952)) return
        p.animate(830)
        clearPatch(p.world, pt)
    }

    private fun clearPatch(w: gg.rsmod.game.model.World, pt: Patch) {
        patches.remove(key(pt.obj))
        swap(w, pt.obj, 29765)
    }
}
