package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.activity.cox.CoxRaids
import gg.rsmod.plugins.content.combat.poison

/**
 * Vespula. Wiki (Vespula; Lux grub; Abyssal portal; Strategies): Vespula isn't aggressive but answers attacks with ranged (poison) while
 * stinging grubs; a stung grub's meter drains, fed medivaemia blossoms it refills, at zero it bursts into a vespine soldier (melee, poison)
 * that heals Vespula and the portal; grubs have 125 hitpoints; flying she can only be hurt by Magic, Ranged or halberds; from 20% she may
 * land each time she's hit (certainly at 1 hp) and stays down ~20 seconds; attacking the portal while she flies enrages her (stings in
 * melee range 5-20, stomps under her 2-8 a tick); septic tendrils close the way in once the fight starts.
 * tsps rsprox (820, 843, 3389): sting 7454 + sound 570, grub sickly 7535 / infected 7536; enraged 7531, stings every 3 ticks (sound 819),
 * every 21 ticks flying for a tick; portal projectile 1366 every 2 ticks; grub at zero dies (7537), soldier hatches (7460, gfx 1365), grub
 * healthy again 37 ticks later; soldiers heal every 5 ticks (gfx 1367, sound 808); the portal falling: 'As the link to the Abyss
 * disintegrates, the abyssal creatures perish.', everything dies (7459 / landed 7458), boils burst 2 ticks later (gfx 240, sound 1457).
 * ONZEKER (tsps): tiles, portal drain 1 + 3% prayer, soldier heal 17, Vespula's ranged (7455 / 7450 landed, projectile 1364, base 15),
 * landing 7457 / rising 7452, blossoms heal a grub 20%, roots regrow 8 ticks after picking, grub meter -1 every 4 ticks, landed 33 ticks.
 */
class VespulaRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private var loaded = false
    private var vespula: Npc? = null
    private var portal: Npc? = null
    private val grubs = mutableListOf<Npc>()
    private val soldierTile = HashMap<Npc, Tile>()
    private val soldierOf = HashMap<Npc, Npc>()
    private val burstUntil = HashMap<Npc, Int>()
    private val healAt = HashMap<Npc, Int>()
    private val landedSoldierTicks = HashMap<Npc, Int>()
    private val tendrils = mutableListOf<GameObject>()
    private val boils = mutableListOf<GameObject>()
    private lateinit var home: Tile
    private val portalHitTiles = mutableListOf<Tile>()
    private lateinit var portalShotFrom: Tile
    private var form = 7530
    private var landedUntil = 0
    private var portalHitAt = -100
    private var enragedAt = 0
    private var stingDelay = 2
    private var stinging: Npc? = null
    private var attackedAt = -100
    private var nextShot = 0
    private val soldierNext = HashMap<Npc, Int>()

    override fun load() {
        PORTAL_HIT[variant].forEach { portalHitTiles += tile(it.x, it.z) }
        portalShotFrom = tile(PORTAL_FROM[variant].x, PORTAL_FROM[variant].z)
        val from = CoxIds.loc(30069); val to = CoxIds.loc(30068)
        if (from != null && to != null) CoxRaids.replaceInRoom(raid, floor, laid, from, to)
    }

    /** The fight is set up when the first raider walks in (tsps; not on the Wiki). */
    private fun setUp() {
        loaded = true
        for (i in 0..3) {
            val g = GRUBS[variant][i]
            val grub = spawn(7534, tile(g.x - 1, g.z - 1, 3)) ?: continue
            grub.combatDef = grub.combatDef.copy(lifepoints = 125); grub.setCurrentLifepoints(125)
            val s = SOLDIERS[variant][i]
            soldierTile[grub] = tile(s.x - 1, s.z - 1, 3)
            grubs += grub
        }
        BOILS[variant].forEach { b -> placeLoc(30070, tile(b.x, b.z), rotation = 0)?.let { boils += it } }
        portal = spawn(7533, tile(PORTAL[variant].x, PORTAL[variant].z, 6), speed = 2)
        vespula = spawn(7530, tile(VESPULA[variant].x - 2, VESPULA[variant].z - 2, 5), speed = 4, deathSeq = 7459)
        home = Tile(vespula?.tile ?: return)
    }

    override fun onTick() {
        if (!loaded) { if (raiders().isNotEmpty() && raid.started) setUp(); return }
        val v = vespula ?: return
        if (completed || !alive(v)) return
        tickGrubs(); tickPortal(); tickVespula(v)
        soldiers().forEach { tickSoldier(it) }
    }

    private fun soldiers() = soldierOf.values.filter { alive(it) }

    private fun tickGrubs() {
        for (g in grubs) {
            val until = burstUntil[g]
            if (until != null) { if (tick >= until) { burstUntil.remove(g); g.setTransmogId(-1); g.setCurrentLifepoints(125) }; continue }
            if (g.getCurrentLifepoints() >= 125) { if (g.getTransmogId() >= 0) g.setTransmogId(-1); continue }
            if (tick % 4 == 0) g.setCurrentLifepoints(maxOf(0, g.getCurrentLifepoints() - 1))
            CoxIds.npc(if (g.getCurrentLifepoints() <= 62) 7536 else 7535)?.let { g.setTransmogId(it) }
            if (g.getCurrentLifepoints() <= 0) burst(g)
        }
    }

    private fun burst(g: Npc) {
        burstUntil[g] = tick + 37
        CoxIds.npc(7537)?.let { g.setTransmogId(it) }
        val s = spawn(7538, Tile(g.tile), speed = 4) ?: return
        soldierOf[g] = s
        animate(s, 7460); gfxOn(s, 1365)
        healAt[s] = tick + 5
        soldierNext[s] = tick + 5
        soldierTile[g]?.let { s.walkTo(it, detectCollision = false) }
    }

    private fun tickPortal() {
        val p = portal ?: return
        if (!alive(p)) return
        for (s in soldiers()) {
            if (tick < (healAt[s] ?: 0)) continue
            healAt[s] = tick + 5
            gfxOn(s, 1367, height = 200); areaSound(s.tile, 808, radius = 6)
            listOfNotNull(p, vespula).forEach { it.setCurrentLifepoints(minOf(it.getMaximumLifepoints(), it.getCurrentLifepoints() + 17)) }
        }
        if (tick % 2 != 0) return
        for (r in raiders()) {
            if (portalHitTiles.none { it.x == r.tile.x && it.z == r.tile.z }) continue
            val at = Tile(r.tile)
            projectile(portalShotFrom, at, 1366, 0, 60, 35, 30, angle = 2, steepness = 128)
            later(1) {
                if (r.tile.x != at.x || r.tile.z != at.z) return@later
                val pray = r.skills.getCurrentLevel(Skills.PRAYER)
                if (pray > 0) r.skills.setCurrentLevel(Skills.PRAYER, maxOf(0, pray - (1 + Math.round(pray * 0.03f))))
                else damage(null, r, world.random(1..2))
            }
        }
    }

    private fun tickVespula(v: Npc) {
        if (stingDelay > 0 && form != 7532) { stingDelay--; }
        if (form == 7532) {
            if (tick >= landedUntil) { v.setCurrentLifepoints(v.getMaximumLifepoints()); animate(v, 7452); setForm(v, 7530) }
            else answer(v)
            return
        }
        if (tick - portalHitAt < 20) { enraged(v); return }
        if (form == 7531) setForm(v, 7530)
        answer(v)
        if (stingDelay > 0) return
        val g = stinging
        if (g != null && g.getCurrentLifepoints() >= 125 && burstUntil[g] == null) {
            if (!v.hasMoveDestination()) {
                stinging = null
                animate(v, 7454)
                areaSound(g.tile, 570, radius = 8, delay = 15)
                v.faceTile(g.tile)
                later(1) { g.setCurrentLifepoints(62) }
                stingDelay = 4
            }
            return
        }
        if (soldiers().isNotEmpty()) return
        val nextGrub = grubs.filter { it.getCurrentLifepoints() >= 125 && burstUntil[it] == null }.maxByOrNull { it.tile.getDistance(v.tile) } ?: return
        stinging = nextGrub
        v.walkTo(nextGrub.tile)
    }

    /** She answers attacks with ranged: at the target and a tile near it, poison 1 in 4 (tsps base 15, 7455 / 7450 landed). */
    private fun answer(v: Npc) {
        if (tick - attackedAt > 10 || tick < nextShot) return
        val p = raiders().minByOrNull { it.tile.getDistance(v.tile) } ?: return
        nextShot = tick + 4
        val landed = form == 7532
        animate(v, if (landed) 7450 else 7455)
        val sc = scaled(v) ?: return
        val max = CoxMonsters.maxHitFor(7530, sc, 15, CoxMonsters.RNG)
        projectile(v.tile, p, 1364, if (landed) 30 else 60, 24, 20, 15)
        val lands = maxOf(1, ((if (landed) 30 else 60) + 24) / 30)
        attack(v, p, max, CombatClass.RANGED, delay = lands, protectedPercent = 50)
        val near = p.tile.transform(world.random(-2..2), world.random(-2..2))
        projectile(v.tile, near, 1364, if (landed) 30 else 60, 24, 20, 0)
        later(lands) {
            raiders().filter { it.tile.getDistance(near) <= 1 }.forEach { r ->
                attack(v, r, max, CombatClass.RANGED, protectedPercent = 50)
                if (world.random(3) == 0) r.poison(20)
            }
        }
    }

    private fun enraged(v: Npc) {
        raiders().filter { it.tile.x - v.tile.x in 0..4 && it.tile.z - v.tile.z in 0..4 }.forEach { damage(v, it, world.random(2..8)) }
        if (form != 7531) {
            if (v.tile.x == home.x && v.tile.z == home.z) { setForm(v, 7531); enragedAt = tick }
            else if (!v.hasMoveDestination()) v.walkTo(home)
            return
        }
        if (tick - enragedAt >= 20) { setForm(v, 7530); return }
        if (--stingDelay > 0) return
        stingDelay = 3
        val reach = raiders().filter { r -> val x = r.tile.x - v.tile.x; val z = r.tile.z - v.tile.z; x in -1..5 && z in -1..5 && !(x in 0..4 && z in 0..4) }
        if (reach.isEmpty()) return
        val t = reach.random().tile
        v.faceTile(t); animate(v, 7454); areaSound(t, 819, radius = 5)
        reach.filter { it.tile.getDistance(t) <= 1 }.forEach { r -> damage(v, r, world.random(5..20)); if (world.random(3) == 0) r.poison(20) }
    }

    private fun tickSoldier(s: Npc) {
        val landed = osrs(s) == 7539
        if (landed) {
            val n = (landedSoldierTicks[s] ?: 0) + 1; landedSoldierTicks[s] = n
            if (n >= 17) {
                gfxAt(1368, Tile(s.tile))
                raiders().forEach { r -> play(r, 1021); if (r.tile.getDistance(s.tile) <= 3) damage(null, r, world.random(15..30)) }
                remove(s); return
            }
        }
        val p = nearest(s) ?: return
        if (!meleeReach(s, p.tile)) { if (landed) s.walkTo(p.tile); return }
        if (tick < (soldierNext[s] ?: 0)) return
        soldierNext[s] = tick + 4
        animate(s, 7454); play(p, 819)
        attack(s, p, 16, CombatClass.MELEE, protectedPercent = 50)
        if (world.random(2) == 0) p.poison(15)
    }

    private fun setForm(v: Npc, osrsForm: Int) { form = osrsForm; CoxIds.npc(osrsForm)?.let { v.setTransmogId(it) } }

    private fun land(v: Npc) {
        animate(v, 7457)
        setForm(v, 7532)
        landedUntil = tick + 33
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, roomLocs(30072).map { it.tile })

    override fun incoming(npc: Npc, hit: Hit) {
        val style = hit.sourceCombatClass
        if (npc === portal) {
            portalHitAt = tick
            if (tendrils.isEmpty() && !completed) TENDRILS[variant].forEach { t -> placeLoc(30024, tile(t.x, t.z), rotation = 0)?.let { tendrils += it } }
            return
        }
        val v = vespula
        if (npc === v) {
            attackedAt = tick
            val halberd = (hit.source as? Player)?.equipment?.get(3)?.getDef(world.definitions)?.name?.contains("halberd", true) == true
            if (form != 7532 && style == CombatClass.MELEE && !halberd) { hit.hitmarks.forEach { it.damage = 0 }; return }
            if (form == 7532 && (style == CombatClass.RANGED || style == CombatClass.MAGIC)) { hit.hitmarks.forEach { it.damage = 0 }; return }
            val after = npc.getCurrentLifepoints() - hit.hitmarks.sumOf { it.damage }
            if (form != 7532 && after <= npc.getMaximumLifepoints() / 5 && (after <= 1 || world.random(2) == 0)) land(npc)
            if (after < 1) { var left = npc.getCurrentLifepoints() - 1; hit.hitmarks.forEach { m -> val d = minOf(m.damage, left.coerceAtLeast(0)); m.damage = d; left -= d } }
            return
        }
        if (npc in soldierOf.values) {
            if (osrs(npc) == 7539 && (style == CombatClass.RANGED || style == CombatClass.MAGIC)) { hit.hitmarks.forEach { it.damage = 0 }; return }
            if (osrs(npc) == 7538 && npc.getCurrentLifepoints() - hit.hitmarks.sumOf { it.damage } <= npc.getMaximumLifepoints() / 5) {
                animate(npc, 7457); CoxIds.npc(7539)?.let { npc.setTransmogId(it) }; configure(npc, 7539, 4, keepHp = true)
            }
        }
    }

    override fun onNpcDeath(npc: Npc) {
        if (npc !== portal) return
        completed = true
        clearExit()
        world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
            .forEach { it.message("<col=ef20ff>As the link to the Abyss disintegrates, the abyssal creatures perish.</col>") }
        val dying = mutableListOf<Npc>()
        vespula?.takeIf { alive(it) }?.let { animate(it, if (form == 7532) 7458 else 7459); dying += it }
        soldiers().forEach { animate(it, 7459); dying += it }
        grubs.toList().forEach { remove(it) }
        val from = CoxIds.loc(30068); val to = CoxIds.loc(30069)
        if (from != null && to != null) CoxRaids.replaceInRoom(raid, floor, laid, from, to)
        later(4) { val a = CoxIds.loc(30072); val b = CoxIds.loc(30073); if (a != null && b != null) CoxRaids.replaceInRoom(raid, floor, laid, a, b) }
        later(2) {
            dying.forEach { remove(it) }
            boils.toList().forEach { b -> gfxAt(240, Tile(b.tile), height = 5); areaSound(b.tile, 1457, radius = 10); removeLoc(b); placeLoc(30071, Tile(b.tile), rotation = 0) }
            tendrils.toList().forEach { removeLoc(it) }; tendrils.clear()
        }
    }

    /** Medivaemia root (30068 'Pick'): a blossom (20892), the root picked for 8 ticks (tsps). */
    fun pick(p: Player, root: GameObject) {
        if (completed) return
        val blossom = CoxIds.item(20892) ?: return
        if (p.inventory.freeSlotCount <= 0) { p.message("You don't have space to hold it."); return }
        animatePlayer(p, 832); play(p, 2581)
        p.inventory.add(blossom)
        val picked = CoxIds.loc(30069) ?: return
        val tile = Tile(root.tile); val rot = root.rot
        world.remove(root)
        val o = gg.rsmod.game.model.entity.DynamicObject(picked, root.type, rot, tile); world.spawn(o)
        later(8) { if (!completed && o.isSpawned(world)) { world.remove(o); CoxIds.loc(30068)?.let { world.spawn(gg.rsmod.game.model.entity.DynamicObject(it, 10, rot, tile)) } } }
    }

    /** Blossom on a grub: +20% (tsps); 'It doesn't look like it needs feeding.' */
    fun feed(p: Player, grub: Npc) {
        if (grub !in grubs) return
        if (grub.getCurrentLifepoints() >= 125 || burstUntil[grub] != null) { p.message("It doesn't look like it needs feeding."); return }
        val blossom = CoxIds.item(20892) ?: return
        if (!p.inventory.contains(blossom)) { p.message("You need some medivaemia roots to feed the grubs."); return }
        p.inventory.remove(blossom, 1)
        grub.setCurrentLifepoints(minOf(125, grub.getCurrentLifepoints() + 25))
        animatePlayer(p, 827); play(p, 2393)
    }

    companion object {
        val GRUBS = listOf(
            listOf(Tile(3287, 5289), Tile(3285, 5294), Tile(3277, 5286), Tile(3278, 5294)),
            listOf(Tile(3312, 5289), Tile(3304, 5291), Tile(3312, 5297), Tile(3304, 5297)),
            listOf(Tile(3344, 5294), Tile(3337, 5286), Tile(3337, 5294), Tile(3334, 5290)),
        )
        val SOLDIERS = listOf(
            listOf(Tile(3284, 5289), Tile(3285, 5291), Tile(3277, 5289), Tile(3278, 5291)),
            listOf(Tile(3309, 5289), Tile(3307, 5291), Tile(3309, 5297), Tile(3307, 5297)),
            listOf(Tile(3344, 5291), Tile(3337, 5289), Tile(3337, 5291), Tile(3337, 5290)),
        )
        val BOILS = listOf(listOf(Tile(3267, 5295), Tile(3267, 5296)), listOf(Tile(3311, 5308), Tile(3312, 5308)), listOf(Tile(3356, 5296), Tile(3356, 5295)))
        val TENDRILS = listOf(listOf(Tile(3279, 5280), Tile(3280, 5280)), listOf(Tile(3311, 5280), Tile(3312, 5280)), listOf(Tile(3343, 5280), Tile(3344, 5280)))
        val PORTAL = listOf(Tile(3281, 5300), Tile(3318, 5291), Tile(3339, 5300))
        val VESPULA = listOf(Tile(3281, 5290), Tile(3308, 5292), Tile(3341, 5290))
        val PORTAL_FROM = listOf(Tile(3282, 5302), Tile(3320, 5292), Tile(3341, 5302))
        val PORTAL_HIT = listOf(listOf(Tile(3281, 5293), Tile(3282, 5293)), listOf(Tile(3311, 5293), Tile(3311, 5292)), listOf(Tile(3340, 5293), Tile(3341, 5293)))
    }
}
