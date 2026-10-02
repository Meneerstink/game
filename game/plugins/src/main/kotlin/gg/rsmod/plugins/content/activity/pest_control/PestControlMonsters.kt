package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.poison
import kotlin.random.Random

/** PROBLEM lines for Pest Control (TRACES.md; category "interact", shown with `traces`). */
object PestControlTrace {
    fun problem(message: String) = Trace.problem(Trace.Cat.INTERACT) { "pest control: $message" }
}

/**
 * The seven pest monsters' own behaviour (2009scape monsters/PC*NPC.java). Their combat itself (stats, attack, animations)
 * is the shared npc combat from combat-defs.json; this adds what each type does besides fighting:
 *
 * - Defiler, Torcher: attack the Void Knight whenever they have nothing else to fight.
 * - Shifter: sometimes goes for the knight, and teleports next to its target when it is more than 5 tiles away.
 * - Ravager: walks to the nearest barricade or gate within 5 tiles and smashes it (one step every 5 ticks); otherwise wanders
 *   towards the knight, or back to its portal once inside the camp.
 * - Spinner: heals its own portal by 10 % when within 5 tiles of it; explodes (5 poison damage) when that portal falls.
 * - Splatter: explodes on a barricade next to it, and on death, hurting everything next to it and damaging the barricade.
 * - Brawler: melee only, the shared combat.
 */
object PestControlMonsters {
    private val nextRavagerAttack = HashMap<Npc, Int>()
    private val ravagerTarget = HashMap<Npc, GameObject>()

    fun tick(game: PestControlGame) {
        for (m in game.monsters.toList()) {
            if (m.isDead() || !m.isSpawned()) continue
            when (pestMonsterOf(m.id)) {
                PestMonster.DEFILER, PestMonster.TORCHER -> if (idle(m)) m.attack(game.knight, notifyRefusal = false)
                PestMonster.SHIFTER -> shifter(game, m)
                PestMonster.RAVAGER -> ravager(game, m)
                PestMonster.SPINNER -> spinner(game, m)
                PestMonster.SPLATTER -> splatter(game, m)
                else -> Unit
            }
        }
        nextRavagerAttack.keys.removeIf { !it.isSpawned() || it.isDead() }
        ravagerTarget.keys.removeIf { !it.isSpawned() || it.isDead() }
    }

    private fun idle(m: Npc): Boolean = m.getCombatTarget().let { it == null || it.isDead() }

    private fun shifter(
        game: PestControlGame,
        m: Npc,
    ) {
        if (idle(m) && Random.nextInt(50) < 2) m.attack(game.knight, notifyRefusal = false)
        val target = m.getCombatTarget() ?: return
        if (!m.tile.isWithinRadius(target.tile, 5)) {
            val dest =
                (-2..2).flatMap { x -> (-2..2).map { z -> x to z } }
                    .filter { (x, z) -> x != 0 && z != 0 }
                    .shuffled()
                    .map { (x, z) -> target.tile.transform(x, z, 0) }
                    .firstOrNull { game.map.area.contains(it) && game.world.collision.isClipped(it).not() }
                    ?: return
            game.world.spawn(gg.rsmod.game.model.TileGraphic(m.tile, PC_SHIFTER_TELEPORT_GFX, 0))
            m.moveTo(dest)
            m.animate(PC_SHIFTER_TELEPORT_ANIM)
            game.world.spawn(gg.rsmod.game.model.TileGraphic(dest, PC_SHIFTER_TELEPORT_GFX, 0))
        }
    }

    private fun ravager(
        game: PestControlGame,
        m: Npc,
    ) {
        if (!idle(m)) {
            ravagerTarget.remove(m)
            return
        }
        var target = ravagerTarget[m]
        if (target != null && (target !in game.barricades || !PestControlBarricades.isDamageable(target))) {
            ravagerTarget.remove(m)
            target = null
        }
        if (target == null) {
            target = game.barricades.firstOrNull { PestControlBarricades.isDamageable(it) && it.tile.isWithinRadius(m.tile, 5) }
            if (target != null) {
                ravagerTarget[m] = target
                m.walkTo(besideOf(target, m))
                return
            }
            if (Random.nextInt(10) < 2 && !m.hasMoveDestination()) {
                // 2009scape offLimits: local (20..44, 26..57) is the camp; inside it the ravager goes back to its portal.
                val lx = m.tile.x - game.base.x
                val lz = m.tile.z - game.base.z
                val dest = if (lx in 20..44 && lz in 26..57) game.portals.filterNotNull().randomOrNull()?.tile else game.knight.tile
                dest?.let { m.walkTo(it) }
            }
            return
        }
        val now = game.world.currentCycle
        if (!m.tile.isWithinRadius(target.tile, 1)) {
            // The barricade's own tile is blocked: keep heading for the open tile beside it until the ravager stands there.
            if (!m.hasMoveDestination()) m.walkTo(besideOf(target, m))
            return
        }
        if ((nextRavagerAttack[m] ?: 0) < now) {
            m.stopMovement()
            m.faceTile(target.tile)
            m.animate(m.combatDef.attackAnimation)
            val replaced = PestControlBarricades.damage(game, target)
            if (replaced == null || !PestControlBarricades.isDamageable(replaced)) ravagerTarget.remove(m) else ravagerTarget[m] = replaced
            nextRavagerAttack[m] = now + 5
        }
    }

    /** The tile next to [obj] on [m]'s side. */
    private fun besideOf(
        obj: GameObject,
        m: Npc,
    ) = obj.tile.transform(Integer.signum(m.tile.x - obj.tile.x), Integer.signum(m.tile.z - obj.tile.z), 0)

    private fun spinner(
        game: PestControlGame,
        m: Npc,
    ) {
        if (Random.nextInt(10) >= 2) return
        val index = game.spinnerPortal[m] ?: return
        val portal = game.portals[index] ?: return
        if (portal.isDead() || !portal.isSpawned()) return
        if (portal.getCurrentLifepoints() >= portal.getMaximumLifepoints()) return
        if (m.tile.isWithinRadius(portal.getCentreTile(), 5)) {
            m.facePawn(portal)
            m.animate(PC_SPINNER_HEAL_ANIM)
            m.graphic(PC_SPINNER_HEAL_GFX)
            portal.setCurrentLifepoints(minOf(portal.getMaximumLifepoints(), portal.getCurrentLifepoints() + portal.getMaximumLifepoints() / 10))
            game.portalHit(portal)
        } else {
            m.walkTo(portal.tile)
        }
    }

    /** 2009scape PCSpinnerNPC.explode: 5 poison damage (x 10 lifepoints) to every player next to it, then gone. */
    fun spinnerExplode(
        game: PestControlGame,
        m: Npc,
    ) {
        if (!m.isSpawned()) return
        m.animate(m.combatDef.deathAnimation.firstOrNull() ?: -1)
        for (p in playersNear(game, m, 1)) {
            p.hit(50, HitType.POISON)
            p.poison(10)
        }
        game.spinnerPortal.remove(m)
        game.monsters.remove(m)
        game.world.remove(m)
    }

    private fun splatter(
        game: PestControlGame,
        m: Npc,
    ) {
        if (!idle(m)) return
        if (game.barricades.any { it.tile.isWithinRadius(m.tile, 1) }) splatterExplode(game, m)
    }

    /** 2009scape PCSplatterNPC.commenceDeath/explode: damages the barricades and everything next to it, then disappears. */
    fun splatterExplode(
        game: PestControlGame,
        m: Npc,
        dying: Boolean = false,
    ) {
        if ((!dying && !m.isSpawned()) || m.attr.has(EXPLODED_ATTR)) return
        m.attr[EXPLODED_ATTR] = true
        m.animate(PC_SPLATTER_EXPLODE_ANIM)
        m.graphic(PC_SPLATTER_EXPLODE_GFX_BASE + (m.id - 3727))
        // 2009scape: maximum = combat level / 3 hitpoints, minimum half of that; x 10 for 667 lifepoints.
        val maximum = m.def.combatLevel / 3 * 10
        val minimum = maximum / 2
        for (obj in game.barricades.toList()) {
            if (obj.tile.isWithinRadius(m.tile, 1)) PestControlBarricades.damage(game, obj)
        }
        for (p in playersNear(game, m, 1)) p.hit(Random.nextInt(minimum, maximum + 1))
        for (n in game.monsters.toList()) {
            if (n !== m && !n.isDead() && n.tile.isWithinRadius(m.tile, 1)) n.hit(Random.nextInt(minimum, maximum + 1))
        }
        game.monsters.remove(m)
        if (!dying) {
            game.world.queue {
                wait(1)
                if (m.isSpawned()) game.world.remove(m)
            }
        }
    }

    private fun playersNear(
        game: PestControlGame,
        m: Npc,
        radius: Int,
    ): List<Player> = game.players.filter { it.tile.isWithinRadius(m.tile, radius) }

    private val EXPLODED_ATTR = gg.rsmod.game.model.attr.AttributeKey<Boolean>()
}
