package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import java.lang.ref.WeakReference

/**
 * N522 bundle 2 (owner 2026-11-01 "alle osrs wilderness bosses ... volledig"): the OSRS Scorpia (OSRS npc 6615, imported as
 * [Npcs.SCORPIA]) in the OSRS Scorpion Pit (map square 12961, OsrsRegionImportTool batch scorpia-pit).
 *
 * Mechanics - OSRS Wiki "Scorpia", "Scorpia's guardian", "Scorpia's offspring (monster)" (2026-11-01):
 *  - melee stab, max 16, attack speed 4; poisonous, starting at 20; "her melee attacks drain 2 prayer points if you have Protect from
 *    Melee active, whether or not the attack hits".
 *  - guardians: "Two appear on the next attack after Scorpia reaches 99 health or lower, even if you splash"; each heals her for 8
 *    every 1.8 seconds (3 ticks) from up to 3 tiles away; "If they do not heal Scorpia in 15 seconds (25 ticks), they will despawn";
 *    killed guardians do not respawn, even if she heals back to full. They never attack.
 *  - offspring: ranged, max 2, attack speed 4, poisonous starting at 6, "incredibly accurate" (ranged attack bonus +900).
 * Ids and the rest (no Jagex documentation): Kronos-184 bosses/scorpia (team/dossiers/N522-SCORPIA-DONOR.txt): poison chance 1/6
 * (Scorpia) and 1/8 (offspring) per damaging hit, guardians spawn inside her 5x5 with spotanim 144 at height 20, heal projectile 109,
 * offspring projectile 663 (range 4). OsrsFxImportTool wildy-bosses-2 (tx-20261101-202238): 109 -> 3135, 144 -> 3199, 663 -> 3200;
 * OsrsNpcImportTool wildy-bosses-2 (tx-20261101-202308): AnimationID SCORPION_UPDATE_ATTACK_TAIL 6254 -> 16122, _DEFEND 6255 -> 16123,
 * _DEATH 6256 -> 16124, SMALL_SCORPION_UPDATE_DEFEND 6259 -> 16125, _DEATH 6260 -> 16126, _ATTACK 6261 -> 16127.
 */
object ScorpiaCombat : CombatScript() {
    override val ids = intArrayOf(Npcs.SCORPIA)

    const val ANIM_ATTACK = 16122 // OSRS 6254
    const val ANIM_DEFEND = 16123 // OSRS 6255
    const val ANIM_DEATH = 16124 // OSRS 6256
    const val ANIM_SMALL_DEFEND = 16125 // OSRS 6259
    const val ANIM_SMALL_DEATH = 16126 // OSRS 6260
    const val ANIM_SMALL_ATTACK = 16127 // OSRS 6261

    const val GFX_HEAL_PROJECTILE = 3135 // OSRS 109
    const val GFX_GUARDIAN_SPAWN = 3199 // OSRS 144
    const val GFX_OFFSPRING_PROJECTILE = 3200 // OSRS 663

    const val MAX_HIT = 16
    const val POISON = 20
    const val POISON_ROLL = 6
    const val PRAYER_DRAIN = 2

    /** "Two appear on the next attack after Scorpia reaches 99 health or lower". */
    const val GUARDIAN_THRESHOLD = 99
    const val GUARDIANS = 2
    const val HEAL = 8
    const val HEAL_TICKS = 3
    const val HEAL_RANGE = 3
    const val GUARDIAN_IDLE_TICKS = 25

    const val OFFSPRING_MAX_HIT = 2
    const val OFFSPRING_POISON = 6
    const val OFFSPRING_POISON_ROLL = 8
    const val OFFSPRING_RANGE = 4

    /** Scorpia reached [GUARDIAN_THRESHOLD]: the next attack against her summons the guardians. */
    val GUARDIANS_ARMED = AttributeKey<Boolean>()

    /** The guardians of this life were summoned (they never come back until she respawns). */
    val GUARDIANS_SUMMONED = AttributeKey<Boolean>()

    /** The living guardians of this Scorpia. */
    val GUARDIAN_LIST = AttributeKey<MutableList<Npc>>()

    /** A guardian's Scorpia. */
    val GUARDIAN_OF = AttributeKey<WeakReference<Npc>>()

    /** The cycle a guardian last was within heal range of Scorpia (its 25-tick despawn clock). */
    val LAST_IN_RANGE = AttributeKey<Int>()

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                melee(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.STAB, WeaponStyle.ACCURATE)
        npc.animate(ANIM_ATTACK)
        // "whether or not the attack hits" - drained when the swing starts.
        if (target is Player && target.isProtectedFrom(CombatClass.MELEE)) drainPrayer(target)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAX_HIT.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE, onHit = { hit ->
            if (dealtDamage(hit) && npc.world.random(POISON_ROLL - 1) == 0) target.poison(POISON)
        })
    }

    fun drainPrayer(player: Player) {
        player.alterPrayerPoints(-PRAYER_DRAIN)
    }

    fun dealtDamage(hit: PawnHit): Boolean = hit.landed && hit.hit.hitmarks.sumOf { it.damage } > 0

    // ------------------------------------------------------------------ guardians

    /**
     * Every attack against Scorpia (a splash included) - called from the shared hit path ([gg.rsmod.plugins.content.combat.dealHit])
     * after the hit's damage: the attack that takes her to 99 or lower arms the summon, the next one summons the two guardians.
     */
    fun onAttacked(scorpia: Npc) {
        if (scorpia.isDead() || !scorpia.isSpawned() || scorpia.attr[GUARDIANS_SUMMONED] == true) return
        if (scorpia.attr[GUARDIANS_ARMED] == true) {
            summonGuardians(scorpia)
        } else if (scorpia.getCurrentLifepoints() <= GUARDIAN_THRESHOLD) {
            scorpia.attr[GUARDIANS_ARMED] = true
        }
    }

    /** A new life (spawn and every respawn): no guardians, nothing armed. */
    fun reset(scorpia: Npc) {
        scorpia.attr.remove(GUARDIANS_ARMED)
        scorpia.attr.remove(GUARDIANS_SUMMONED)
        removeGuardians(scorpia)
    }

    /** The tiles inside her 5x5 (Kronos: guardians appear under her). */
    fun spawnTiles(scorpia: Npc): List<Tile> {
        val size = scorpia.getSize()
        return (0 until size).flatMap { dx -> (0 until size).map { dz -> scorpia.tile.transform(dx, dz) } }
    }

    /** Chebyshev distance from [tile] to the nearest tile of an npc of [size] standing on [origin]. */
    fun distanceToArea(tile: Tile, origin: Tile, size: Int): Int {
        if (tile.height != origin.height) return Int.MAX_VALUE
        val dx = maxOf(origin.x - tile.x, 0, tile.x - (origin.x + size - 1))
        val dz = maxOf(origin.z - tile.z, 0, tile.z - (origin.z + size - 1))
        return maxOf(dx, dz)
    }

    fun inHealRange(guardian: Npc, scorpia: Npc): Boolean = distanceToArea(guardian.tile, scorpia.tile, scorpia.getSize()) <= HEAL_RANGE

    private fun summonGuardians(scorpia: Npc) {
        scorpia.attr[GUARDIANS_SUMMONED] = true
        scorpia.attr.remove(GUARDIANS_ARMED)
        val world = scorpia.world
        val list = ArrayList<Npc>()
        val tiles = spawnTiles(scorpia).shuffled()
        repeat(GUARDIANS) { i ->
            val guardian = Npc(Npcs.SCORPIAS_GUARDIAN, Tile(tiles[i % tiles.size]), world)
            guardian.respawns = false
            guardian.walkRadius = 0
            guardian.attr[GUARDIAN_OF] = WeakReference(scorpia)
            guardian.attr[LAST_IN_RANGE] = world.currentCycle
            if (world.spawn(guardian)) {
                guardian.graphic(GFX_GUARDIAN_SPAWN, height = 20)
                list += guardian
                startHealing(guardian, scorpia)
            }
        }
        scorpia.attr[GUARDIAN_LIST] = list
    }

    fun removeGuardians(scorpia: Npc) {
        val list = scorpia.attr[GUARDIAN_LIST] ?: return
        scorpia.attr.remove(GUARDIAN_LIST)
        list.forEach { if (it.isSpawned()) scorpia.world.remove(it) }
    }

    /** Heal every [HEAL_TICKS] while within [HEAL_RANGE], else walk to her; gone after [GUARDIAN_IDLE_TICKS] out of range. */
    private fun startHealing(guardian: Npc, scorpia: Npc) {
        val world = guardian.world
        world.queue {
            wait(1)
            while (guardian.isSpawned() && !guardian.isDead()) {
                if (!scorpia.isSpawned() || scorpia.isDead()) {
                    world.remove(guardian)
                    break
                }
                if (inHealRange(guardian, scorpia)) {
                    guardian.attr[LAST_IN_RANGE] = world.currentCycle
                    if (scorpia.getCurrentLifepoints() < scorpia.getMaximumLifepoints()) heal(guardian, scorpia)
                } else if (world.currentCycle - (guardian.attr[LAST_IN_RANGE] ?: world.currentCycle) >= GUARDIAN_IDLE_TICKS) {
                    world.remove(guardian)
                    scorpia.attr[GUARDIAN_LIST]?.remove(guardian)
                    break
                } else {
                    guardian.walkTo(scorpia.getCentreTile())
                }
                wait(HEAL_TICKS)
            }
        }
    }

    private fun heal(guardian: Npc, scorpia: Npc) {
        guardian.faceTile(scorpia.getCentreTile())
        guardian.world.spawn(
            guardian.createProjectile(scorpia, GFX_HEAL_PROJECTILE, startHeight = 43, endHeight = 31, angle = 10, steepness = 16, delay = 51, lifespan = 51 + 56),
        )
        scorpia.setCurrentLifepoints((scorpia.getCurrentLifepoints() + HEAL).coerceAtMost(scorpia.getMaximumLifepoints()))
    }

    // ------------------------------------------------------------------ offspring

    object Offspring : CombatScript() {
        override val ids = intArrayOf(Npcs.SCORPIAS_OFFSPRING)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = OFFSPRING_RANGE, projectile = true)) {
                    ranged(npc, target)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        private fun ranged(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            npc.animate(ANIM_SMALL_ATTACK)
            val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            val flight = 5 * Combat.tileDistance(npc.tile, target.tile)
            npc.world.spawn(
                npc.createProjectile(target, GFX_OFFSPRING_PROJECTILE, startHeight = 16, endHeight = 20, angle = 0, steepness = 18, delay = 20, lifespan = 20 + 30 + flight),
            )
            val land = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
            npc.dealHit(target = target, maxHit = OFFSPRING_MAX_HIT.toDouble(), landHit = land, delay = delay, hitType = HitType.RANGE, onHit = { hit ->
                if (dealtDamage(hit) && npc.world.random(OFFSPRING_POISON_ROLL - 1) == 0) target.poison(OFFSPRING_POISON)
            })
        }
    }
}
