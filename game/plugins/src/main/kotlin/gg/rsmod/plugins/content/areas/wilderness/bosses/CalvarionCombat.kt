package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula

/**
 * N522 nachtrun deel 2 (owner 2026-11-04: Calvar'ion via route B): the OSRS Calvar'ion (OSRS npcs 11993 normal / 11994 enraged, NpcID
 * VETION_SINGLE / VETION_2_SINGLE) in the Skeletal Tomb, baked skeletal (OsrsPetImportTool skeletal-npc calvarion, tx-20261105-000956);
 * hellhounds 12107 / 12108 (OsrsNpcImportTool calvarion-hounds), lightning FX_VETION_ATTACK_* (OsrsFxImportTool calvarion).
 *
 * Mechanics - OSRS Wiki "Calvar'ion" + "Calvar'ion/Strategies" (2026-11-05), tick details from Near Reality (zaryx-nr) Vetion.java:
 * - Only crush hurts him ("Immune to all combat styles except crush"); no damage at all while his hellhounds live.
 * - Lightning (Near Reality lightningAttack): five tiles - one next to the target, four more within 5 - glow, and 2 ticks later strike a
 *   3x3: up to 20 on the tile itself, up to 10 next to it; protection prayers do not help. Blue (normal) / orange (enraged).
 * - Shield bash, "only used when in melee distance" (Near Reality: a player within 4, 1 in 2): shadows mark "two tiles thick on a side or
 *   diagonal, or one tile thick on every side"; players there or under him take up to 26 and cannot attack for 4.8 seconds (8 ticks).
 * - At half health: two Skeleton Hellhounds (enraged: two Greater ones), 30 hitpoints each; he yells when they are gone.
 * - At 0 in his first form: "Now... DO IT AGAIN!!!", full heal, orange lightning in a ring round him, enraged form (attack speed 5); not
 *   finished within 5 minutes (Wiki) -> back to the normal form.
 * - Shouts: Near Reality FORCE_CHATS / HOUND_SPAWN_CHATS / HOUND_DEATH_CHATS / DEATH_CHATS (capitals when enraged).
 */
object CalvarionCombat : CombatScript() {
    const val NORMAL = Npcs.CALVARION
    const val ENRAGED = Npcs.CALVARION_ENRAGED
    const val HOUND = Npcs.CALVARION_HELLHOUND
    const val GREATER_HOUND = Npcs.CALVARION_GREATER_HELLHOUND
    override val ids = intArrayOf(NORMAL, ENRAGED)

    // NPC_VETION_*_01 (normal) / _02 (enraged), baked.
    const val ANIM_MAGIC = 16251
    const val ANIM_MELEE = 16252
    const val ANIM_SHIELD = 16253
    const val ANIM_SUMMON = 16254
    const val ANIM_SPAWN = 16255
    const val ANIM_DEATH = 16265
    const val ANIM_MAGIC_ENRAGED = 16260
    const val ANIM_MELEE_ENRAGED = 16261
    const val ANIM_SHIELD_ENRAGED = 16262
    const val ANIM_SUMMON_ENRAGED = 16263
    const val ANIM_ENRAGE = 16256

    const val HOUND_ATTACK = 16267 // DOG_UPDATE_SKELETON_HELLHOUND_ATTACK 6579
    const val HOUND_DEFEND = 16268 // _DEFEND 6578
    const val HOUND_DEATH = 16269 // DOG_UPDATE_WOLF_DEATH 6558

    const val GFX_LIGHTNING = 3232 // FX_VETION_ATTACK_MAGIC_IMPACT_01 2346
    const val GFX_LIGHTNING_ENRAGED = 3233 // _IMPACT_02 2347
    const val GFX_SHIELD_SHADOW = 3234 // FX_VETION_ATTACK_MELEE 2348

    const val LIGHTNING_DIRECT_MAX = 20
    const val LIGHTNING_SPLASH_MAX = 10
    const val LIGHTNING_TICKS = 2
    const val BOLTS = 5
    const val SHIELD_MAX = 26
    const val SHIELD_LOCK_TICKS = 8
    const val SHIELD_RANGE = 4
    const val ENRAGE_TIMEOUT_TICKS = 500 // 5 minutes

    val SPAWN = Tile(1887, 11546, 1)
    val TOMB_X = 1877..1898
    val TOMB_Z = 11533..11560
    val HOUND_X = 1876..1894
    val HOUND_Z = 11540..11552

    const val MSG_IMMUNE = "You must kill the Skeletal Hellhounds first in order to harm Calvar'ion!"

    val SHOUTS =
        listOf(
            "You are powerless to me!", "You're not blocking this one!", "You are weak! You are feeble!", "Die, rodent!",
            "Stand still, rat!", "You're mine now!", "Now I've got you!", "Hold still so I can smite you!", "Take this!",
            "Time to die, mortal!", "Raaargh!", "Grrrah!", "For the lord!", "You can't escape!", "Filthy whelps!", "Dodge this!",
            "I will cut you down!", "You call that a weapon?!",
        )
    val HOUND_SPAWN_SHOUTS =
        listOf(
            "Gah! Hounds! Get them!", "Time to feast, hounds!", "Hounds! Dispose of these tresspassers!",
            "Hounds! Get rid of these interlopers!", "Go forth, my hounds, and destroy them!", "I've had enough of this! Hounds!",
        )
    val HOUND_DEATH_SHOUTS =
        listOf("I'll kill you for killing my pets!", "My hounds! I'll make you pay for that!", "Must I do everything around here?!", "Fine! I'll deal with you myself!")
    val DEATH_SHOUTS =
        listOf("This isn't... the last... of me...", "Urk! I... failed...", "I'll get you... next... time...", "My lord... I'm... sorry...", "Urg... not... again...", "I'll... be... back...")
    const val SHOUT_ENRAGE = "Now... DO IT AGAIN!!!"

    val HOUNDS = AttributeKey<MutableList<Npc>>()
    val HOUNDS_CALLED = AttributeKey<Boolean>()

    fun isCalvarion(id: Int) = id == NORMAL || id == ENRAGED

    fun enraged(npc: Npc) = npc.id == ENRAGED

    fun inTomb(t: Tile): Boolean = t.height == SPAWN.height && t.x in TOMB_X && t.z in TOMB_Z

    fun shout(npc: Npc, text: String) = npc.forceChat(if (enraged(npc)) text.uppercase() else text)

    fun hounds(npc: Npc): MutableList<Npc> = npc.attr[HOUNDS] ?: mutableListOf<Npc>().also { npc.attr[HOUNDS] = it }

    fun houndsAlive(npc: Npc): Boolean = hounds(npc).any { it.isSpawned() && !it.isDead() }

    /** Shared hit path (combat PawnExt): only crush hurts him, and nothing while his hellhounds live. */
    fun modifyIncomingDamage(target: Npc, attacker: Pawn, hitType: HitType, damage: Int): Int {
        if (!isCalvarion(target.id) || damage <= 0) return damage
        if (houndsAlive(target)) {
            (attacker as? Player)?.message(MSG_IMMUNE)
            return 0
        }
        val crush = hitType == HitType.MELEE && CombatConfigs.getCombatStyle(attacker) == StyleType.CRUSH
        return if (crush) damage else 0
    }

    /** At impact: a hit already flying when the hounds came is stopped too. */
    fun modifyIncomingHit(npc: Npc, hit: gg.rsmod.game.model.Hit) {
        if (!houndsAlive(npc)) return
        hit.hitmarks.forEach { if (it.damage > 0) it.damage = 0 }
    }

    /** The three shield-bash shapes (Strategies): two tiles thick on the side or diagonal the [target] stands, or one ring round him. */
    fun shieldTiles(npc: Npc, target: Tile, ring: Boolean): List<Tile> {
        val size = npc.getSize()
        val x0 = npc.tile.x
        val z0 = npc.tile.z
        val outer = (x0 - 2..x0 + size + 1).flatMap { x -> (z0 - 2..z0 + size + 1).map { z -> Tile(x, z, npc.tile.height) } }
        val under = outer.filter { ScorpiaCombat.distanceToArea(it, npc.tile, size) == 0 }
        if (ring) return under + outer.filter { ScorpiaCombat.distanceToArea(it, npc.tile, size) == 1 }
        val (vx, vz) = OldWildernessBosses.pushVector(npc, target)
        val side = outer.filter { t ->
            val d = ScorpiaCombat.distanceToArea(t, npc.tile, size)
            if (d !in 1..2) return@filter false
            val sx = Integer.signum(t.x - t.x.coerceIn(x0, x0 + size - 1))
            val sz = Integer.signum(t.z - t.z.coerceIn(z0, z0 + size - 1))
            sx == vx && sz == vz
        }
        return under + side
    }

    /** Lightning tiles (Near Reality lightningAttack): one next to the target (else its own tile), four more free tiles within 5. */
    fun lightningTiles(target: Tile, free: (Tile) -> Boolean, pick: (List<Tile>) -> List<Tile>, adjacent: Tile): List<Tile> {
        val first = if (free(adjacent)) adjacent else target
        val around = (-5..5).flatMap { dx -> (-5..5).map { dz -> target.transform(dx, dz) } }.filter { free(it) && !it.sameAs(first) }
        return listOf(first) + pick(around).take(BOLTS - 1)
    }

    /** 2 ticks after the glow: up to 20 on the tile itself, up to 10 next to it (Near Reality lightningHit). */
    fun lightningMax(distance: Int): Int = when (distance) { 0 -> LIGHTNING_DIRECT_MAX; 1 -> LIGHTNING_SPLASH_MAX; else -> 0 }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            houndsCheck(npc)
            val close = players(npc).filter { ScorpiaCombat.distanceToArea(it.tile, npc.tile, npc.getSize()) <= 1 }
            if (close.isNotEmpty() && npc.world.random(1) == 0) {
                shieldBash(npc, close.random())
            } else if (npc.moveToAttackRange(it, target, distance = 10, projectile = true)) {
                shout(npc, SHOUTS.random())
                lightning(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun players(npc: Npc): List<Player> {
        val out = ArrayList<Player>()
        npc.world.players.forEach { p -> if (p.isOnline && !p.isDead() && inTomb(p.tile)) out += p }
        return out
    }

    private fun lightning(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        val e = enraged(npc)
        npc.animate(if (npc.world.random(1) == 0) (if (e) ANIM_MAGIC_ENRAGED else ANIM_MAGIC) else (if (e) ANIM_MELEE_ENRAGED else ANIM_MELEE))
        val world = npc.world
        val t = Tile(target.tile)
        val adjacent = t.transform(world.random(-1..1), world.random(-1..1))
        val tiles = lightningTiles(t, { inTomb(it) && !world.collision.isClipped(it) }, { it.shuffled() }, adjacent)
        strike(npc, tiles, if (e) GFX_LIGHTNING_ENRAGED else GFX_LIGHTNING)
    }

    fun strike(npc: Npc, tiles: List<Tile>, gfx: Int) {
        val world = npc.world
        val life = WildernessBossLife.token(npc)
        tiles.forEach { world.spawn(TileGraphic(it, gfx, 0)) }
        world.queue {
            wait(LIGHTNING_TICKS)
            if (!WildernessBossLife.active(npc, life)) return@queue
            players(npc).forEach { p ->
                val d = tiles.minOf { maxOf(Math.abs(it.x - p.tile.x), Math.abs(it.z - p.tile.z)) }
                val max = lightningMax(d)
                if (max > 0) {
                    val damage = world.random(max)
                    npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                }
            }
        }
    }

    private fun shieldBash(npc: Npc, victim: Player) {
        npc.facePawn(victim)
        npc.animate(if (enraged(npc)) ANIM_SHIELD_ENRAGED else ANIM_SHIELD)
        val tiles = shieldTiles(npc, victim.tile, ring = npc.world.random(2) == 0)
        val world = npc.world
        val life = WildernessBossLife.token(npc)
        tiles.filter { ScorpiaCombat.distanceToArea(it, npc.tile, npc.getSize()) > 0 }.forEach { world.spawn(TileGraphic(it, GFX_SHIELD_SHADOW, 0)) }
        world.queue {
            wait(LIGHTNING_TICKS)
            if (!WildernessBossLife.active(npc, life)) return@queue
            players(npc).filter { p -> tiles.any { it.sameAs(p.tile) } }.forEach { p ->
                val damage = world.random(SHIELD_MAX)
                npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                p.timers[ATTACK_DELAY] = SHIELD_LOCK_TICKS
            }
        }
    }

    /** Half health, once per form: two hellhounds (greater when enraged) on free tiles of the tomb (Near Reality spawnHellhounds). */
    fun houndsCheck(npc: Npc) {
        if (npc.attr[HOUNDS_CALLED] == true) {
            val list = hounds(npc)
            if (list.isNotEmpty() && !houndsAlive(npc)) {
                list.clear()
                shout(npc, HOUND_DEATH_SHOUTS.random())
            }
            return
        }
        if (npc.getCurrentLifepoints() * 2 > npc.getMaximumLifepoints()) return
        npc.attr[HOUNDS_CALLED] = true
        npc.animate(if (enraged(npc)) ANIM_SUMMON_ENRAGED else ANIM_SUMMON)
        shout(npc, HOUND_SPAWN_SHOUTS.random())
        val world = npc.world
        val free = HOUND_X.flatMap { x -> HOUND_Z.map { z -> Tile(x, z, SPAWN.height) } }
            .filter { !world.collision.isClipped(it) && ScorpiaCombat.distanceToArea(it, npc.tile, npc.getSize()) > 1 }
            .shuffled().take(2)
        val target = npc.getCombatTarget()
        free.forEach { t ->
            val dog = Npc(if (enraged(npc)) GREATER_HOUND else HOUND, t, world)
            dog.respawns = false
            dog.walkRadius = 5
            if (world.spawn(dog)) {
                hounds(npc) += dog
                if (target != null && !target.isDead()) dog.attack(target)
            }
        }
    }

    fun removeHounds(npc: Npc) {
        hounds(npc).forEach { if (it.isSpawned()) npc.world.remove(it) }
        hounds(npc).clear()
    }

    /** First form at 0: the enraged form on the same tile, full health, a ring of orange lightning (Near Reality lightningRing). */
    fun enrage(npc: Npc): Npc? {
        removeHounds(npc)
        val next = OldWildernessBosses.nextPhase(npc, ENRAGED) ?: return null
        next.forceChat(SHOUT_ENRAGE)
        val mid = next.tile.transform(1, 1)
        val ring = listOf(1 to 3, 2 to 2, 3 to 1, 3 to -1, 2 to -2, 1 to -3, -1 to -3, -2 to -2, -3 to -1, -3 to 1, -2 to 2, -1 to 3)
            .map { (dx, dz) -> mid.transform(dx, dz) }.filter { !next.world.collision.isClipped(it) }
        next.animate(ANIM_SUMMON_ENRAGED)
        strike(next, ring, GFX_LIGHTNING_ENRAGED)
        startEnrageTimeout(next)
        return next
    }

    /** Wiki: not finished within 5 minutes, he reverts to the normal form (with the hitpoints he has). */
    fun startEnrageTimeout(npc: Npc) {
        val life = WildernessBossLife.token(npc)
        npc.world.queue {
            wait(ENRAGE_TIMEOUT_TICKS)
            if (!WildernessBossLife.active(npc, life)) return@queue
            removeHounds(npc)
            if (OldWildernessBosses.nextPhase(npc, NORMAL, npc.getCurrentLifepoints()) != null) npc.world.remove(npc)
        }
    }

    /** Calvar'ion's hellhounds: crush melee, max 16 / greater 18, speed 4 (OSRS Wiki infoboxes 12107 / 12108). */
    object HoundCombat : CombatScript() {
        override val ids = intArrayOf(HOUND, GREATER_HOUND)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
                    npc.animate(HOUND_ATTACK)
                    val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
                    npc.dealHit(target = target, maxHit = (if (npc.id == HOUND) 16 else 18).toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }
}
