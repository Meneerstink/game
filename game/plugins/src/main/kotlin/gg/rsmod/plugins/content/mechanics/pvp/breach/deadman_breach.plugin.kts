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

on_world_init {
    DeadmanBreach.start(world)
    logger.info("Deadman breaches: {} monsters, next opening {}.", BreachMonsters.ROSTER.size, DeadmanBreach.nextOpening)
}

// ---- rules --------------------------------------------------------------------------------------------------------------

// OSRS Wiki "Deadman Mode": "Players with PvP protection active cannot attack breach monsters at either breach location."
can_attack { attacker, target ->
    // Pestilent Bloat "does not directly attack": only its flies hurt (the per-tick loop below).
    if (attacker is Npc && attacker.id == BLOAT && DeadmanBreach.isBreachNpc(attacker)) return@can_attack false
    val npc = (target as? Npc) ?: (attacker as? Npc)
    val player = (attacker as? Player) ?: (target as? Player)
    if (npc == null || player == null || !DeadmanBreach.isBreachNpc(npc) || DeadmanBreach.canFight(player)) {
        true
    } else {
        if (attacker is Player && world.plugins.notifyAttackRefusal) {
            player.filterableMessage("You can't attack breach monsters while you have PvP protection.")
        }
        false
    }
}

BreachMonsters.ROSTER.forEach { m ->
    on_npc_death(m.id) {
        val npc = npc
        if (!DeadmanBreach.isBreachNpc(npc)) return@on_npc_death
        if (m.summon) return@on_npc_death
        DeadmanBreach.dropLoot(npc)
    }
}

// ---- monster mechanics --------------------------------------------------------------------------------------------------

/** "casting Tele Block on players, preventing them from teleporting for 1 minute" (ADAPTED: on every landed magic hit). */
val TELEBLOCK_TICKS = 100

listOf("breach_justiciar", "breach_derwen").forEach { def ->
    NpcAttacks.onImpact(def, "magic") { _, target ->
        if (target is Player && !target.timers.has(TELEBLOCK_TIMER)) {
            target.timers[TELEBLOCK_TIMER] = TELEBLOCK_TICKS
            target.message("You have been teleblocked.")
        }
        true
    }
}

/** Flaming pyrelord: "2 hitsplats of 5 burn damage each" per hit (ADAPTED: 4 ticks apart). */
NpcAttacks.onImpact("breach_pyrelord", "magic") { _, target ->
    target.hit(5, HitType.REGULAR_HIT, delay = 4)
    target.hit(5, HitType.REGULAR_HIT, delay = 8)
    true
}

/** Sulphur Lizard: "knocking back any players hit by it" - one tile straight away from the lizard when that tile is free. */
NpcAttacks.onImpact("breach_sulphur_lizard", "melee") { npc, target ->
    val dx = Integer.signum(target.tile.x - npc.tile.x)
    val dz = Integer.signum(target.tile.z - npc.tile.z)
    val dest = target.tile.transform(dx, dz)
    val dir = Direction.between(target.tile, dest)
    if (dir != Direction.NONE && world.collision.canTraverse(target.tile, dir, projectile = false, water = false)) target.moveTo(dest)
    true
}

/** Magic Mark: blood spells "healing himself with the damage he deals". */
NpcAttacks.onHitDealt("breach_magic_mark") { npc, _, _, damage ->
    if (damage > 0) npc.setCurrentLifepoints((npc.getCurrentLifepoints() + damage).coerceAtMost(npc.getMaximumLifepoints()))
}

/** Durial321: Ice Barrage freeze "lasts for a shorter duration than regular freezes" (ADAPTED: half of Ice Barrage's 32 ticks). */
val DURIAL_FREEZE_TICKS = 16

NpcAttacks.onImpact("breach_durial321", "ice_barrage") { _, target ->
    target.freeze(DURIAL_FREEZE_TICKS)
    true
}

/** I DSCIM YOU: Sever while the target is not under its effect, a normal slash otherwise. */
NpcAttacks.condition("breach_sever_ready") { _, target -> target !is Player || !Prayers.overheadsDisabled(target) }
NpcAttacks.condition("breach_target_severed") { _, target -> target is Player && Prayers.overheadsDisabled(target) }

NpcAttacks.onImpact("breach_dscim", "sever") { _, target ->
    if (target is Player) {
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MAGIC)
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MISSILES)
        Prayers.deactivate(target, Prayer.PROTECT_FROM_MELEE)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MAGIC)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MISSILES)
        AncientCurses.deactivateCurse(target, AncientCurse.DEFLECT_MELEE)
        Prayers.disableOverheads(target, 8)
        target.message("You have been injured!")
    }
    true
}

/** Greater abyssal demon: "capable of rapidly teleporting short distances" (ADAPTED: the 667 abyssal demon script's 1/6 per hit). */
NpcAttacks.onAttack("breach_greater_abyssal_demon", "melee") { npc, target ->
    if (world.random(5) != 0) return@onAttack
    val dest = Tile(target.tile.x + world.random(-2..2), target.tile.z + world.random(-2..2), target.tile.height)
    if (dest != target.tile && !world.collision.isClipped(dest) && npc.lock.canMove()) {
        world.spawn(TileGraphic(tile = dest, id = Gfx.ABYSSAL_DEMON_TELEPORT, height = 0))
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
    val others = ArrayList<Player>()
    world.players.forEach { p ->
        if (p !== current && !p.isDead() && p.tile.height == npc.tile.height && p.tile.isWithinRadius(npc.tile, SWITCH_RADIUS) && DeadmanBreach.canFight(p)) others += p
    }
    val next = others.randomOrNull() ?: return
    world.queue {
        wait(1)
        if (world.npcs.contains(npc) && !npc.isDead() && world.players.contains(next)) npc.attack(next)
    }
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
    val tile = Tile(target.tile.x + world.random(-1..1), target.tile.z + world.random(-1..1), target.tile.height)
    val spawned = DeadmanBreach.spawnMonster(world, summon.id, if (world.collision.isClipped(tile)) target.tile else tile, 8, DeadmanBreach.LINGER_TICKS)
    alive += spawned
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
val nbAttacked = gg.rsmod.game.model.attr.AttributeKey<Boolean>()
val nbFireballsLeft = gg.rsmod.game.model.attr.AttributeKey<Int>()
val nbResumeCycle = gg.rsmod.game.model.attr.AttributeKey<Int>()

fun nbSpecialActive(npc: Npc) = (npc.attr[nbFireballsLeft] ?: 0) > 0

fun nbBordering(npc: Npc, target: gg.rsmod.game.model.entity.Pawn) =
    gg.rsmod.plugins.content.combat.Combat.areBordering(npc.tile.x, npc.tile.z, npc.getSize(), npc.getSize(), target.tile.x, target.tile.z, target.getSize(), target.getSize())

NpcAttacks.condition("breach_nb_magic") { npc, target -> !nbSpecialActive(npc) && (npc.attr[nbAttacked] != true || !nbBordering(npc, target)) }
NpcAttacks.condition("breach_nb_melee") { npc, _ -> !nbSpecialActive(npc) && npc.attr[nbAttacked] == true }
NpcAttacks.condition("breach_nb_fireball") { npc, _ -> nbSpecialActive(npc) && world.currentCycle >= (npc.attr[nbResumeCycle] ?: 0) }

NpcAttacks.onSwing("breach_night_beast") { npc, _, attack ->
    npc.attr[nbAttacked] = true
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
            ArrayList<Player>().also { list ->
                world.players.forEach { if (it.tile.height == npc.tile.height && it.tile.isWithinRadius(npc.tile, 1 + npc.getSize())) list += it }
            }
        } else {
            listOfNotNull(npc.getCombatTarget() as? Player)
        }
    victims.forEach { it.hit(world.random(SPLATTER_EXPLOSION_MAX)) }
}

/** Bee Swarm: "Stepping beneath the bee swarm will result in taking damage" (ADAPTED: 0 to its max hit 11, each tick). */
/** Pestilent Bloat: flies hit every player it can see, fully blocked by Protect from Missiles (ADAPTED: 10 tiles, every 5 ticks). */
val BLOAT_RANGE = 10

on_world_init {
    world.queue {
        var tick = 0
        while (true) {
            wait(1)
            tick++
            DeadmanBreach.pruneLive(world)
            DeadmanBreach.live.forEach { npc ->
                when (npc.id) {
                    BEE_SWARM ->
                        world.players.forEach { p ->
                            val t = p.tile
                            if (t.height == npc.tile.height && t.x in npc.tile.x until npc.tile.x + npc.getSize() && t.z in npc.tile.z until npc.tile.z + npc.getSize() &&
                                DeadmanBreach.canFight(p)
                            ) {
                                p.hit(world.random(11))
                                p.filterableMessage("You get stung while stood within the swarm of bees.")
                            }
                        }
                    BLOAT ->
                        if (tick % 5 == 0) {
                            world.players.forEach { p ->
                                if (p.tile.height == npc.tile.height && p.tile.isWithinRadius(npc.tile, BLOAT_RANGE) && DeadmanBreach.canFight(p) &&
                                    npc.hasLineOfSightTo(p, projectile = true, maximumDistance = BLOAT_RANGE)
                                ) {
                                    p.graphic(BreachMonsters.GFX_BLOAT_FLIES)
                                    p.playSound(BreachMonsters.SFX_BLOAT_FLIES)
                                    val protected = Prayers.isActive(p, Prayer.PROTECT_FROM_MISSILES)
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
        opened.sites.forEach { s -> player.message("Breach: ${s.name} (${s.kind}, ${if (s.multi) "multi" else "single"}) spawner ${s.spawners.first()}") }
    }
}

/** Owner 2026-09-19 ("i cant find the breach please fix me a teleport command"): to the open breach's spawner (site 1 or 2). */
/** Every breach location with its number, for breach <n> / breachtele <n>. */
on_command("breachlist", Privilege.ADMIN_POWER) {
    DeadmanBreach.allLocations(world).forEachIndexed { i, (name, _) -> player.message("${i + 1}. $name") }
}

/** Closes the open breach and removes its monsters. */
on_command("breachclose", Privilege.ADMIN_POWER) {
    DeadmanBreach.close(world)
    player.message("The breach is closed.")
}

on_command("breachtele", Privilege.ADMIN_POWER) {
    // breachtele <number from breachlist | part of a name> goes to any breach location, open or not; breachtele alone goes to
    // the open breach.
    val arg = player.getCommandArgs().joinToString(" ").trim()
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
    val index = (player.getCommandArgs().firstOrNull()?.toIntOrNull() ?: 1).coerceIn(1, open.sites.size) - 1
    val site = open.sites[index]
    val dest = site.landing.minByOrNull { it.getDistance(site.spawners.first()) } ?: site.spawners.first()
    player.moveTo(dest)
    player.message("Teleported to the breach ${site.name.replaceFirstChar { it.lowercase() }} (${if (site.multi) "multi" else "single"}-way).")
}

on_command("breachinfo") {
    player.message(DeadmanBreach.statusLine())
}
