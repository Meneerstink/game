package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.ANIM_CALL_MINION
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.ANIM_MAGIC
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.ANIM_NO_ESCAPE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.ANIM_SIPHON
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.ANIM_SPECIAL
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.CENTRE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_BLOOD_HIT
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_DARKNESS
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_ICE_HIT
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_ICE_PRISON
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_NO_ESCAPE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_PLAYER_SACRIFICE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_SHADOW_TRAP
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_SIPHON
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.NO_ESCAPE_TILES
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_BLOOD
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_MAGIC
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_SHADOW
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_SMOKE_ZAROS
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.Phase
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Nex's per-phase attack rotation, ported from the Novite donor NexCombat with 2011 mechanics:
 *
 * - Smoke: magic barrages (poison chance), "Let the virus flow through you!" (spreading cough),
 *   pull attack on the farthest player, melee, and "There is... NO ESCAPE!" rush.
 * - Shadow: distance-scaled shadow bolts, "Fear the shadow!" traps, "Embrace darkness!".
 * - Blood: blood barrages healing Nex, "A siphon will solve this!" reavers, "I demand a blood
 *   sacrifice!".
 * - Ice: ice barrages that freeze, "Die now, in a prison of ice!", "Contain this!" stalagmites.
 * - Zaros: all attacks with Soul Split, Turmoil and Deflect Melee.
 */
object NexCombatScript : CombatScript() {
    override val ids = intArrayOf(gg.rsmod.plugins.api.cfg.Npcs.NEX)

    /** C4: Darkan Nex.attackCount (1 at spawn, 0 at every phase change, +1 per attack). */
    var attackCount = NexDarkanRules.SPAWN_ATTACK_COUNT
        private set
    private var countPhase: Phase? = null

    /** Darkan Nex.followTarget: the next auto attack walks in for melee (re-rolled 1 in 2 after every auto attack). */
    private var followTarget = false
    private var lastAttackCycle = 0
    private var shadowTrapsActive = false
    private var busy = false

    /** The running No Escape rush (world-owned, see [noEscape]); [busy] is set while it runs. */
    private var rush: Any? = null

    /**
     * N25 (LC10 2026-10-16: after a pull Nex stood still for minutes). The old loop `while (canEngage && isAttackDelayReady ...)` +
     * `wait(attackSpeed)` woke one tick before its own ATTACK_DELAY ran out and quit after every attack (class 191, N24/N84): only a
     * player's hit made her attack again, so a frozen, pulled or passive player was never attacked. Ported Darkan's per-tick npc
     * combat process (NPCCombat.java:48-61: the delay counts down each tick, an attack fires when it is 0) as for the strykewyrm
     * (N24, 99a3ecc32). Darkan's Nex fights every player in her arena (aggressive, Nex.getPossibleTargets): a lost target is
     * replaced by another arena player instead of ending her fight.
     */
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        val world = npc.world
        val enc = NexEncounter
        var exit = "no-target"

        while (true) {
            if (!enc.fightActive || enc.nex !== npc) { exit = "fight-over"; break }
            // C4: the No Escape rush owns Nex while it runs. Its start is the arena edge itself (Darkan NoEscape.java:58), 16 tiles
            // from a player on the far side - beyond canEngage's 15 - so picking a target mid-rush ended her fight for good.
            if (busy) {
                it.wait(1)
                continue
            }
            val current = npc.getCombatTarget()
            val target: Pawn =
                if (current != null && npc.canEngageCombat(current)) {
                    current
                } else {
                    enc.players().filter { npc.canEngageCombat(it) }.randomOrNull()?.also { npc.setCombatTarget(it) } ?: break
                }
            if (enc.awaitingMinion || enc.intro || enc.siphoning || busy || !npc.isAttackDelayReady()) {
                it.wait(1)
                continue
            }
            npc.facePawn(target)
            // C4: Darkan Nex.java:73/336 - the counter starts at 1 at spawn and is 0 again at every phase change.
            if (countPhase != enc.phase) {
                // 0 at every phase change - also when the phase changed before her first attack of the old phase (a fast kill of
                // a minion's phase: with `countPhase != null` the Shadow phase began at 1 and Embrace darkness never came).
                if (enc.phase != Phase.SMOKE) attackCount = NexDarkanRules.PHASE_ATTACK_COUNT
                countPhase = enc.phase
            }
            enc.consumeFirstStageAttack() // Darkan has no separate first attack: the schedule starts at count 1.
            val count = attackCount
            // C4: Darkan NexCombat.java:42-76 - the special is a pure function of (phase, attack count); Drag on Utils.random(4) == 0.
            val special = NexDarkanRules.special(enc.phase, count, world.random(3) == 0)
            if (special != null) {
                attackCount++ // Nex.performAttack -> incrementAttack
                lastAttackCycle = world.currentCycle // Nex.incrementAttack -> refreshTicksAttacked (shouldStopMeleeing)
                traceAttack(npc, count, special.name)
                performSpecial(npc, target, special)
            } else {
                if (!autoAttack(it, npc, target)) {
                    it.wait(1)
                    continue
                }
                traceAttack(npc, count, "AUTO")
            }
            npc.postAttackLogic(target)
            // Darkan: Blood sacrifice, Contain this and Ice prison return attackSpeed * 2.
            if (special != null && special.speedFactor > 1) {
                npc.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] = npc.combatDef.attackSpeed * special.speedFactor
            }
            it.wait(1)
        }
        // N25: why Nex stopped attacking - breach trace (watched npc ids).
        if (gg.rsmod.game.model.BreachTrace.watches(npc.id)) {
            gg.rsmod.game.model.BreachTrace.log("combat.exit") { "npc=${npc.id}#${npc.index} script=nex reason=$exit cycle=${world.currentCycle}" }
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** Zaros phase, Soul Split form only (Novite NexCombat.sendSoulSplit: id 13448): Nex heals from the damage she deals. */
    private fun soulSplit(npc: Npc, damage: Int) {
        if (NexEncounter.phase == Phase.ZAROS && NexZarosForms.soulSplitActive(NexZarosForms.formOf(npc)) && damage > 0) {
            NexEncounter.heal(npc, soulSplitAmount(damage))
        }
    }

    /** Novite's Player.sendSoulSplit heals one fifth of the incoming damage. */
    fun soulSplitAmount(damage: Int): Int = damage / 5

    private fun traceAttack(npc: Npc, count: Int, what: String) {
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            "nex attack npc ${npc.id}#${npc.index} phase=${NexEncounter.phase} count=$count special=$what"
        }
    }

    private fun performSpecial(npc: Npc, target: Pawn, special: NexDarkanRules.Special) {
        when (special) {
            NexDarkanRules.Special.DRAG -> pullAttack(npc)
            NexDarkanRules.Special.NO_ESCAPE -> noEscape(npc)
            NexDarkanRules.Special.VIRUS -> virusAttack(npc, target)
            NexDarkanRules.Special.EMBRACE_DARKNESS -> embraceDarkness(npc)
            NexDarkanRules.Special.SHADOW_TRAPS -> shadowTraps(npc)
            NexDarkanRules.Special.SIPHON -> siphon(npc, target)
            NexDarkanRules.Special.BLOOD_SACRIFICE -> bloodSacrifice(npc, target)
            NexDarkanRules.Special.CONTAIN_THIS -> {
                npc.forceChat("Contain this!")
                NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_ICE_BARRICADE)
                npc.animate(ANIM_SPECIAL)
                npc.stopMovement()
                NexEncounter.icePrison(npc)
            }
            NexDarkanRules.Special.ICE_PRISON -> icePrison(npc, target)
        }
    }

    /**
     * Darkan NexCombat.autoAttack (:78-142). False = Nex is still walking in for melee (Darkan returns 0: no attack this tick).
     */
    private suspend fun autoAttack(it: QueueTask, npc: Npc, target: Pawn): Boolean {
        val world = npc.world
        val phase = NexEncounter.phase
        if (followTarget) {
            // Darkan inMeleeRange / calcFollow: walk in (the walk itself suspends until she borders the target or gives up).
            if (!npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                if (world.currentCycle - lastAttackCycle > NexDarkanRules.STOP_MELEEING_TICKS) followTarget = false
                return false
            }
            followTarget = world.random(1) == 0
            meleeAttack(npc, target)
        } else {
            followTarget = world.random(1) == 0
            when (phase) {
                Phase.SMOKE -> magicAttack(npc, ANIM_CALL_MINION, poison = true)
                Phase.SHADOW -> shadowAttack(npc)
                Phase.BLOOD -> bloodAttack(npc, target)
                Phase.ICE -> iceAttack(npc)
                Phase.ZAROS -> magicAttack(npc, ANIM_CALL_MINION, poison = false)
            }
        }
        // Darkan NexCombat.java:138-139: in the Zaros phase the prayer form switches on every third attack count.
        if (phase == Phase.ZAROS && NexDarkanRules.zarosSwitch(attackCount)) {
            NexZarosForms.apply(npc, NexZarosForms.next(NexZarosForms.formOf(npc)))
        }
        attackCount++
        lastAttackCycle = world.currentCycle
        return true
    }

    /**
     * One Nex hit (Darkan getMaxHit + Player hit processing): the accuracy roll ignores the "npc never lands on a protection prayer"
     * rule, a protection PRAYER cuts the max hit to 60% (Nex.java:305-320, Nex only); a Deflect curse keeps its own 60%
     * (AncientCurses.deflectDamageTaken in dealHit). BLOOD phase heals 15% of melee/magic hits above 1.5 (Nex.java:325-327).
     */
    private fun nexHit(
        npc: Npc,
        target: Pawn,
        style: CombatClass,
        max: Double,
        delay: Int,
        hitType: HitType,
        onHit: (Int) -> Unit = {},
    ) {
        val accuracy =
            when (style) {
                CombatClass.MAGIC -> MagicCombatFormula.getUnprotectedAccuracy(npc, target)
                CombatClass.RANGED -> RangedCombatFormula.getUnprotectedAccuracy(npc, target)
                else -> MeleeCombatFormula.getUnprotectedAccuracy(npc, target)
            }
        val land = accuracy >= npc.world.randomDouble()
        val prayer = target.isProtectedFrom(style) && !AncientCurses.deflects(target, style)
        val realMax = if (prayer) max * NexDarkanRules.PROTECTION_MULTIPLIER else max
        npc.dealHit(target = target, maxHit = realMax, landHit = land, delay = delay, hitType = hitType, onHit = { hit ->
            val damage = hit.hit.hitmarks.sumOf { it.damage }
            soulSplit(npc, damage)
            if (style != CombatClass.RANGED) {
                val heal = NexDarkanRules.bloodHeal(NexEncounter.phase, damage)
                if (heal > 0) NexEncounter.heal(npc, heal)
            }
            onHit(damage)
        })
    }

    /* ------------------------------- shared ------------------------------- */

    /** Darkan NexCombat.java:89-91: melee 360 (x10) in every phase, anim 6354. */
    private fun meleeAttack(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        nexHit(npc, target, CombatClass.MELEE, NexDarkanRules.MELEE_MAX, 1, HitType.MELEE)
    }

    /** Darkan NexCombat.java:94-101 (smoke: 250, poison 1 in 6 on damage) and :129-134 (Zaros: 350) - every arena player. */
    private fun magicAttack(npc: Npc, anim: Int, poison: Boolean) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(anim)
        if (poison) npc.graphic(1214)
        for (player in NexEncounter.players()) {
            npc.world.spawn(npc.createProjectile(player, PROJ_SMOKE_ZAROS, ProjectileType.MAGIC))
            nexHit(npc, player, CombatClass.MAGIC, NexDarkanRules.magicMax(NexEncounter.phase), 2, HitType.MAGIC) { damage ->
                player.graphic(NexEncounter.GFX_MAGIC_IMPACT)
                if (poison && damage > 0 && npc.world.random(NexDarkanRules.EFFECT_ROLL_BOUND) == 0) player.poison(8)
            }
        }
    }

    /** Pulls the farthest player to Nex, disabling protection prayers and stunning them. */
    private fun pullAttack(npc: Npc) {
        val player = NexEncounter.players().maxByOrNull { it.tile.getDistance(npc.tile) } ?: return
        // N25: the fight the player was in when he was pulled. Darkan (InteractionManager/PlayerCombat) and Novite
        // (sendPullAttack: actionManager delay, action kept) keep the victim's combat through the 3-tick lock; our full lock ends
        // the player's combat loop (combat.plugin.kts cycle: !canAttack -> Combat.reset), so it is handed back when the lock ends.
        val fighting = player.getCombatTarget()
        player.stopMovement()
        // Darkan Drag.java:47-48: Nex animates (6986) and only SETS her combat target. The old `npc.attack(player)` ran
        // Pawn.attack -> interruptQueues on Nex, which killed this running combat loop; the fresh loop then quit on the still
        // running attack delay and Nex stood still until a player hit her again (LC10).
        npc.animate(ANIM_MAGIC)
        npc.setCombatTarget(player)
        npc.facePawn(player)
        // She drags the player to her, so she stands still: a walk queued towards the old target would carry her away from
        // the tile he lands on (NexPullFightTests: pulled beside her, then she walked three tiles on).
        npc.stopMovement()
        // Keep the delayed pull owned by the player. A world queue outlives the player death/
        // logout queue and could otherwise leave a stale lock or apply the special after the
        // victim had already respawned. STRONG also prevents an open standard menu from pausing
        // the action while its full lock is active.
        player.lockingQueue(priority = TaskPriority.STRONG) {
            wait(1)
            if (!npc.isSpawned() || npc.isDead() || !player.isOnline || player.isDead() || !NexEncounter.inArena(player.tile)) {
                return@lockingQueue
            }
            player.animate(14388)
            player.graphic(GFX_PLAYER_SACRIFICE)
            wait(1)
            if (!npc.isSpawned() || npc.isDead() || !player.isOnline || player.isDead() || !NexEncounter.inArena(player.tile)) {
                return@lockingQueue
            }
            besideBody(npc)?.let { player.moveTo(it) }
            player.message("You've been injured and you cannot use ${if (AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT) "protective curses" else "protective prayers"}!")
            // Darkan Drag.java:55-57: protection prayers blocked 12 ticks + "You're stunned." (the 3-tick lock), no freeze.
            NexPrayer.disableProtection(player, NexDarkanRules.PROTECTION_BLOCK_TICKS)
            player.message("You're stunned.")
            // The stun ends with this task (Darkan lock(3) / Novite lock(3)): the queue releases the lock when the task completes, and
            // the player's queue then runs the combat loop queued here in the same tick, unlocked. The player fights on against
            // whatever he was fighting, without a new click - unless a live combat loop of his still holds a target.
            if (fighting != null && fighting.isAlive() && (fighting !is Npc || fighting.isSpawned()) && player.getCombatTarget() == null) {
                player.setCombatTarget(fighting)
                player.world.plugins.executeCombat(player)
            }
        }
    }

    /**
     * A free tile touching Nex's body (Darkan Drag.java:46 `nex.getNearestTeleTile(1)` - a free tile next to Nex). The old
     * `findRandomTileAround(centre, 2)` also picked the nine tiles under her 3x3 body (class test: pulled to 2925,5204 with Nex
     * on 2924,5202), from where no melee attack can reach her. The four diagonal corners are left out too (no melee reach).
     */
    private fun besideBody(npc: Npc): Tile? {
        val size = npc.getSize()
        val ring = ArrayList<Tile>()
        for (dx in -1..size) {
            for (dz in -1..size) {
                if (dx in 0 until size && dz in 0 until size) continue
                if ((dx == -1 || dx == size) && (dz == -1 || dz == size)) continue
                val tile = npc.tile.transform(dx, dz)
                if (!npc.world.collision.isClipped(tile)) ring.add(tile)
            }
        }
        return if (ring.isEmpty()) null else ring[npc.world.random(ring.size - 1)]
    }

    /**
     * "There is... NO ESCAPE!": rush from a random edge through the centre, hitting anyone in the path.
     *
     * N25 round 2 (AUDIT 10-17): the rush ran inside Nex's own combat queue and cleared [busy] only on its last line, so any
     * Pawn.attack on Nex mid-rush (npc retaliation Combat.kt:275, a phase change's NexEncounter.engageRandomPlayer) killed it with
     * busy=true for ever - her loop waits on busy, she never attacked again, not even in the next fight. Darkan NoEscape.java:42-79
     * (and Novite NexCombat.java:532-560) run the rush in a WORLD task and return Nex's attack speed: her combat cycle cannot strand
     * it. The rush is world-owned here too, keyed by its own [rush] token (a new fight's [reset] retires an old rush), and every step
     * checks that the fight is still this Nex's.
     */
    private fun noEscape(npc: Npc) {
        val token = Any()
        rush = token
        busy = true
        npc.forceChat("There is...")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_NO_ESCAPE_START)
        npc.animate(ANIM_NO_ESCAPE)
        npc.graphic(GFX_NO_ESCAPE)
        val world = npc.world
        world.queue {
            try {
                wait(1)
                if (!rushing(token, npc)) return@queue
                val index = world.random(NO_ESCAPE_TILES.size - 1)
                val start = NO_ESCAPE_TILES[index]
                // C4: Darkan NoEscape.java:58 nex.tele(dir) - the edge tile itself, no -1 (Darkan's npc tile is its SW tile, as ours).
                npc.moveTo(start)
                npc.forceChat("NO ESCAPE!")
                NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_NO_ESCAPE_HIT)
                val vertical = index == 0 || index == 2
                val victims = NexEncounter.players().filter { player ->
                    if (vertical) {
                        player.tile.x in (CENTRE.x - 2)..(CENTRE.x + 2) && player.tile.z in minOf(start.z, CENTRE.z)..maxOf(start.z, CENTRE.z)
                    } else {
                        player.tile.z in (CENTRE.z - 2)..(CENTRE.z + 2) && player.tile.x in minOf(start.x, CENTRE.x)..maxOf(start.x, CENTRE.x)
                    }
                }
                wait(1)
                if (!rushing(token, npc)) return@queue
                for (player in victims) {
                    // A victim who died, logged out or left the arena in the meantime is not hit or moved back in.
                    if (!player.isOnline || player.isDead() || !NexEncounter.inArena(player.tile)) continue
                    player.animate(10070)
                    val damage = world.random(NexDarkanRules.NO_ESCAPE_DAMAGE.first..NexDarkanRules.NO_ESCAPE_DAMAGE.last)
                    player.hit(damage, HitType.REGULAR_HIT)
                    soulSplit(npc, damage)
                    // C4: Darkan NoEscape.java:66-67 forceMove(player.getTile(), 10070, ...) - the victim is knocked back on his
                    // own tile, never moved (the earlier random tile around the centre could put him under Nex's body, where her
                    // melee walk-in found no route: 22-tick gaps in GREEN try 1).
                }
                wait(1)
                if (!rushing(token, npc)) return@queue
                // C4: Darkan NoEscape.java:68 nex.tele(center) with center 2924,5202 = her spawn tile (NexArena.java:151). The old
                // CENTRE-1 put her on 2923,5201, a blocked tile: from there no route exists and her melee walk-in never moved.
                npc.moveTo(Tile(CENTRE))
                wait(1)
            } finally {
                if (rush === token) {
                    rush = null
                    busy = false
                }
            }
        }
    }

    /** The rush [token] is still the live one and its Nex is still fighting (not dead, removed or replaced by a new fight). */
    private fun rushing(token: Any, npc: Npc): Boolean =
        rush === token && NexEncounter.fightActive && NexEncounter.nex === npc && npc.isSpawned() && !npc.isDead()

    /* ------------------------------- smoke ------------------------------- */

    /**
     * C4: Darkan Virus.java:34-58 - "Let the virus flow through you." (anim 6987, voice 3296) infects Nex's TARGET: he and every arena
     * player straight beside him cough once, take 0-10 true damage and lose 5% of Magic, Ranged, Attack, Strength and Defence. (Was the
     * Novite version: the farthest player + radius 2, '!' text, anim 6986 and a 20-tick cough infection that Darkan does not have.)
     */
    private fun virusAttack(npc: Npc, target: Pawn) {
        npc.forceChat("Let the virus flow through you.")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_VIRUS)
        npc.animate(ANIM_CALL_MINION)
        val infected = Tile(target.tile)
        NexEncounter.players().filter { NexDarkanRules.virusReaches(infected, it.tile) }.forEach { player ->
            player.forceChat("*Cough*")
            player.hit(npc.world.random(NexDarkanRules.VIRUS_DAMAGE), HitType.REGULAR_HIT)
            for (stat in VIRUS_STATS) {
                val drain = (player.skills.getCurrentLevel(stat) * NexDarkanRules.VIRUS_DRAIN).toInt()
                if (drain > 0) player.skills.decrementCurrentLevel(stat, drain, capped = false)
            }
        }
    }

    /** Virus.java:48-52: Constants.MAGIC, RANGE, ATTACK, STRENGTH, DEFENSE. */
    private val VIRUS_STATS = intArrayOf(Skills.MAGIC, Skills.RANGED, Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE)

    /* ------------------------------- shadow ------------------------------- */

    /** Darkan NexCombat.java:103-111: ranged arrows only to players within 10 tiles, max 800 - d*800/11 (x10). */
    private fun shadowAttack(npc: Npc) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ANIM_CALL_MINION)
        for (player in NexEncounter.players()) {
            val max = NexDarkanRules.shadowMax(player.tile.getDistance(npc.tile)) ?: continue
            npc.world.spawn(npc.createProjectile(player, PROJ_SHADOW, ProjectileType.ARROW))
            nexHit(npc, player, CombatClass.RANGED, max, 2, HitType.RANGE) { player.graphic(NexEncounter.GFX_MAGIC_IMPACT) }
        }
    }

    private fun shadowTraps(npc: Npc) {
        shadowTrapsActive = true
        npc.forceChat("Fear the shadow!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_SHADOW_TRAPS)
        npc.animate(ANIM_SPECIAL)
        npc.graphic(GFX_ICE_PRISON)
        val world = npc.world
        val tiles = NexEncounter.players().map { Tile(it.tile) }.distinct()
        tiles.forEach { world.spawnTemporaryObject(DynamicObject(Objs.SHADOW_57261, 10, 0, it), 4) }
        world.queue {
            wait(3)
            tiles.forEach { tile ->
                world.spawn(gg.rsmod.game.model.TileGraphic(tile, GFX_SHADOW_TRAP, 0))
                NexEncounter.players().filter { it.tile == tile }.forEach { player ->
                    player.hit(40 + world.random(40), HitType.REGULAR_HIT)
                }
            }
            wait(3)
            shadowTrapsActive = false
        }
    }

    private fun embraceDarkness(npc: Npc) {
        npc.forceChat("Embrace darkness!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_DARKNESS)
        // C4: Darkan EmbraceDarkness.java:36-37 anim 6355 + spotanim 1217; the darkness lasts until the shadow phase ends.
        npc.animate(NexEncounter.ANIM_START)
        npc.graphic(GFX_DARKNESS)
        NexEncounter.applyDarkness(true)
    }

    /* ------------------------------- blood ------------------------------- */

    /** Darkan NexCombat.java:113-115: ONE blood barrage (250, x10) at her target; the 15% heal is the phase rule in [nexHit]. */
    private fun bloodAttack(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        npc.world.spawn(npc.createProjectile(target, PROJ_BLOOD, ProjectileType.MAGIC))
        nexHit(npc, target, CombatClass.MAGIC, NexDarkanRules.MAGIC_MAX, 2, HitType.MAGIC) { damage ->
            if (damage > 0) target.graphic(GFX_BLOOD_HIT)
        }
    }

    private fun siphon(npc: Npc, target: Pawn) {
        NexEncounter.clearReavers()
        npc.forceChat("A siphon will solve this!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_SIPHON)
        npc.animate(ANIM_SIPHON)
        npc.graphic(GFX_SIPHON)
        NexEncounter.siphoning = true
        NexEncounter.spawnReavers(npc)
        npc.world.queue {
            wait(NexDarkanRules.SIPHON_TICKS)
            NexEncounter.siphoning = false
        }
    }

    private fun bloodSacrifice(npc: Npc, target: Pawn) {
        val player = target as? Player ?: return
        npc.forceChat("I demand a blood sacrifice!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_BLOOD_SACRIFICE)
        player.attr[NexEncounter.SACRIFICE_TARGET] = true
        player.graphic(GFX_PLAYER_SACRIFICE)
        player.message("<col=480000>Nex has marked you as a sacrifice, RUN!")
        val world = npc.world
        world.queue {
            wait(npc.combatDef.attackSpeed)
            player.attr.remove(NexEncounter.SACRIFICE_TARGET)
            if (!NexEncounter.fightActive || !player.isOnline || player.isDead() || !npc.isSpawned() || npc.isDead()) return@queue
            // Darkan BloodSacrifice.java:44-52: still closer than 5 tiles -> every target loses 10% of its hitpoints (true damage)
            // and Nex heals by that target's hitpoints.
            if (player.tile.getDistance(npc.tile) < NexDarkanRules.SACRIFICE_SAFE_DISTANCE) {
                player.message("You didn't make it far enough in time - Nex fires a punishing attack!")
                npc.animate(ANIM_CALL_MINION)
                for (victim in NexEncounter.players()) {
                    world.spawn(npc.createProjectile(victim, PROJ_BLOOD, ProjectileType.MAGIC))
                    NexEncounter.heal(npc, victim.getCurrentLifepoints())
                    victim.hit(NexDarkanRules.sacrificeDamage(victim.getCurrentLifepoints()), HitType.REGULAR_HIT, 1)
                }
            }
        }
    }

    /* ------------------------------- ice ------------------------------- */

    /** Darkan NexCombat.java:117-128: ice barrage (250, x10) at every player; 1 in 6 damaging hits freezes 18 s (3 s praying magic). */
    private fun iceAttack(npc: Npc) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        for (player in NexEncounter.players()) {
            npc.world.spawn(npc.createProjectile(player, PROJ_MAGIC, ProjectileType.MAGIC))
            nexHit(npc, player, CombatClass.MAGIC, NexDarkanRules.MAGIC_MAX, 1, HitType.MAGIC) { damage ->
                if (damage > 0 && npc.world.random(NexDarkanRules.EFFECT_ROLL_BOUND) == 0) {
                    player.freeze(NexDarkanRules.iceFreezeTicks(player.isProtectedFrom(CombatClass.MAGIC)))
                    player.graphic(GFX_ICE_HIT)
                }
            }
        }
    }

    /** Darkan IcePrison.java: the prison is cast on her target; the centre tile stays free, 60-80 (x10) after 8 ticks. */
    private fun icePrison(npc: Npc, target: Pawn) {
        npc.forceChat("Die now, in a prison of ice!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_ICE_PRISON)
        npc.animate(ANIM_MAGIC)
        val world = npc.world
        val player = (target as? Player)?.takeIf { NexEncounter.inArena(it.tile) } ?: return
        world.spawn(npc.createProjectile(player, PROJ_MAGIC, ProjectileType.MAGIC))
        val base = Tile(player.tile)
        val prison = ArrayList<DynamicObject>()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val tile = base.transform(dx, dz)
                if (tile == base || world.collision.isClipped(tile)) continue
                val obj = DynamicObject(Objs.STALAGMITE_57263, 10, 0, tile)
                world.spawn(obj)
                prison.add(obj)
            }
        }
        world.queue {
            wait(NexDarkanRules.ICE_PRISON_DELAY)
            if (!player.isOnline || player.isDead()) {
                prison.forEach { world.remove(it) }
                return@queue
            }
            if (player.tile == base) {
                player.message("The centre of the ice prison freezes you to the bone!")
                player.stopMovement()
                player.hit(world.random(NexDarkanRules.ICE_PRISON_DAMAGE.first..NexDarkanRules.ICE_PRISON_DAMAGE.last), HitType.REGULAR_HIT)
            }
            prison.forEach { world.remove(it) }
        }
    }

    fun reset() {
        attackCount = NexDarkanRules.SPAWN_ATTACK_COUNT
        countPhase = null
        followTarget = false
        lastAttackCycle = 0
        shadowTrapsActive = false
        busy = false
        rush = null
    }
}

/**
 * Nex minion magic attacks (Novite Fumus/Umbra/Cruor/GlaciesCombat): each barrages every player
 * with its element — smoke poisons, shadow drains Attack, blood heals the minion, ice freezes.
 */
object NexMinionCombatScript : CombatScript() {
    override val ids = NexEncounter.MINION_IDS

    private const val MAX_HIT = 16.4

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target) && NexEncounter.fightActive) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) {
                npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                npc.animate(NexEncounter.ANIM_MAGIC)
                val (projectile, effect) = when (npc.id) {
                    gg.rsmod.plugins.api.cfg.Npcs.FUMUS -> 386 to { p: Player, d: Int -> if (d > 0 && world.random(4) == 0) { p.poison(8); p.graphic(388) } }
                    gg.rsmod.plugins.api.cfg.Npcs.UMBRA -> 380 to { p: Player, d: Int -> if (d > 0 && world.random(4) == 0) { p.skills.decrementCurrentLevel(Skills.ATTACK, p.skills.getCurrentLevel(Skills.ATTACK) / 10, capped = false); p.graphic(381) } }
                    gg.rsmod.plugins.api.cfg.Npcs.CRUOR -> 374 to { p: Player, d: Int -> if (d > 0) { p.graphic(375); npc.setCurrentLifepoints((npc.getCurrentLifepoints() + d / 4).coerceAtMost(npc.getMaximumLifepoints())) } }
                    else -> 362 to { p: Player, d: Int -> if (d > 0 && world.random(4) == 0) { p.freeze(25); p.graphic(369) } }
                }
                for (player in NexEncounter.players()) {
                    world.spawn(npc.createProjectile(player, projectile, ProjectileType.MAGIC))
                    val land = MagicCombatFormula.getAccuracy(npc, player) >= world.randomDouble()
                    npc.dealHit(target = player, maxHit = MAX_HIT, landHit = land, delay = 1, hitType = HitType.MAGIC, onHit = { hit ->
                        effect(player, hit.hit.hitmarks.sumOf { it.damage })
                    })
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }
}

/** Prayer helpers shared by Nex's prayer-disabling attacks. */
object NexPrayer {
    fun disableProtection(player: Player, cycles: Int) {
        Prayers.deactivateAll(player)
        AncientCurses.deactivateAllCurses(player)
        Prayers.disableOverheads(player, cycles)
    }
}
