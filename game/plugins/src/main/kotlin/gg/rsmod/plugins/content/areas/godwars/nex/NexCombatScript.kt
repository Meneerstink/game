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
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_MAGIC_CAST
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_NO_ESCAPE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_PLAYER_SACRIFICE
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_SHADOW_TRAP
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.GFX_SIPHON
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.NO_ESCAPE_TILES
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_BLOOD
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_MAGIC
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_SHADOW
import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.PROJ_SMOKE_VIRUS
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

    fun nextIceAttackIndex(index: Int): Int = if (index == 10) 0 else (index + 1) % 11

    fun zarosAttackRoll(inMelee: Boolean, randomValue: Int): Int = if (inMelee) randomValue + 3 else randomValue

    private const val MAGIC_MAX = 36.9
    private const val MELEE_MAX = 30.0
    private const val ZAROS_MELEE_MAX = 55.0

    private var bloodCounter = 0
    private var iceCounter = 0
    private var virusCooldown = 0
    private var shadowTrapsActive = false
    private var busy = false

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        val enc = NexEncounter

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady() && enc.fightActive && enc.nex === npc) {
            npc.facePawn(target)
            if (enc.awaitingMinion || enc.intro || enc.siphoning || busy) {
                it.wait(1)
                target = enc.players().randomOrNull() ?: break
                continue
            }
            if (virusCooldown > 0) virusCooldown--
            val inMelee = npc.getFrontFacingTile(target).getDistance(target.tile) <= 1
            npc.prayerIcon = if (enc.phase == Phase.ZAROS) PrayerIcon.SOUL_SPLIT.id else -1

            if (enc.consumeFirstStageAttack()) {
                when (enc.phase) {
                    Phase.SMOKE -> {
                        virusAttack(npc)
                        npc.postAttackLogic(target)
                        it.wait(npc.combatDef.attackSpeed)
                        target = npc.getCombatTarget() ?: enc.players().randomOrNull() ?: break
                        continue
                    }
                    Phase.SHADOW -> if (world.random(6) == 0) shadowTraps(npc) else embraceDarkness(npc)
                    else -> {}
                }
            }

            when (enc.phase) {
                Phase.SMOKE -> when (if (inMelee) world.random(10) else world.random(6)) {
                    0, 1, 2 -> magicAttack(npc, false)
                    3 -> pullAttack(npc)
                    4 -> if (virusCooldown == 0) virusAttack(npc) else magicAttack(npc, true)
                    5, 6 -> magicAttack(npc, true)
                    7, 8, 9 -> meleeAttack(npc, target)
                    else -> noEscape(it, npc)
                }
                Phase.SHADOW -> when (world.random(3)) {
                    0 -> if (!shadowTrapsActive) shadowTraps(npc) else shadowAttack(npc)
                    1, 2 -> shadowAttack(npc)
                    else -> if (world.random(4) == 0) embraceDarkness(npc) else shadowAttack(npc)
                }
                Phase.BLOOD -> {
                    when (bloodCounter) {
                        0 -> siphon(npc, target)
                        5 -> bloodSacrifice(npc, target)
                        else -> bloodAttack(npc, target, inMelee)
                    }
                    bloodCounter = (bloodCounter + 1) % 11
                }
                Phase.ICE -> {
                    val attackIndex = iceCounter
                    when (attackIndex) {
                        0 -> icePrison(npc)
                        6 -> {
                            npc.forceChat("Contain this!")
                            NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_ICE_BARRICADE)
                            npc.animate(ANIM_SPECIAL)
                            npc.graphic(GFX_ICE_PRISON)
                            enc.icePrison(npc)
                        }
                        10 -> {
                            icePrison(npc)
                        }
                        else -> iceAttack(npc, target, inMelee)
                    }
                    iceCounter = nextIceAttackIndex(attackIndex)
                }
                // Matrix's Utils.random(max) is exclusive; World.random(bound) is inclusive.
                // At range the source only selects magic (0..2), while in melee it selects the
                // close-range table (3..13).
                Phase.ZAROS -> when (zarosAttackRoll(inMelee, if (inMelee) world.random(10) else world.random(2))) {
                    0, 1 -> magicAttack(npc, true)
                    2 -> magicAttack(npc, false)
                    3, 4, 5, 6, 7, 8, 9, 10, 11, 12 -> meleeAttack(npc, target)
                    13 -> noEscape(it, npc)
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: enc.players().randomOrNull() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** Zaros phase: Nex heals herself for the damage she deals (Soul Split). */
    private fun soulSplit(npc: Npc, damage: Int) {
        if (NexEncounter.phase == Phase.ZAROS && damage > 0) {
            NexEncounter.heal(npc, soulSplitAmount(damage))
        }
    }

    /** Novite's Player.sendSoulSplit heals one fifth of the incoming damage. */
    fun soulSplitAmount(damage: Int): Int = damage / 5

    private fun magicMax(npc: Npc): Double = if (NexEncounter.phase == Phase.ZAROS) MAGIC_MAX * 1.2 else MAGIC_MAX

    private fun magicHit(npc: Npc, target: Pawn, max: Double, delay: Int, onHit: (Int) -> Unit = {}) {
        val accuracy = MagicCombatFormula.getAccuracy(npc, target)
        val land = accuracy >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max, landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = { hit ->
            val damage = hit.hit.hitmarks.sumOf { it.damage }
            soulSplit(npc, damage)
            onHit(damage)
        })
    }

    /* ------------------------------- shared ------------------------------- */

    private fun meleeAttack(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        val max = if (NexEncounter.phase == Phase.ZAROS) ZAROS_MELEE_MAX else MELEE_MAX
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max, landHit = land, delay = 1, hitType = HitType.MELEE, onHit = { hit ->
            soulSplit(npc, hit.hit.hitmarks.sumOf { it.damage })
        })
    }

    private fun magicAttack(npc: Npc, secondAttack: Boolean) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_CALL_MINION)
        npc.graphic(1214)
        for (player in NexEncounter.players()) {
            npc.world.spawn(npc.createProjectile(player, PROJ_SMOKE_VIRUS, ProjectileType.MAGIC))
            magicHit(npc, player, magicMax(npc), 2) { damage ->
                if (NexEncounter.phase == Phase.SMOKE && !secondAttack && damage > 0 && npc.world.random(5) == 0) {
                    player.poison(8)
                }
            }
        }
    }

    /** Pulls the farthest player to Nex, disabling protection prayers and stunning them. */
    private fun pullAttack(npc: Npc) {
        val player = NexEncounter.players().maxByOrNull { it.tile.getDistance(npc.tile) } ?: return
        player.lock()
        player.stopMovement()
        npc.world.queue {
            wait(1)
            player.animate(14388)
            player.graphic(GFX_PLAYER_SACRIFICE)
            npc.attack(player)
            wait(1)
            player.moveTo(npc.world.findRandomTileAround(npc.getCentreTile(), radius = 2) ?: Tile(npc.tile))
            player.message("You've been injured and you cannot use ${if (AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT) "protective curses" else "protective prayers"}!")
            NexPrayer.disableProtection(player, 5 + npc.world.random(15))
            player.freeze(5 + npc.world.random(5))
            player.unlock()
        }
    }

    /** "There is... NO ESCAPE!": rush from a random edge through the centre, hitting anyone in the path. */
    private suspend fun noEscape(it: QueueTask, npc: Npc) {
        busy = true
        npc.forceChat("There is...")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_NO_ESCAPE_START)
        npc.animate(ANIM_NO_ESCAPE)
        npc.graphic(GFX_NO_ESCAPE)
        it.wait(1)
        val index = npc.world.random(NO_ESCAPE_TILES.size - 1)
        val start = NO_ESCAPE_TILES[index]
        npc.moveTo(Tile(start.x - 1, start.z - 1, 0))
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
        it.wait(1)
        for (player in victims) {
            player.animate(10070)
            val damage = npc.world.random(70)
            player.hit(damage, HitType.REGULAR_HIT)
            soulSplit(npc, damage)
            player.moveTo(npc.world.findRandomTileAround(CENTRE, radius = 2) ?: CENTRE)
        }
        it.wait(1)
        npc.moveTo(Tile(CENTRE.x - 1, CENTRE.z - 1, 0))
        it.wait(1)
        busy = false
    }

    /* ------------------------------- smoke ------------------------------- */

    private fun virusAttack(npc: Npc) {
        virusCooldown = 12 + npc.world.random(4)
        npc.forceChat("Let the virus flow through you!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_VIRUS)
        npc.animate(ANIM_MAGIC)
        val farthest = NexEncounter.players().maxByOrNull { it.tile.getDistance(npc.tile) } ?: return
        NexEncounter.players().filter { it.tile.isWithinRadius(farthest.tile, 2) }.forEach { player ->
            player.forceChat("*Cough*")
            player.hit(npc.world.random(10), HitType.REGULAR_HIT)
            NexEncounter.infect(player)
        }
    }

    /* ------------------------------- shadow ------------------------------- */

    private fun shadowAttack(npc: Npc) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ANIM_CALL_MINION)
        for (player in NexEncounter.players()) {
            val distance = player.tile.getDistance(npc.getCentreTile())
            val max = if (distance <= 10) 40.0 - (distance * 40 / 11) else 30.0 + npc.world.random(7)
            npc.world.spawn(npc.createProjectile(player, PROJ_SHADOW, ProjectileType.ARROW))
            val land = RangedCombatFormula.getAccuracy(npc, player) >= npc.world.randomDouble()
            npc.dealHit(target = player, maxHit = max, landHit = land, delay = 2, hitType = HitType.RANGE, onHit = { hit ->
                soulSplit(npc, hit.hit.hitmarks.sumOf { it.damage })
            })
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
        npc.animate(ANIM_MAGIC)
        npc.graphic(GFX_DARKNESS)
        NexEncounter.applyDarkness(true)
        npc.world.queue {
            wait(50)
            if (NexEncounter.phase == Phase.SHADOW) NexEncounter.applyDarkness(false)
        }
    }

    /* ------------------------------- blood ------------------------------- */

    private fun bloodAttack(npc: Npc, target: Pawn, inMelee: Boolean) {
        if (inMelee && npc.world.random(2) == 0) {
            meleeAttack(npc, target)
            return
        }
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        npc.graphic(GFX_MAGIC_CAST)
        for (player in NexEncounter.players()) {
            npc.world.spawn(npc.createProjectile(player, PROJ_BLOOD, ProjectileType.MAGIC))
            magicHit(npc, player, 29.0, 2) { damage ->
                if (damage > 0) {
                    player.graphic(GFX_BLOOD_HIT)
                    NexEncounter.heal(npc, damage / 4)
                }
            }
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
            wait(8)
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
            if (!NexEncounter.fightActive || npc.isDead()) return@queue
            if (player.tile.isWithinRadius(npc.getCentreTile(), 3)) {
                player.message("You didn't make it far enough in time - Nex fires a punishing attack!")
                npc.animate(ANIM_MAGIC)
                for (victim in NexEncounter.players()) {
                    world.spawn(npc.createProjectile(victim, PROJ_BLOOD, ProjectileType.MAGIC))
                    magicHit(npc, victim, 29.0, 2) { damage ->
                        if (damage > 0) {
                            victim.graphic(GFX_BLOOD_HIT)
                            NexEncounter.heal(npc, damage / 4)
                        }
                        victim.setCurrentPrayerPoints(victim.getCurrentPrayerPoints() / 2)
                    }
                }
            }
        }
    }

    /* ------------------------------- ice ------------------------------- */

    private fun iceAttack(npc: Npc, target: Pawn, inMelee: Boolean) {
        if (inMelee && npc.world.random(2) == 0) {
            meleeAttack(npc, target)
            return
        }
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        for (player in NexEncounter.players()) {
            npc.world.spawn(npc.createProjectile(player, PROJ_MAGIC, ProjectileType.MAGIC))
            val protecting = player.isProtectedFrom(CombatClass.MAGIC)
            magicHit(npc, player, magicMax(npc), 1) { damage ->
                player.alterPrayerPoints(-(damage / 4).coerceAtLeast(0))
                if (npc.world.random(if (protecting) 6 else 3) == 0) {
                    player.freeze(15)
                    player.graphic(GFX_ICE_HIT)
                }
            }
        }
    }

    private fun icePrison(npc: Npc) {
        npc.forceChat("Die now, in a prison of ice!")
        NexEncounter.playEncounterSound(npc, NexEncounter.SOUND_ICE_PRISON)
        npc.animate(ANIM_MAGIC)
        val world = npc.world
        val player = NexEncounter.players().filter { it.tile.isWithinRadius(npc.tile, 14) }.randomOrNull() ?: return
        world.spawn(npc.createProjectile(player, PROJ_MAGIC, ProjectileType.MAGIC))
        val base = Tile(player.tile)
        val prison = ArrayList<DynamicObject>()
        for (dx in -1..1) {
            for (dz in -1..1) {
                val tile = base.transform(dx, dz)
                if (world.collision.isClipped(tile)) continue
                val obj = DynamicObject(Objs.STALAGMITE_57263, 10, 0, tile)
                world.spawn(obj)
                prison.add(obj)
            }
        }
        world.queue {
            wait(8)
            if (player.tile == base) {
                player.message("The centre of the ice prison freezes you to the bone!")
                player.stopMovement()
                player.hit(world.random(80), HitType.REGULAR_HIT)
            }
            prison.forEach { world.remove(it) }
        }
    }

    fun reset() {
        bloodCounter = 0
        iceCounter = 0
        virusCooldown = 0
        shadowTrapsActive = false
        busy = false
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
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady() && NexEncounter.fightActive) {
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
