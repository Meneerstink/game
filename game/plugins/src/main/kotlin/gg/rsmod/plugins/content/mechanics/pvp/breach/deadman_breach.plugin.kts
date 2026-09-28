package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.getLastHitBy
import gg.rsmod.plugins.content.combat.isBeingAttacked
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses

/**
 * OSRS permanent Deadman Mode breaches: combat definitions and attack rows of every breach monster ([BreachMonsters]), the
 * breach schedule ([DeadmanBreach.start]), loot on death ([BreachLoot]), the PvP-protection rule and the monster mechanics the
 * wiki describes. Values the wiki does not give are named constants marked ADAPTED below (listed in the handoff for the owner).
 */

val BEE_SWARM = 14459
val BLOAT = 14470

// ---- combat definitions -------------------------------------------------------------------------------------------------

BreachMonsters.ROSTER.forEach { m ->
    set_combat_def(m.id) {
        configs {
            attackSpeed = m.speed
            attackStyle = m.style
            respawnDelay = 0
            poisonImmune = m.id in BreachMonsters.POISON_IMMUNE
            venomImmune = m.id in BreachMonsters.VENOM_IMMUNE
        }
        aggro {
            radius = 8
            searchDelay = 1
            alwaysAggro()
        }
        stats {
            hitpoints = m.stats.hp * 10
            attack = m.stats.att
            strength = m.stats.str
            defence = m.stats.def
            magic = m.stats.mage
            ranged = m.stats.range
        }
        bonuses {
            attackBonus = m.stats.attbns
            strengthBonus = m.stats.strbns
            attackMagic = m.stats.amagic
            magicDamageBonus = m.stats.mbns
            attackRanged = m.stats.arange
            rangedStrengthBonus = m.stats.rngbns
            defenceStab = m.stats.dstab
            defenceSlash = m.stats.dslash
            defenceCrush = m.stats.dcrush
            defenceMagic = m.stats.dmagic
            defenceRanged = m.stats.dranged
        }
        anims {
            attack = m.attackAnim
            block = m.blockAnim
            death = m.deathAnim
        }
        if (m.id in BreachMonsters.DEMONBANE_VULNERABILITY) {
            species { +gg.rsmod.plugins.api.NpcSpecies.DEMON }
        }
    }
}

BreachMonsters.DEMONBANE_VULNERABILITY.forEach { (id, percent) -> gg.rsmod.plugins.content.items.osrs.Demonbane.vulnerability[id] = percent }

BreachMonsters.attackRows().forEach { NpcAttacks.register(it) }
// Breach conformance trace (breachtrace command / -Drsps.breachTrace=true / C:\RSPS\breachtrace.on): the engine hooks follow these ids.
gg.rsmod.game.model.BreachTrace.watchedNpcIds = BreachMonsters.ROSTER.map { it.id }.toSet()
gg.rsmod.plugins.api.ext.FREEZE_RESISTANT_NPCS += BreachMonsters.FREEZE_IMMUNE
gg.rsmod.plugins.content.combat.formula.ElementalWeakness.idOverrides += BreachMonsters.WEAKNESS_OVERRIDES
BreachMonsters.SOUND_ALIASES.forEach { (id, source) -> gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.alias(id, source) }
BreachMonsters.SOUND_ROWS.forEach { (id, s) ->
    NpcCombatAudio.register(
        NpcCombatAudio.Row(
            id = id,
            name = BreachMonsters.BY_ID.getValue(id).name,
            attack = if (s.first >= 0) listOf(NpcCombatAudio.Sound(id = s.first)) else emptyList(),
            defend = s.second,
            death = s.third,
        ),
    )
}

/*
 * Multi-way breach sites below the surface (Ourania Altar, Mor Ul Rek, the Mole Hole): the Deadman: Annihilation multicombat map
 * covers the surface only (MultiCombatZones), but the wiki's breach table lists these sites as multi-way combat - their landing
 * area (every chunk within LOCAL_SPAWN_RADIUS of a spawner) is registered as multi.
 */
DeadmanBreach.loadLocations().localised.filter { it.multi }.forEach { site ->
    site.spawners.filter { !gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.covers(it.x, it.z) }.forEach { s ->
        val r = DeadmanBreach.LOCAL_SPAWN_RADIUS
        for (x in (s.x - r) until (s.x + r + 8) step 8) for (z in (s.z - r) until (s.z + r + 8) step 8) {
            set_multi_combat_chunk(Tile(x, z, s.height).chunkCoords.hashCode())
        }
    }
}

on_world_init {
    DeadmanBreach.start(world)
    logger.info("Deadman breaches: {} monsters, next opening {}.", BreachMonsters.ROSTER.size, DeadmanBreach.nextOpening)
    // Agent test run only (-Drsps.breachSim=true): never active in a normal start.
    if (BreachSim.enabled()) BreachSim.start(world)
    // Agent conformance run only (-Drsps.breachConformance=true): multi-player OSRS checks per monster, single and multi.
    if (BreachConformance.enabled()) BreachConformance.start(world)
}

// ---- rules --------------------------------------------------------------------------------------------------------------

// OSRS Wiki "Deadman Mode": "Players with PvP protection active cannot attack breach monsters at either breach location."
can_attack { attacker, target ->
    // Pestilent Bloat "does not directly attack": only its flies hurt (the per-tick loop below).
    if (attacker is Npc && attacker.id == BLOAT && DeadmanBreach.isBreachNpc(attacker)) return@can_attack false
    // The breach monster on either side; the other side's player is the player itself or the owner of a familiar, so a
    // protected player's familiar cannot fight breach monsters for him (owner 2026-09-23 protection-bypass audit).
    val npc = (target as? Npc)?.takeIf { DeadmanBreach.isBreachNpc(it) } ?: (attacker as? Npc)?.takeIf { DeadmanBreach.isBreachNpc(it) }
    val other = if (npc === target) attacker else target
    val player = DeadmanBreach.controllingPlayer(other)
    if (npc == null || player == null || DeadmanBreach.canFight(player)) {
        true
    } else {
        if (attacker !== npc && world.plugins.notifyAttackRefusal) {
            player.filterableMessage("You can't attack breach monsters while you have PvP protection.")
        }
        false
    }
}

BreachMonsters.ROSTER.forEach { m ->
    on_npc_death(m.id) {
        val npc = npc
        if (!DeadmanBreach.isBreachNpc(npc)) return@on_npc_death
        gg.rsmod.game.model.BreachTrace.log("death") {
            "npc=${npc.id}#${npc.index} at=${npc.tile.x},${npc.tile.z} deathAnims=${npc.combatDef.deathAnimation} summon=${m.summon} contributors=${DeadmanBreach.contribution(npc).size}"
        }
        if (m.summon) return@on_npc_death
        DeadmanBreach.dropLoot(npc)
    }
}

// ---- monster mechanics --------------------------------------------------------------------------------------------------

/** "casting Tele Block on players, preventing them from teleporting for 1 minute" (ADAPTED: on every landed magic hit). */
val TELEBLOCK_TICKS = 100

/** Tele Block impact synth, the same id the players' spell plays (SpellSounds: CombatSpell.TELEPORT_BLOCK impact 203). */
val TELEBLOCK_IMPACT_SOUND = 203

listOf("breach_justiciar", "breach_derwen").forEach { def ->
    NpcAttacks.onLandedImpact(def, "magic") { _, target ->
        if (target is Player && !target.timers.has(TELEBLOCK_TIMER)) {
            gg.rsmod.game.model.BreachTrace.log("mech.teleblock") { "def=$def player=${target.username}" }
            target.timers[TELEBLOCK_TIMER] = TELEBLOCK_TICKS
            // Owner live test 2026-09-28 ("Derwen mist de teleblock animatie en teleblock sound"): the players' Tele Block impact
            // look and sound (CombatSpell.TELEPORT_BLOCK: OSRS spotanim 345 TELE_BLOCK_IMPACT, SpellSounds impact 203).
            target.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.TELE_BLOCK_IMPACT, 0)
            target.playSound(TELEBLOCK_IMPACT_SOUND)
            target.message("You have been teleblocked.")
        }
        true
    }
}

/** Flaming pyrelord: "2 hitsplats of 5 burn damage each" per hit (ADAPTED: 4 ticks apart). */
NpcAttacks.onImpact("breach_pyrelord", "magic") { _, target ->
    gg.rsmod.game.model.BreachTrace.log("mech.burn") { "target=${(target as? Player)?.username ?: target.toString()}" }
    // The OSRS burn hitsplat (HitType.BURN, owner 2026-09-19), not the plain red one (Deadman audit 2026-09-28).
    target.hit(5, HitType.BURN, delay = 4)
    target.hit(5, HitType.BURN, delay = 8)
    // A burn tick sound (owner 2026-09-28): ADAPTED 2412 sizzle (OSRS Wiki "List of sound IDs", 667 cache id) with each burn hitsplat.
    world.queue {
        for (d in listOf(4, 4)) {
            wait(d)
            if (target.isDead()) return@queue
            world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = target.tile, id = BURN_SIZZLE_SOUND, radius = 5, volume = 1))
        }
    }
    true
}

/** Sulphur Lizard: "knocking back any players hit by it" - one tile straight away from the lizard when that tile is free. */
NpcAttacks.onLandedImpact("breach_sulphur_lizard", "melee") { npc, target ->
    // Owner live test 2026-09-28: the knockback pushed players into trees/rocks and they were stuck. Two causes: (1) canTraverse
    // only asked the tile being left, never the tile entered (CollisionManager.canStep asks both); (2) "away" was measured from
    // the lizard's south-west corner, so a player beside its 5x5 body could be knocked sideways or diagonally.
    val size = npc.getSize()
    val dx = if (target.tile.x < npc.tile.x) -1 else if (target.tile.x > npc.tile.x + size - 1) 1 else 0
    val dz = if (target.tile.z < npc.tile.z) -1 else if (target.tile.z > npc.tile.z + size - 1) 1 else 0
    val dest = target.tile.transform(dx, dz)
    val dir = Direction.between(target.tile, dest)
    val moved = dir != Direction.NONE && world.collision.canStep(target.tile, dir)
    gg.rsmod.game.model.BreachTrace.log("mech.knockback") { "target=${(target as? Player)?.username ?: target.toString()} moved=$moved to=${dest.x},${dest.z}" }
    if (moved) target.moveTo(dest)
    true
}

/** Magic Mark: blood spells "healing himself with the damage he deals". */
NpcAttacks.onHitDealt("breach_magic_mark") { npc, _, _, damage ->
    if (damage > 0) {
        gg.rsmod.game.model.BreachTrace.log("mech.heal") { "npc=${npc.id}#${npc.index} +$damage hp=${npc.getCurrentLifepoints()}" }
        npc.setCurrentLifepoints((npc.getCurrentLifepoints() + damage).coerceAtMost(npc.getMaximumLifepoints()))
    }
}

/** Durial321: Ice Barrage freeze "lasts for a shorter duration than regular freezes" (ADAPTED: half of Ice Barrage's 32 ticks). */
val DURIAL_FREEZE_TICKS = 16

// A splashed Ice Barrage freezes nobody (OSRS Wiki "Ice Barrage"), so the freeze needs a landed hit.
NpcAttacks.onLandedImpact("breach_durial321", "ice_barrage") { _, target ->
    val frozen = target.freeze(DURIAL_FREEZE_TICKS) {}
    gg.rsmod.game.model.BreachTrace.log("mech.freeze") { "npc=durial target=${(target as? Player)?.username} applied=$frozen ticks=$DURIAL_FREEZE_TICKS" }
    if (frozen && target is Player) target.message("You have been frozen.")
    true
}

/** I DSCIM YOU: Sever while the target is not under its effect, a normal slash otherwise. */
NpcAttacks.condition("breach_sever_ready") { _, target -> target !is Player || !Prayers.overheadsDisabled(target) }
NpcAttacks.condition("breach_target_severed") { _, target -> target is Player && Prayers.overheadsDisabled(target) }

// Sever only disables prayers on a successful hit, as the players' dragon scimitar special.
NpcAttacks.onLandedImpact("breach_dscim", "sever") { _, target ->
    if (target is Player) {
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MAGIC)
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MISSILES)
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MELEE)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MAGIC)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MISSILES)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MELEE)
        Prayers.disableOverheads(target, 8)
        gg.rsmod.game.model.BreachTrace.log("mech.sever") { "player=${target.username} overheadsDisabled=${Prayers.overheadsDisabled(target)}" }
        target.message("You have been injured!")
    }
    true
}

val ABYSSAL_TELEPORT_SOUND = 278

/** Greater abyssal demon: "capable of rapidly teleporting short distances" (ADAPTED: the 667 abyssal demon script's 1/6 per hit). */
NpcAttacks.onAttack("breach_greater_abyssal_demon", "melee") { npc, target ->
    if (world.random(5) != 0) return@onAttack
    // Breach audit 2026-09-27 (in-engine test run): the 3x3 demon landed on top of its target (footprint overlapping the
    // player). The destination is now a spot where its whole footprint is free and next to, never on, the target.
    val size = npc.getSize()
    val dest = Tile(target.tile.x + world.random(-(size + 1)..2), target.tile.z + world.random(-(size + 1)..2), target.tile.height)
    val overlaps =
        dest.x < target.tile.x + target.getSize() && target.tile.x < dest.x + size &&
            dest.z < target.tile.z + target.getSize() && target.tile.z < dest.z + size
    if (!overlaps && DeadmanBreach.fits(world, dest, size) && npc.lock.canMove()) {
        world.spawn(TileGraphic(tile = dest, id = Gfx.ABYSSAL_DEMON_TELEPORT, height = 0))
        // Owner live test 2026-09-28 ("mist wat sound"): the teleport was silent. Void abyssal_demon.sounds.toml abyssal_demon_teleport = 278.
        world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = dest, id = ABYSSAL_TELEPORT_SOUND, radius = 10, volume = 1))
        gg.rsmod.game.model.BreachTrace.log("mech.teleport") { "npc=${npc.id}#${npc.index} from=${npc.tile.x},${npc.tile.z} to=${dest.x},${dest.z}" }
        npc.teleportNpc(dest)
    }
}

/** Zemouregal "can summon Zemouregal Summons" (ADAPTED: every 5th swing, at most 3 alive, next to his target). */
val ZEMOUREGAL_SUMMON_EVERY = 5
val ZEMOUREGAL_MAX_SUMMONS = 3
val summonCount = gg.rsmod.game.model.attr.AttributeKey<Int>()
val summonsOf = gg.rsmod.game.model.attr.AttributeKey<MutableList<Npc>>()

/*
 * OSRS Wiki "Zemouregal (Deadman)": "He will also not stay focused on a single target, instead occasionally switching targets if other
 * players are nearby"; "Zemouregal Summon": "They will also frequently switch targets if there are multiple players to target".
 * ADAPTED (no rate given): Zemouregal 1 in ZEMOUREGAL_SWITCH_CHANCE swings, summons 1 in SUMMON_SWITCH_CHANCE, to another fightable
 * player within SWITCH_RADIUS tiles.
 */
val ZEMOUREGAL_SWITCH_CHANCE = 5
val SUMMON_SWITCH_CHANCE = 2
val SWITCH_RADIUS = 8

fun maybeSwitchTarget(npc: Npc, current: gg.rsmod.game.model.entity.Pawn, chance: Int) {
    if (world.random(chance - 1) != 0) return
    val others =
        DeadmanBreach.playersNear(world, npc.tile, SWITCH_RADIUS).filter { p ->
            p !== current && !p.isDead() && DeadmanBreach.canFight(p) &&
                gg.rsmod.plugins.content.combat.NpcLeash.mayPursue(npc, p, NpcAttacks.attackRange(npc) ?: 1) &&
                // single-way: never onto a player someone else is already fighting - or has just picked and not hit yet
                // (conformance run 2026-09-27: a Zemouregal summon switched onto its master's target, two monsters on one player)
                (
                    npc.tile.isMulti(world) ||
                        ((!p.isBeingAttacked() || p.getLastHitBy() === npc) && DeadmanBreach.live.none { it !== npc && !it.isDead() && it.getCombatTarget() === p })
                )
        }
    val next = others.randomOrNull() ?: return
    world.queue {
        wait(1)
        if (!world.npcs.contains(npc) || npc.isDead() || !world.players.contains(next)) return@queue
        // Breach conformance 2026-09-28 (pile-up): Zemouregal and a summon both chose the same player on one tick; each check ran
        // before the other's switch happened. Single-way: check again now, when the switch is made.
        if (!next.tile.isMulti(world) && DeadmanBreach.live.any { it !== npc && !it.isDead() && world.npcs.contains(it) && it.getCombatTarget() === next }) return@queue
        npc.attack(next)
    }
}

/*
 * Owner decision 2026-09-27 (OSRS sources silent except Zemouregal): in multi-way combat EVERY breach monster occasionally
 * switches to another player nearby, like Zemouregal - 1 in MULTI_SWITCH_CHANCE swings. Single-way keeps one monster per player.
 * Zemouregal and his summons keep their own hooks below.
 */
val MULTI_SWITCH_CHANCE = 5

NpcAttacks.onAnySwing { npc, target, _ ->
    if (!DeadmanBreach.isBreachNpc(npc) || npc.id == 14466 || BreachMonsters.BY_ID[npc.id]?.summon == true) return@onAnySwing
    if (!npc.tile.isMulti(world) || !target.tile.isMulti(world)) return@onAnySwing
    maybeSwitchTarget(npc, target, MULTI_SWITCH_CHANCE)
}

BreachMonsters.SUMMONS.forEach { s ->
    val def = (s.attacks as BreachMonsters.Custom).combatDef
    NpcAttacks.onSwing(def) { npc, target, _ -> maybeSwitchTarget(npc, target, SUMMON_SWITCH_CHANCE) }
}

NpcAttacks.onSwing("breach_zemouregal") { npc, target, _ ->
    maybeSwitchTarget(npc, target, ZEMOUREGAL_SWITCH_CHANCE)
    val swings = (npc.attr[summonCount] ?: 0) + 1
    npc.attr[summonCount] = swings
    if (swings % ZEMOUREGAL_SUMMON_EVERY != 0) return@onSwing
    val alive = (npc.attr[summonsOf] ?: ArrayList<Npc>().also { npc.attr[summonsOf] = it })
    alive.removeIf { !world.npcs.contains(it) || it.isDead() }
    if (alive.size >= ZEMOUREGAL_MAX_SUMMONS) return@onSwing
    npc.animate(15678)
    npc.graphic(BreachMonsters.GFX_ZEMOUREGAL_SUMMON)
    val summon = BreachMonsters.SUMMONS.random()
    val size = world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, summon.id).size.coerceAtLeast(1)
    val spawned = DeadmanBreach.spawnMonster(world, summon.id, DeadmanBreach.summonTile(world, target, size), 8, DeadmanBreach.LINGER_TICKS, test = DeadmanBreach.isTestNpc(npc))
    alive += spawned
}

/*
 * TzTok-Jad (Deadman): "In Deadman: Annihilation, it can summon a TzTok-Jad-Rek, which is a baby version of itself."
 * ADAPTED (no rate given): the same cadence as Zemouregal's summons - every JAD_REK_EVERY-th swing, one Jad-Rek alive at a time,
 * next to its target, gone after the breach linger time. Keyed on the shared tztok_jad combat definition, so only the breach Jad.
 */
val JAD_REK_EVERY = 5
val jadSwings = gg.rsmod.game.model.attr.AttributeKey<Int>()
val jadRek = gg.rsmod.game.model.attr.AttributeKey<Npc>()

NpcAttacks.onSwing("tztok_jad") { npc, target, _ ->
    if (npc.id != BreachMonsters.TZTOK_JAD || !DeadmanBreach.isBreachNpc(npc)) return@onSwing
    val swings = (npc.attr[jadSwings] ?: 0) + 1
    npc.attr[jadSwings] = swings
    if (swings % JAD_REK_EVERY != 0) return@onSwing
    val alive = npc.attr[jadRek]
    if (alive != null && world.npcs.contains(alive) && !alive.isDead()) return@onSwing
    val size = world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, BreachMonsters.TZTOK_JAD_REK).size.coerceAtLeast(1)
    npc.attr[jadRek] =
        DeadmanBreach.spawnMonster(
            world, BreachMonsters.TZTOK_JAD_REK, DeadmanBreach.summonTile(world, target, size), 8, DeadmanBreach.LINGER_TICKS, test = DeadmanBreach.isTestNpc(npc),
        )
}

/*
 * Night beast (OSRS Wiki "Night beast", which "Night beast (Deadman)" says it attacks like): "a night beast's initial attack is
 * always magic ... It will always attempt to melee the player unless they cannot reach them, in which they will begin using magic";
 * special: "it will briefly stop attacking then it uses Magic for three attacks which resemble Fire Blast, covering a 3x3 area
 * dealing damage equal to 1/3 of the player's current Hitpoints (rounded down)" (so it cannot kill below 3 HP).
 * ADAPTED (the wiki gives no rate or pause length): the special starts on 1 in NIGHT_BEAST_SPECIAL_CHANCE normal swings and the
 * pause is one attack cycle (4 ticks) before the first fireball.
 */
val NIGHT_BEAST_SPECIAL_CHANCE = 5
val NIGHT_BEAST_FIREBALLS = 3
val NIGHT_BEAST_PAUSE_TICKS = 8
/** The target the Night beast has already opened on (breach audit 2026-09-28: a flag per npc made only its very first fight open with magic). */
val nbAttacked = gg.rsmod.game.model.attr.AttributeKey<java.lang.ref.WeakReference<gg.rsmod.game.model.entity.Pawn>>()

fun nbOpenedOn(npc: Npc, target: gg.rsmod.game.model.entity.Pawn) = npc.attr[nbAttacked]?.get() === target
val nbFireballsLeft = gg.rsmod.game.model.attr.AttributeKey<Int>()
val nbResumeCycle = gg.rsmod.game.model.attr.AttributeKey<Int>()

fun nbSpecialActive(npc: Npc) = (npc.attr[nbFireballsLeft] ?: 0) > 0

fun nbBordering(npc: Npc, target: gg.rsmod.game.model.entity.Pawn) =
    gg.rsmod.plugins.content.combat.Combat.areBordering(npc.tile.x, npc.tile.z, npc.getSize(), npc.getSize(), target.tile.x, target.tile.z, target.getSize(), target.getSize())

/**
 * "Always attempt to melee the player unless they cannot reach them": magic only when no route to melee distance exists. Owner
 * 2026-09-27: magic used to be picked whenever the target was merely in spell range, so the npc stood there casting and never
 * walked in (and once the target came close it had no section left that matched).
 */
fun cannotReachMelee(npc: Npc, target: gg.rsmod.game.model.entity.Pawn) =
    !nbBordering(npc, target) &&
        (npc.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER) || !gg.rsmod.game.action.PawnPathAction.meleeRouteExists(npc, target))

NpcAttacks.condition("breach_nb_magic") { npc, target -> !nbSpecialActive(npc) && (!nbOpenedOn(npc, target) || cannotReachMelee(npc, target)) }
NpcAttacks.condition("breach_nb_melee") { npc, target -> !nbSpecialActive(npc) && nbOpenedOn(npc, target) }

/*
 * Durial321 (owner 2026-09-27: "hij hoort je 1 keer te barragen dan je whippen"): the opening attack of a fight is Ice Barrage,
 * then he runs in and whips; he casts again only while he cannot reach melee distance (freeze: DURIAL_FREEZE_TICKS above).
 */
val durialOpened = gg.rsmod.game.model.attr.AttributeKey<java.lang.ref.WeakReference<gg.rsmod.game.model.entity.Pawn>>()

fun durialOpenedOn(npc: Npc, target: gg.rsmod.game.model.entity.Pawn) = npc.attr[durialOpened]?.get() === target

NpcAttacks.condition("breach_durial_barrage") { npc, target -> !durialOpenedOn(npc, target) || cannotReachMelee(npc, target) }
NpcAttacks.condition("breach_durial_whip") { npc, target -> durialOpenedOn(npc, target) }

NpcAttacks.onSwing("breach_durial321") { npc, target, _ -> npc.attr[durialOpened] = java.lang.ref.WeakReference(target) }
NpcAttacks.condition("breach_nb_fireball") { npc, _ -> nbSpecialActive(npc) && world.currentCycle >= (npc.attr[nbResumeCycle] ?: 0) }

NpcAttacks.onSwing("breach_night_beast") { npc, target, attack ->
    npc.attr[nbAttacked] = java.lang.ref.WeakReference(target)
    if (attack.id == "fireball") {
        npc.attr[nbFireballsLeft] = (npc.attr[nbFireballsLeft] ?: 1) - 1
    } else if (world.random(NIGHT_BEAST_SPECIAL_CHANCE - 1) == 0) {
        npc.attr[nbFireballsLeft] = NIGHT_BEAST_FIREBALLS
        npc.attr[nbResumeCycle] = world.currentCycle + NIGHT_BEAST_PAUSE_TICKS
    }
}

NpcAttacks.onAttack("breach_night_beast", "fireball") { _, target ->
    if (target is Player && DeadmanBreach.canFight(target)) target.hit(target.getCurrentLifepoints() / 3, delay = 2)
}

/*
 * Big Evil Chicken (OSRS Wiki): "Like the regular Evil Chicken, it attacks with magic, and will say one of various lines of
 * dialogue when attacking players." Lines: OSRS Wiki "Transcript:Evil Chicken", overhead dialogue "When under attack" (random).
 * Keyed on the shared combat definition, so the regular Evil Chicken says them too, as in OSRS.
 */
val EVIL_CHICKEN_LINES = listOf("Bwaaaaaauk bwuk bwuk", "Bwuk", "Bwuk bwuk bwuk", "Flee from me, %s!", "Begone, %s!", "MUAHAHAHAHAAA!")

NpcAttacks.onSwing("evil_chicken") { npc, target, _ ->
    val name = (target as? Player)?.username ?: "human"
    npc.forceChat(EVIL_CHICKEN_LINES.random().replace("%s", name))
}

/** Splatter: "Upon death, it explodes": its target in single-way combat, every adjacent player in multi (ADAPTED: 0-100, the "100+"). */
val SPLATTER_EXPLOSION_MAX = 100

on_npc_pre_death(14468) {
    val npc = npc
    if (!DeadmanBreach.isBreachNpc(npc)) return@on_npc_pre_death
    world.spawn(TileGraphic(tile = npc.tile, id = BreachMonsters.GFX_SPLATTER_EXPLODE, height = 0))
    val victims =
        if (npc.tile.isMulti(world)) {
            // Breach audit 2026-09-28: adjacent to the whole footprint on every side (the radius used to be measured from the
            // south-west corner, so it reached size+1 tiles west/south and only 1 tile east/north).
            val size = npc.getSize()
            DeadmanBreach.playersNear(world, npc.tile, 1 + size).filter { p ->
                p.tile.x in (npc.tile.x - 1)..(npc.tile.x + size) && p.tile.z in (npc.tile.z - 1)..(npc.tile.z + size)
            }
        } else {
            listOfNotNull(npc.getCombatTarget() as? Player)
        }
    // PvP-protected players are never hurt by breach monsters, the explosion included.
    victims.filter { DeadmanBreach.canFight(it) }.forEach { it.hit(world.random(SPLATTER_EXPLOSION_MAX)) }
}

/** Bee Swarm: "Stepping beneath the bee swarm will result in taking damage" (ADAPTED: 0 to its max hit 11, each tick). */
/** Pestilent Bloat: flies hit every player it can see, fully blocked by Protect from Missiles (ADAPTED: 10 tiles, every 5 ticks). */
val BLOAT_RANGE = 10

val bloatFacing = gg.rsmod.game.model.attr.AttributeKey<java.lang.ref.WeakReference<Player>>()

/*
 * Owner decision 2026-09-28 ("de grote monsters moeten ook loop geluiden"): OSRS gives walk frame sounds only to Graardor, K'ril,
 * Bee Swarm and Bloat (BreachSoundAuditTool vs the pinned OSRS cache; theirs play from their own walk sequences). Every other
 * breach monster of size 3+ now plays a footstep every second tile it walks: ADAPTED, the God Wars generals' own OSRS footstep
 * (local 10272, the frame sound of Graardor's / K'ril's OSRS walk sequences). Not for the floating smoke devil and the jelly.
 */
val FOOTSTEP_SOUND = 10272

/** OSRS body sounds of the Dagannoth Kings by body type (OSRS Wiki "List of sound IDs": plate / range / shell body), their own footsteps. */
val FOOTSTEP_BY_NPC = mapOf(14438 to 1062, 14440 to 1068, 14441 to 1069)

val BURN_SIZZLE_SOUND = 2412
val FOOTSTEP_EVERY_TILES = 2
val OWN_WALK_SOUNDS = setOf(14442, 14444, 14459, 14470)
val NO_FOOTSTEPS = setOf(14455, 14460)
val lastStepTile = gg.rsmod.game.model.attr.AttributeKey<Tile>()
val stepsWalked = gg.rsmod.game.model.attr.AttributeKey<Int>()

fun footstep(npc: Npc) {
    if (npc.getSize() < 3 || npc.id in OWN_WALK_SOUNDS || npc.id in NO_FOOTSTEPS) return
    val last = npc.attr[lastStepTile]
    npc.attr[lastStepTile] = npc.tile
    if (last == null || last == npc.tile) return
    val steps = (npc.attr[stepsWalked] ?: 0) + 1
    npc.attr[stepsWalked] = steps
    if (steps % FOOTSTEP_EVERY_TILES == 0) {
        world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = npc.tile, id = FOOTSTEP_BY_NPC[npc.id] ?: FOOTSTEP_SOUND, radius = 10, volume = 1))
    }
}

/**
 * Owner live test 2026-09-28 ("bloat moet in ieder geval mijn kant op kijken"): the Bloat never gets a combat target (it "does not
 * directly attack", can_attack above), so it never turned to anyone. OSRS Wiki: it is aggressive and "wanders around, attacking
 * players with range and line of sight". It now faces the nearest player its flies can reach. Only the client-side face block is
 * sent - the FACING_PAWN attribute (which stops random walk) is dropped at once, so it keeps wandering while it looks at you.
 */
fun bloatFace(npc: Npc) {
    val current = npc.attr[bloatFacing]?.get()
    val next =
        DeadmanBreach.playersNear(world, npc.tile, BLOAT_RANGE)
            .filter { !it.isDead() && DeadmanBreach.canFight(it) && npc.hasLineOfSightTo(it, projectile = true, maximumDistance = BLOAT_RANGE) }
            .minByOrNull { it.tile.getDistance(npc.tile) }
    if (next === current) return
    if (next == null) {
        npc.resetFacePawn()
        npc.attr.remove(bloatFacing)
    } else {
        npc.facePawn(next)
        npc.attr.remove(gg.rsmod.game.model.attr.FACING_PAWN_ATTR)
        npc.attr[bloatFacing] = java.lang.ref.WeakReference(next)
    }
}

on_world_init {
    world.queue {
        var tick = 0
        while (true) {
            wait(1)
            tick++
            DeadmanBreach.pruneLive(world)
            DeadmanBreach.live.forEach { npc ->
                footstep(npc)
                when (npc.id) {
                    BEE_SWARM ->
                        DeadmanBreach.playersNear(world, npc.tile, npc.getSize()).forEach { p ->
                            val t = p.tile
                            if (t.x in npc.tile.x until npc.tile.x + npc.getSize() && t.z in npc.tile.z until npc.tile.z + npc.getSize() &&
                                DeadmanBreach.canFight(p)
                            ) {
                                p.hit(world.random(11))
                                p.filterableMessage("You get stung while stood within the swarm of bees.")
                            }
                        }
                    BLOAT -> {
                        bloatFace(npc)
                        if (tick % 5 == 0) {
                            DeadmanBreach.playersNear(world, npc.tile, BLOAT_RANGE).forEach { p ->
                                if (DeadmanBreach.canFight(p) &&
                                    npc.hasLineOfSightTo(p, projectile = true, maximumDistance = BLOAT_RANGE)
                                ) {
                                    p.graphic(BreachMonsters.GFX_BLOAT_FLIES)
                                    p.playSound(BreachMonsters.SFX_BLOAT_FLIES)
                                    // Deflect Missiles is the curse book's Protect from Missiles (the only kept non-OSRS extra).
                                    val protected = Prayers.isActive(p, Prayer.PROTECT_FROM_MISSILES) ||
                                        AncientCurses.isCurseActive(p, AncientCurse.DEFLECT_MISSILES)
                                    p.hit(if (protected) 0 else world.random(20), delay = 1)
                                    p.filterableMessage("Flies leave the body of the bloat and swarm you.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- information --------------------------------------------------------------------------------------------------------

on_command("breach", Privilege.ADMIN_POWER) {
    // Owner 2026-09-19 ("i can only teleport to one location"): an open breach is closed first, so every command opens a
    // new one. breach [local|region|<number from breachlist>|<part of a name>].
    val args = player.getCommandArgs()
    val first = args.joinToString(" ").trim()
    val kind =
        when (first.lowercase()) {
            "local", "localised", "localized" -> DeadmanBreach.Kind.LOCALISED
            "region", "regional" -> DeadmanBreach.Kind.REGIONAL
            else -> null
        }
    val only =
        if (kind != null || first.isEmpty()) {
            null
        } else {
            first.toIntOrNull()?.let { n -> DeadmanBreach.allLocations(world).getOrNull(n - 1)?.first?.substringBefore(" (") } ?: first
        }
    DeadmanBreach.close(world)
    val opened = DeadmanBreach.open(world, kind, only)
    if (opened == null) {
        player.message(if (only != null) "No usable breach location matches '$first'. Type breachlist." else "No usable breach location.")
    } else {
        opened.sites.forEachIndexed { i, s ->
            player.message("Breach site ${i + 1}: ${s.name} (${if (s.kind == DeadmanBreach.Kind.LOCALISED) "local" else "region"}, ${if (s.multi) "multi" else "single"}) - breachtele site ${i + 1}")
        }
    }
}

/** Owner 2026-09-19 ("i cant find the breach please fix me a teleport command"): to the open breach's spawner (site 1 or 2). */
/** Every breach location with its number, for breach <n> / breachtele <n>. */
on_command("breachlist", Privilege.ADMIN_POWER) {
    DeadmanBreach.allLocations(world).forEachIndexed { i, (name, _) -> player.message("${i + 1}. $name") }
}

/**
 * Diagnostic for "the breach monsters are invisible" (owner 2026-09-20).
 *
 * Spawns one of every spawnable breach monster in a line beside the player and prints the id and name of each, so
 * the invisible ones can be named instead of guessed at. The cache side has already been cleared: every breach npc
 * definition exists, its bas render type exists and points at real sequences, every model it references is present
 * with real data, and the game cache and the file-server cache are byte-identical. That leaves something at
 * spawn/sync time, and this narrows it to exactly which ids are affected.
 *
 * `breachtest` spawns them all; `breachtest <npcId>` spawns just that one on the player's own tile.
 */
on_command("breachtest", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val wanted = args.firstOrNull()?.toIntOrNull()
    val roster = if (wanted != null) BreachMonsters.ROSTER.filter { it.id == wanted } else BreachMonsters.SPAWNABLE
    if (roster.isEmpty()) {
        player.message("No breach monster with that id.")
        return@on_command
    }
    player.message("<col=ffff00>Spawning ${roster.size} test breach monster(s). They fight normally but give no loot, emblems or points.</col>")
    // Spread them in rows of 8 with a gap of one free tile around the largest body (owner 2026-09-27: at 2 tiles apart a 4x4 and a
    // 2x2 overlapped and blocked each other, so the test monsters could not walk).
    val cell = roster.maxOf { world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, it.id).size.coerceAtLeast(1) } + 1
    roster.forEachIndexed { index, monster ->
        val tile = player.tile.transform(1 + (index % 8) * cell, (index / 8) * cell)
        DeadmanBreach.spawnMonster(world, monster.id, tile, walkRadius = 0, test = true)
        player.message("${monster.id} ${monster.name} at ${tile.x},${tile.z}")
    }
}

/** Closes the open breach and removes its monsters. */
on_command("breachclose", Privilege.ADMIN_POWER) {
    val removed = DeadmanBreach.close(world)
    player.message("Every breach is closed: spawners, Boss Spawns and $removed breach monster(s) removed.")
}

on_command("breachtele", Privilege.ADMIN_POWER) {
    // breachtele <number from breachlist | part of a name> goes to any breach location, open or not; breachtele alone goes to
    // the open breach's first site, breachtele site <1|2> to that site of the open breach (breach audit 2026-09-27, F3: the
    // number was always read as a breachlist number, so the second site of an open breach could not be reached).
    val args = player.getCommandArgs()
    val siteArg = if (args.firstOrNull()?.equals("site", ignoreCase = true) == true) args.getOrNull(1)?.toIntOrNull() ?: 1 else null
    val arg = if (siteArg != null) "" else args.joinToString(" ").trim()
    val all = DeadmanBreach.allLocations(world)
    val target = arg.toIntOrNull()?.let { all.getOrNull(it - 1) } ?: arg.takeIf { it.isNotEmpty() }?.let { a -> all.firstOrNull { it.first.contains(a, ignoreCase = true) } }
    if (arg.isNotEmpty() && target == null) {
        player.message("No breach location matches '$arg'. Type breachlist.")
        return@on_command
    }
    target?.let { (name, tile) ->
        player.moveTo(tile)
        player.message("Teleported to breach location $name.")
        return@on_command
    }
    val open = DeadmanBreach.active
    if (open == null) {
        player.message("No breach is open. ${DeadmanBreach.statusLine()} Use 'breach' to open one.")
        return@on_command
    }
    if (siteArg != null && siteArg !in 1..open.sites.size) {
        player.message("The open breach has ${open.sites.size} site(s).")
        return@on_command
    }
    val index = (siteArg ?: 1) - 1
    val site = open.sites[index]
    val dest = site.landing.minByOrNull { it.getDistance(site.spawners.first()) } ?: site.spawners.first()
    player.moveTo(dest)
    player.message("Teleported to the breach ${site.name.replaceFirstChar { it.lowercase() }} (${if (site.multi) "multi" else "single"}-way).")
}

on_command("breachinfo") {
    player.message(DeadmanBreach.statusLine())
}

/** Breach conformance trace on/off: `[breachtrace]` lines in the game-server log (see BreachTrace). */
on_command("breachtrace", Privilege.ADMIN_POWER) {
    val trace = gg.rsmod.game.model.BreachTrace
    trace.enabled = !trace.enabled
    player.message("Breach trace: ${if (trace.enabled) "on" else "off"} (game-server log, [breachtrace] lines).")
}
