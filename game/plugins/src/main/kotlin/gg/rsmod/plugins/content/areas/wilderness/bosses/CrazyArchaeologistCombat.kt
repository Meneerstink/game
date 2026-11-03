package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters

/**
 * N522 bundle 1: the OSRS Crazy archaeologist (OSRS npc 6618, imported as [Npcs.CRAZY_ARCHAEOLOGIST]).
 *
 * Mechanics - OSRS Wiki "Crazy archaeologist" + Strategies (2026-11-01): Ranged (thrown book, max 14) and Crush melee only when the
 * player is next to him, attack speed 3; special "Rain of knowledge!": three books land in an arc and explode in a 3x3 area (max 24),
 * one of them splits into two more books that explode nearby after a delay. Random shouts in combat, "Ow!" on death
 * (OSRS Wiki "Transcript:Crazy archaeologist").
 * Ids: Kronos-184 data/npcs/combat/Crazy_archaeologist.json (attack BOOK_CHUCK 3353, defend 425, death 2304) and
 * bosses/CrazyArchaeologist.java (special 1/8, melee kick 423 half the time when adjacent, books 1259 / special 1260 from the target
 * tile for the split, explosions 157 three ticks after the throw); RuneLite gameval SpotanimID CRAZY_ARCHAEOLOGIST_BOOK 1259 /
 * _BOOK_SPECIAL 1260 (OsrsFxImportTool wildy-bosses-1: 1259 -> 3196, 1260 -> 3197, 157 -> 3127).
 * Sounds ADAPTED (the OSRS Wiki lists none for him): OSRS "throw" 1576 for the book, OSRS human_attack for the kick (the breach
 * humans' imported 10295), OSRS "explosion" 1487 for the special.
 */
object CrazyArchaeologistCombat : CombatScript() {
    override val ids = intArrayOf(Npcs.CRAZY_ARCHAEOLOGIST)

    const val ANIM_THROW = 16115 // OSRS 3353 BOOK_CHUCK
    const val ANIM_KICK = 16116 // OSRS 423 HUMAN_UNARMEDKICK
    const val ANIM_DEFEND = 16113 // OSRS 425 HUMAN_UNARMED_DEF
    const val ANIM_DEATH = 16114 // OSRS 2304

    const val GFX_BOOK = 3196 // OSRS 1259
    const val GFX_BOOK_SPECIAL = 3197 // OSRS 1260
    const val GFX_EXPLOSION = 3127 // OSRS 157

    const val MAX_STANDARD = 14
    const val MAX_SPECIAL = 24
    const val ATTACK_RANGE = 8
    private const val SPECIAL_TICKS = 3

    const val SHOUT_SPECIAL = "Rain of knowledge!"
    const val SHOUT_DEATH = "Ow!"
    val SHOUTS =
        listOf(
            "You belong in a museum!",
            "I'm Bellock - respect me!",
            "Get off my site!",
            "These ruins are mine!",
            "Taste my knowledge!",
            "No-one messes with Bellock's dig!",
        )

    enum class Attack { RANGED, MELEE, SPECIAL }

    /** Kronos attack(): 1/8 special; next to the target 1/2 melee; otherwise the book. */
    fun choose(adjacent: Boolean, specialRoll: Int, meleeRoll: Int): Attack =
        when {
            specialRoll == 0 -> Attack.SPECIAL
            adjacent && meleeRoll == 0 -> Attack.MELEE
            else -> Attack.RANGED
        }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = ATTACK_RANGE, projectile = true)) {
                val adjacent = canKick(npc, target.tile)
                when (choose(adjacent, npc.world.random(7), npc.world.random(1))) {
                    Attack.SPECIAL -> special(npc, target)
                    Attack.MELEE -> {
                        melee(npc, target)
                        npc.forceChat(SHOUTS.random())
                    }
                    Attack.RANGED -> {
                        ranged(npc, target)
                        npc.forceChat(SHOUTS.random())
                    }
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    fun canKick(npc: Npc, tile: Tile): Boolean = gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
        npc.world.collision, npc.tile, npc.getSize(), npc.getSize(), tile, 1, 1)

    private fun ranged(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ANIM_THROW)
        npc.world.spawn(npc.createProjectile(target, GFX_BOOK, ProjectileType.ARROW))
        val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        if (target is Player) target.playSound(Sfx.THROW)
        val land = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAX_STANDARD.toDouble(), landHit = land, delay = delay, hitType = HitType.RANGE)
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
        npc.animate(ANIM_KICK)
        if (target is Player) target.playSound(BreachMonsters.SFX_HUMAN_ATTACK)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAX_STANDARD.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
    }

    /** Melee reach of a size-1 npc: a cardinal neighbour on the same plane. */
    fun isAdjacent(a: Tile, b: Tile): Boolean = a.height == b.height && kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.z - b.z) == 1

    /** The three landing tiles: the target's tile first, then two others within 2, then the two split-book tiles (never the archaeologist's own tile). */
    fun landingTiles(npc: Npc, target: Pawn, pick: (List<Tile>) -> List<Tile>): List<Tile> {
        val centre = Tile(target.tile)
        val around =
            (-2..2).flatMap { dx -> (-2..2).map { dz -> centre.transform(dx, dz) } }
                .filter { it != centre && it != npc.tile }
        return listOf(centre) + pick(around).take(4)
    }

    /** A 3x3 explosion around [centre] hits every player standing in it (OSRS Wiki: "explode in a 3x3 area"). */
    fun inBlast(centre: Tile, tile: Tile): Boolean =
        tile.height == centre.height && kotlin.math.abs(tile.x - centre.x) <= 1 && kotlin.math.abs(tile.z - centre.z) <= 1

    private fun special(npc: Npc, target: Pawn) {
        npc.forceChat(SHOUT_SPECIAL)
        npc.animate(ANIM_THROW)
        val world = npc.world
        val life = WildernessBossLife.token(npc)
        val all = landingTiles(npc, target) { it.shuffled() }
        val first = all.take(3)
        val split = all.drop(3)
        first.forEach { tile ->
            world.spawn(npc.createProjectile(tile, GFX_BOOK_SPECIAL, startHeight = 40, endHeight = 0, delay = 30, angle = 16, lifespan = 90))
        }
        world.queue {
            wait(SPECIAL_TICKS)
            if (!WildernessBossLife.active(npc, life) || !explode(npc, first)) return@queue
            // The book on the target's tile splits into two more (Kronos SECONDARY_MAGIC_PROJECTILE from the first landing tile).
            split.forEach { tile ->
                world.spawn(
                    gg.rsmod.game.model.entity.Projectile.Builder()
                        .setTiles(start = first[0], target = tile)
                        .setGfx(gfx = GFX_BOOK_SPECIAL)
                        .setHeights(startHeight = 0, endHeight = 0)
                        .setSlope(angle = 32, steepness = 64)
                        .setTimes(delay = 0, lifespan = 90)
                        .build(),
                )
            }
            wait(SPECIAL_TICKS)
            if (WildernessBossLife.active(npc, life)) explode(npc, split)
        }
    }

    /** Explosions on [tiles]; false once the archaeologist is gone (the rest of the special is cancelled). */
    private fun explode(npc: Npc, tiles: List<Tile>): Boolean {
        val world = npc.world
        tiles.forEach { world.spawn(TileGraphic(it, GFX_EXPLOSION, 0)) }
        if (npc.isDead() || !npc.isSpawned()) return false
        val victims = ArrayList<Player>()
        world.players.forEach { p -> if (p.isOnline && !p.isDead() && tiles.any { inBlast(it, p.tile) }) victims += p }
        victims.forEach { p ->
            p.playSound(Sfx.EXPLOSION)
            npc.dealHit(target = p, maxHit = MAX_SPECIAL.toDouble(), landHit = true, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
        }
        return true
    }
}
