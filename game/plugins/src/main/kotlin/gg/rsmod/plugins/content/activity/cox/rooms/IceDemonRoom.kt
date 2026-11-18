package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/**
 * The Ice Demon (puzzle room). Wiki (Ice demon; Brazier; Chambers of Xeric, Gaining points): light the braziers with a tinderbox and kindling
 * to thaw it (lighting 8% at Firemaking 1 to 78% at 99, Mod Ash); icefiends steal a kindling 1 in 6 every 3-4 ticks; 32 points a lighting +
 * 36 a kindling (up to ~6,050); it takes a third of all damage, fire spells 250%, demonbane weapons full; it attacks with the style its
 * target prays against, or either; Ice Burst freezes.
 * tsps rsprox (820, 843, 3282, 3285, 3389): lighting retried every tick (3687, sound 2066), all kindling held goes in; the storm thaws
 * 0.15% + 0.02% per kindling a tick per lit brazier, burns a kindling every 8 ticks, regrows 0.17% a tick with nothing lit; thawed: smoke
 * (gfx 188), sound 1930, icefiends die (1580, sound 532) a tick later, the demon walks out and is hostile 8 ticks after the thaw; it attacks
 * every 3 ticks (anim 69): boulder projectile 1324 (gfx 1325, sound 1021), Ice Burst projectile 366 over the 3x3 (gfx 363, sound 170),
 * 'You have been frozen!' 2 ticks after a hit, 9 ticks; dying anim 67, the storm goes 5 ticks later.
 * ONZEKER (tsps): attack bases 30/25, prayer letting 1/2 (boulder) / 1/3 (burst) through; the storm's headbar is not shown.
 */
class IceDemonRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private enum class Stage { FROZEN, THAWING, FIGHTING, DEAD }
    private var stage = Stage.FROZEN
    private var demon: Npc? = null
    private var storm: GameObject? = null
    private var stormLeft = 10000
    private class Brazier(var obj: GameObject?, var kindling: Int, var burn: Int = 8)
    private val braziers = HashMap<Int, Brazier>()
    private val icefiends = HashMap<Int, Pair<Npc, IntArray>>()
    private var kindlingPoints = 0
    private var attackWait = 0

    override fun load() {
        val d = DEMON[variant]
        storm = placeLoc(29876, tile(d[0].x, d[0].z, 2), rotation = 0)
        demon = spawn(7584, tile(d[0].x, d[0].z, 2), speed = 3, deathSeq = 67)?.also { it.faceTile(tile(d[1].x, d[1].z, 2)) }
        (0..3).shuffled().take(minOf(scaling().size, 4)).forEach { i ->
            val s = FIENDS[variant][i]
            spawn(7586, tile(s.x, s.z))?.let { f -> val b = BRAZIERS[variant][i]; f.faceTile(tile(b.x, b.z)); icefiends[i] = f to intArrayOf(world.random(3..4)) }
        }
    }

    fun brazierIndex(t: Tile): Int = BRAZIERS[variant].indexOfFirst { val bt = tile(it.x, it.z); bt.x == t.x && bt.z == t.z }

    override fun onTick() {
        when (stage) {
            Stage.FROZEN -> thaw()
            Stage.FIGHTING -> fight()
            else -> {}
        }
    }

    private fun thaw() {
        if (braziers.isEmpty()) stormLeft = minOf(10000, stormLeft + 17)
        for ((i, b) in braziers.toList()) {
            stormLeft -= 15 + 2 * b.kindling
            icefiends[i]?.let { (f, wait) ->
                if (--wait[0] <= 0) {
                    wait[0] = world.random(3..4)
                    animate(f, 1582); b.obj?.let { areaSound(it.tile, 531, radius = 4) }
                    if (world.random(1..6) == 1) b.kindling = maxOf(0, b.kindling - 1)
                }
            }
            if (--b.burn <= 0) { b.burn = 8; b.kindling-- }
            if (b.kindling <= 0) extinguish(i)
        }
        if (stormLeft <= 0) defreeze()
    }

    /** Op 'Light' on an unlit brazier: retried every tick (Wiki chance by Firemaking), all kindling held (at most 28) goes in. */
    fun light(p: Player, obj: GameObject) {
        val index = brazierIndex(obj.tile)
        if (index < 0 || stage != Stage.FROZEN || braziers.containsKey(index)) return
        val kindling = CoxIds.item(20799) ?: return
        if (!p.inventory.contains(590) || !p.inventory.contains(kindling)) {
            p.queue { messageBox("You need a tinderbox and some suitable kindling to light the brazier.") }
            return
        }
        p.queue {
            while (!braziers.containsKey(index) && stage == Stage.FROZEN && !closed) {
                animatePlayer(p, 3687); areaSound(obj.tile, 2066, radius = 6)
                wait(1)
                val level = p.skills.getCurrentLevel(Skills.FIREMAKING).coerceIn(1, 99)
                if (world.randomDouble() < 0.08 + (level - 1) * 0.7 / 98) {
                    val have = minOf(28, p.inventory.getItemCount(kindling))
                    if (have <= 0) break
                    p.inventory.remove(kindling, have)
                    val tile = Tile(obj.tile); val rot = obj.rot
                    world.remove(obj)
                    val lit = placeLoc(29748, tile, rotation = rot)
                    braziers[index] = Brazier(lit, have)
                    p.addXp(Skills.FIREMAKING, have * 5.0)
                    reward(p, have, 32)
                    break
                }
            }
            p.animate(-1)
        }
    }

    /** Op 'Fuel' on a lit brazier: all kindling held goes in (tsps). */
    fun fuel(p: Player, obj: GameObject) {
        val b = braziers[brazierIndex(obj.tile)] ?: return
        val kindling = CoxIds.item(20799) ?: return
        val amount = p.inventory.getItemCount(kindling)
        if (amount <= 0) return
        p.inventory.remove(kindling, amount)
        b.kindling += amount
        animatePlayer(p, 832)
        p.addXp(Skills.FIREMAKING, amount * 5.0)
        reward(p, amount, 0)
    }

    private fun reward(p: Player, kindling: Int, bonus: Int) {
        val points = maxOf(0, minOf(6050 - kindlingPoints, kindling * 36 + bonus))
        kindlingPoints += points
        raid.addPoints(p.username, points)
    }

    private fun extinguish(i: Int) {
        val b = braziers.remove(i) ?: return
        val o = b.obj ?: return
        val tile = Tile(o.tile); val rot = o.rot
        removeLoc(o)
        CoxIds.loc(29747)?.let { world.spawn(gg.rsmod.game.model.entity.DynamicObject(it, 10, rot, tile)) }
    }

    private fun defreeze() {
        stage = Stage.THAWING
        val firstLit = braziers.values.firstOrNull()?.obj?.tile
        braziers.keys.toList().forEach { extinguish(it) }
        BRAZIERS[variant].forEachIndexed { i, b -> gfxAt(188, tile(b.x, b.z), height = 40, delay = i * 5) }
        areaSound(firstLit ?: tile(BRAZIERS[variant][0].x, BRAZIERS[variant][0].z), 1930, radius = 8)
        val fiends = icefiends.values.map { it.first }; icefiends.clear()
        later(1) {
            fiends.forEach { animate(it, 1580); areaSound(it.tile, 532, radius = 10) }
            val out = DEMON[variant][2]
            demon?.walkTo(tile(out.x, out.z, 2), detectCollision = false)
        }
        later(3) { fiends.forEach { remove(it) } }
        later(8) {
            val d = demon ?: return@later
            d.stopMovement()
            CoxIds.npc(7585)?.let { d.setTransmogId(it) }
            configure(d, 7585, 3, 67, keepHp = true)
            stage = Stage.FIGHTING
            attackWait = 1
        }
    }

    private fun fight() {
        val d = demon ?: return
        if (!alive(d) || --attackWait > 0) return
        val targets = raiders()
        if (targets.isEmpty()) { attackWait = 1; return }
        attackWait = 3
        val p = targets.random()
        val style = when { p.isProtectedFrom(CombatClass.RANGED) -> CombatClass.RANGED; p.isProtectedFrom(CombatClass.MAGIC) -> CombatClass.MAGIC
            else -> if (world.random(1) == 0) CombatClass.RANGED else CombatClass.MAGIC }
        val at = Tile(p.tile)
        d.faceTile(at)
        animate(d, 69)
        val sc = scaled(d) ?: return
        if (style == CombatClass.RANGED) {
            val cycles = 60 + 30 * maxOf(0, (d.tile.getDistance(at) - 1) / 3)
            projectile(d.tile, at, 1324, 51, cycles - 51, 88, 3, angle = 8)
            areaSound(d.tile, 1440, radius = 10)
            later((cycles + 29) / 30) {
                if (!alive(d)) return@later
                gfxAt(1325, at, height = 10); areaSound(at, 1021, radius = 6)
                inBlast(at).forEach { attack(d, it, 30 * (sc.stats[CoxMonsters.RNG] + 9) / 399, CombatClass.RANGED, protectedPercent = 50) }
            }
            return
        }
        projectile(d.tile, at, 366, 51, 9, 81, 3, angle = 8)
        areaSound(d.tile, 171, radius = 10)
        later(2) {
            if (!alive(d)) return@later
            listOf(Triple(0, 0, 0), Triple(-1, 1, 3), Triple(1, -1, 3), Triple(0, 1, 6), Triple(0, -1, 6), Triple(1, 1, 9), Triple(-1, -1, 9),
                Triple(1, 0, 12), Triple(-1, 0, 12)).forEach { (dx, dz, delay) -> gfxAt(363, at.transform(dx, dz), height = 10, delay = delay) }
            areaSound(at, 170, radius = 6)
            inBlast(at).forEach { r ->
                attack(d, r, 25 * (sc.stats[CoxMonsters.MAG] + 9) / 399, CombatClass.MAGIC, protectedPercent = 33) { dealt ->
                    if (dealt >= 0) later(2) {
                        if (kotlin.math.abs(r.tile.x - at.x) <= 1 && kotlin.math.abs(r.tile.z - at.z) <= 1) {
                            r.message("<col=ef1020>You have been frozen!</col>")
                            r.freeze(9) {}
                        }
                    }
                }
            }
        }
    }

    private fun inBlast(at: Tile) = raiders().filter { kotlin.math.abs(it.tile.x - at.x) <= 1 && kotlin.math.abs(it.tile.z - at.z) <= 1 }

    /** Wiki: a third of all damage, fire spells 250%, demonbane weapons full; frozen it can't be hurt. */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !== demon) return
        if (stage != Stage.FIGHTING) { hit.hitmarks.forEach { it.damage = 0 }; return }
        val p = hit.source as? Player ?: return
        val magic = hit.sourceCombatClass == CombatClass.MAGIC
        val fire = magic && ElementalWeakness.elementOf(p.attr[Combat.CASTING_SPELL]) == ElementalWeakness.Element.FIRE
        val weapon = p.getEquipment(EquipmentType.WEAPON)?.id
        val demonbane = !magic && weapon != null && weapon in DEMONBANE
        hit.hitmarks.forEach { it.damage = when { fire -> it.damage * 5 / 2; demonbane -> it.damage; else -> it.damage / 3 } }
    }

    override fun onNpcDeath(npc: Npc) {
        if (npc !== demon) return
        stage = Stage.DEAD
        completed = true
        // Wiki 'Ice demon' drops: stinkhorn mushroom x7, endarkened juice x7, cicely x5 (fiendish ashes: not in our game).
        val at = Tile(npc.tile)
        drop(at, 20910, 7); drop(at, 20911, 7); drop(at, 20912, 5)
        clearExit()
        val s = storm; storm = null
        later(5) { removeLoc(s) }
    }

    companion object {
        /** Demonbane weapons (tsps list = OSRS Silverlight, Darklight, Arclight, Burning claws, Emberlight, Scorching bow; our item ids). */
        val DEMONBANE = setOf(gg.rsmod.plugins.api.cfg.Items.SILVERLIGHT, gg.rsmod.plugins.api.cfg.Items.DARKLIGHT, gg.rsmod.plugins.api.cfg.Items.ARCLIGHT,
            gg.rsmod.plugins.api.cfg.Items.BURNING_CLAWS, gg.rsmod.plugins.api.cfg.Items.EMBERLIGHT, gg.rsmod.plugins.api.cfg.Items.SCORCHING_BOW)
        val DEMON = listOf(
            listOf(Tile(3271, 5365), Tile(3272, 5365), Tile(3278, 5365)),
            listOf(Tile(3310, 5368), Tile(3310, 5367), Tile(3310, 5361)),
            listOf(Tile(3349, 5362), Tile(3348, 5362), Tile(3342, 5363)),
        )
        val BRAZIERS = listOf(
            listOf(Tile(3272, 5362), Tile(3275, 5364), Tile(3275, 5367), Tile(3272, 5369)),
            listOf(Tile(3307, 5368), Tile(3309, 5365), Tile(3312, 5365), Tile(3314, 5368)),
            listOf(Tile(3349, 5366), Tile(3346, 5364), Tile(3346, 5361), Tile(3349, 5359)),
        )
        val FIENDS = listOf(
            listOf(Tile(3273, 5362), Tile(3275, 5365), Tile(3275, 5366), Tile(3273, 5369)),
            listOf(Tile(3307, 5367), Tile(3310, 5365), Tile(3311, 5365), Tile(3314, 5367)),
            listOf(Tile(3348, 5366), Tile(3346, 5363), Tile(3346, 5362), Tile(3348, 5359)),
        )
    }
}
