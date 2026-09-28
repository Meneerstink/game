package gg.rsmod.plugins.content.combat

import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.model.attr.AGGRESSOR
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.INTERACTING_PLAYER_ATTR
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.STUN_TIMER
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.BossNpcs
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab

set_combat_logic {
    if (pawn.getCombatTarget() != null) {
        // Deadman skull (owner 2026-09-18): NOT granted here at combat start any more. A player is
        // skulled only when a hitsplat registers on the target - see PvpSkull.onHitRegistered,
        // called from Pawn.dealHit (content/combat/PawnExt.kt), the one hit dispatch point every
        // melee/ranged/magic/special route passes through.
        // RC-1: a player's combat loop is persistent - it survives prayers, eating, equipping,
        // familiar commands and other STRONG/soft actions; only a hard interruption (walk, new
        // interaction, teleport, death) ends it. NPC loops keep the old head-only scheduling so
        // boss/familiar scripts that queue waiting tasks during combat keep their pacing.
        val traced = (pawn as? Npc)?.takeIf { gg.rsmod.game.model.BreachTrace.watches(it.id) }
        traced?.let { n -> gg.rsmod.game.model.BreachTrace.log("combat.start") { "npc=${n.id}#${n.index} locked=${!n.lock.canAttack()}" } }
        pawn.queue(persistent = pawn is Player) {
            traced?.let { n -> gg.rsmod.game.model.BreachTrace.log("combat.loop") { "npc=${n.id}#${n.index} first-cycle" } }
            while (true) {
                if (!cycle(this)) {
                    break
                }
                wait(1)
            }
        }
    }
}

on_player_option("Attack") {
    val target = pawn.attr[INTERACTING_PLAYER_ATTR]?.get() ?: return@on_player_option

    // R14.25: initiating PvP while protected needs explicit confirmation that permanently
    // forfeits protection - only for a real, explicit click here, never for auto-retaliation
    // (auto-retaliation never routes through this option handler at all, so it can't
    // accidentally forfeit protection - matching R14.25's "never through ... auto-retaliation").
    if (BeginnerProtection.isProtected(player)) {
        player.queue {
            val choice =
                options(
                    "Yes - attack (this permanently ends your beginner protection).",
                    "No, cancel.",
                )
            if (choice == 1) {
                BeginnerProtection.forfeit(player)
                player.attack(target)
            } else {
                player.message("You decide not to attack.")
            }
        }
        return@on_player_option
    }

    player.attack(target)
}

on_timer(ACTIVE_COMBAT_TIMER) {
    val pawn = pawn
    if (pawn.attr.has(AGGRESSOR)) {
        pawn.attr.remove(AGGRESSOR)
    }
}

/** Whether the two footprints overlap (a target standing inside an npc's body). */
fun overlaps(a: gg.rsmod.game.model.entity.Pawn, b: gg.rsmod.game.model.entity.Pawn): Boolean {
    val sa = a.getSize().coerceAtLeast(1)
    val sb = b.getSize().coerceAtLeast(1)
    return a.tile.height == b.tile.height && a.tile.x < b.tile.x + sb && b.tile.x < a.tile.x + sa && a.tile.z < b.tile.z + sb && b.tile.z < a.tile.z + sa
}

suspend fun cycle(it: QueueTask): Boolean {
    val pawn = it.pawn
    // Owner live test 2026-09-28: Justiciar and Big Evil Chicken picked a target and their combat loop ended at once with no trace;
    // the two silent exits below now say why (breach trace only).
    val watched = pawn is Npc && gg.rsmod.game.model.BreachTrace.watches(pawn.id)
    val target =
        pawn.getCombatTarget() ?: run {
            if (watched) gg.rsmod.game.model.BreachTrace.log("combat.exit") { "npc=${(pawn as Npc).id}#${pawn.index} reason=no-target" }
            return false
        }

    if (!pawn.lock.canAttack()) {
        if (watched) gg.rsmod.game.model.BreachTrace.log("combat.exit") { "npc=${(pawn as Npc).id}#${pawn.index} reason=locked lock=${pawn.lock}" }
        Combat.reset(pawn)
        return false
    }

    if (pawn.attr[FACING_PAWN_ATTR] != target) {
        pawn.facePawn(target)
    }

    if (!Combat.canEngage(pawn, target)) {
        Combat.reset(pawn)
        pawn.resetFacePawn()
        // Breach conformance 2026-09-27: a target that teleported far away ended combat here and the npc stood where it was,
        // 13 tiles from its spawn (K'ril). Same rule as the leash below: back to its spawn area.
        if (pawn is Npc) {
            if (gg.rsmod.game.model.BreachTrace.watches(pawn.id)) {
                gg.rsmod.game.model.BreachTrace.log("leash") {
                    "npc=${pawn.id}#${pawn.index} at=${pawn.tile.x},${pawn.tile.z} spawn=${pawn.spawnTile.x},${pawn.spawnTile.z} " +
                        "target=${(target as? Player)?.username ?: target.toString()}@${target.tile.x},${target.tile.z} result=cannot-engage-walk-home"
                }
            }
            NpcLeash.returnHome(pawn)
        }
        return false
    }

    if (pawn is Npc) {
        if (pawn.combatDef.spell > -1) {
            val spell = CombatSpell.values.firstOrNull { it.uniqueId == pawn.combatDef.spell }
            if (spell != null) {
                pawn.attr[Combat.CASTING_SPELL] = spell
            }
        }
    }

    if (pawn is Player) {
        pawn.setVarp(Combat.PRIORITY_PID_VARP, target.index)
        // The one autocast entry point: loads the resolved autocast spell (weapon, spellbook and mode checked centrally) or drops a
        // stale one; a pending manual cast wins for this attack. Powered staves never resolve (AutocastWeapons).
        gg.rsmod.plugins.content.combat.magic.Autocast.prepareAttack(pawn, target)
    }

    val strategy = CombatConfigs.getCombatStrategy(pawn)
    // RCV-005: an npc with data-driven attacks (NpcAttacks, Void Attack.kt) approaches to its longest usable attack.
    val dataAttackRange = (pawn as? Npc)?.let { gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackRange(it) }
    val attackRange = dataAttackRange ?: strategy.getAttackRange(pawn)

    // RCV-005 root cause: npcs had no leash model. Void CombatMovement.withinAggro - an npc fights on
    // while the target stays inside its spawn leash, and gives up only once the target leaves it.
    if (pawn is Npc && !NpcLeash.mayPursue(pawn, target, attackRange)) {
        if (gg.rsmod.game.model.BreachTrace.watches(pawn.id)) {
            gg.rsmod.game.model.BreachTrace.log("leash") {
                "npc=${pawn.id}#${pawn.index} at=${pawn.tile.x},${pawn.tile.z} spawn=${pawn.spawnTile.x},${pawn.spawnTile.z} " +
                    "target=${(target as? Player)?.username ?: target.toString()}@${target.tile.x},${target.tile.z} result=released-walk-home"
            }
        }
        pawn.stopMovement()
        pawn.resetFacePawn()
        Combat.reset(pawn)
        // Breach audit 2026-09-27 (B5): the npc used to stay exactly where the leash ran out, a free safespot at the edge.
        // It goes back towards its spawn tile instead (OSRS: an npc that loses its target returns to its spawn area).
        NpcLeash.returnHome(pawn)
        return false
    }

    var pathFound: Boolean

    if (pawn is Npc && dataAttackRange != null) {
        // RCV-005 root cause (owner: GWD generals/minions "niet meer aggressive"): the melee shortcut below treated a
        // large boss within `size` tiles, or a minion standing diagonally, as in reach; it then stopped walking but no
        // attack section could hit from there, so it stood still forever. A data npc is in reach only when one of
        // its sections can actually hit (Void Attack.withinRange); otherwise it keeps approaching.
        //
        // Breach audit 2026-09-27 (B1): the approach itself used a second, different distance rule. The route was asked for
        // the npc's LONGEST range as a touch radius, which the npc pathfinder measures as a box stretching north-east from the
        // target's south-west tile, while withinRange measures from the npc's nearest tile with line of sight. The route could
        // "arrive" where no section can hit (e.g. a 1x1 range-8 npc at +7,+8: box says arrived, withinRange says 11 > 9), and
        // the npc then stood there every tick. Now withinRange is the only "arrived" rule: in reach -> stop and attack; not in
        // reach -> step towards the target (a melee approach), and look again next tick.
        val inReach = gg.rsmod.plugins.content.combat.attack.NpcAttacks.hasValidAttack(pawn, target)
        if (gg.rsmod.game.model.BreachTrace.watches(pawn.id)) {
            gg.rsmod.game.model.BreachTrace.log("reach") {
                "npc=${pawn.id}#${pawn.index} at=${pawn.tile.x},${pawn.tile.z} target=${(target as? Player)?.username ?: target.toString()}@${target.tile.x},${target.tile.z} " +
                    "inReach=$inReach frozen=${pawn.timers.has(FROZEN_TIMER)} ${gg.rsmod.plugins.content.combat.attack.NpcAttacks.explainReach(pawn, target)}"
            }
        }
        if (inReach) {
            pawn.stopMovement()
            pathFound = true
        } else {
            // Owner live test 2026-09-28: a player standing inside a breach monster's body was never attacked again - no section
            // can hit from an overlapping footprint and the melee approach found nothing to walk to. OSRS: the npc steps off its
            // target first. The monster walks to a free spot of its whole body next to the target, then fights on from there.
            if (gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(pawn) && overlaps(pawn, target)) {
                val spot = gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.summonTile(pawn.world, target, pawn.getSize().coerceAtLeast(1), nearestTo = pawn.tile)
                if (gg.rsmod.game.model.BreachTrace.watches(pawn.id)) {
                    gg.rsmod.game.model.BreachTrace.log("stepoff") { "npc=${pawn.id}#${pawn.index} from=${pawn.tile.x},${pawn.tile.z} to=${spot.x},${spot.z} target=${target.tile.x},${target.tile.z}" }
                }
                pawn.walkTo(spot, detectCollision = true)
                return true
            }
            PawnPathAction.walkTo(it, pawn, target, interactionRange = 1, lineOfSight = false, waitForArrival = false)
            return true
        }
    } else {
        pathFound = PawnPathAction.walkTo(it, pawn, target, interactionRange = attackRange, lineOfSight = false)
    }

    if (pawn is Npc && dataAttackRange != null) {
        // handled above
    } else if (strategy == MeleeCombatStrategy && pawn.tile.getDistance(target.tile) <= pawn.getSize()) {
        pathFound = true
    }

    if (!pathFound) {
        pawn.stopMovement()
        if (pawn.entityType.isNpc) {
            /**
             * RCV-005 owner retest 2026-09-13 ("npcs vergeten dat ze je moeten blijven attacken als je
             * wegloopt"): npcs keep trying to reach their target. The old rule dropped the fight as
             * soon as one route failed in single combat with the target more than 6 tiles away, which
             * a running player triggers every time. Void keeps trying while the target is inside the
             * npc's spawn leash; [NpcLeash.mayPursue] above ends the fight once it leaves it.
             */
            return true
        }
        if (pawn is Player) {
            when {
                pawn.timers.has(FROZEN_TIMER) -> pawn.message(Entity.MAGIC_STOPS_YOU_FROM_MOVING)
                pawn.timers.has(STUN_TIMER) -> pawn.message(Entity.YOURE_STUNNED)
                else -> pawn.message(Entity.YOU_CANT_REACH_THAT)
            }
            pawn.clearMapFlag()
        }
        pawn.resetFacePawn()
        Combat.reset(pawn)
        return false
    }

    // Granite maul Quick Smash is instant and gives no attack cooldown, so it fires before the attack delay check.
    if (pawn is Player && gg.rsmod.plugins.content.items.osrs.GraniteMaul.onCombatCycle(pawn, target)) {
        return true
    }

    if (Combat.isAttackDelayReady(pawn)) {
        if (Combat.canAttack(pawn, target, strategy)) {
            if (pawn is Npc && dataAttackRange != null && !gg.rsmod.plugins.content.combat.attack.NpcAttacks.hasValidAttack(pawn, target)) {
                return true // no attack section can hit from here yet: keep approaching instead of standing still
            }
            // A player's melee swing needs the real reach at the moment it fires (halberds 2 tiles, others adjacent);
            // the target may have stepped away after the route finished - keep chasing instead of hitting from afar.
            if (pawn is Player && strategy == MeleeCombatStrategy &&
                !gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.isSalamander(pawn.getEquipment(EquipmentType.WEAPON)?.id) &&
                !Combat.inMeleeReach(pawn, target, attackRange)
            ) {
                return true
            }
            pawn.stopMovement()
            // Check if either the attacker or the target is in a multi-combat area
            val pawnInMulti = pawn.tile.isMulti(pawn.world)
            val targetInMulti = target.tile.isMulti(target.world)

            // Deadman guards (OSRS Wiki): "Multiple guards are able to attack the player regardless
            // of the location's multicombat area status" - a guard is never held back by, and never
            // holds back, the single-combat "already under attack" rule.
            val guardInvolved = CityGuards.ignoresSingleCombat(pawn) || CityGuards.ignoresSingleCombat(target)
            // Audit C-10 (owner: OSRS everywhere): a player fighting an ordinary npc in single-way combat is protected from PKers
            // by the normal "already under attack" rule below; the former Deadman exception that released the npc is removed.
            // OSRS Wiki "Deadman: Annihilation": "Multiple players can attack the same boss that spawned through a breach in a
            // single-way combat zone" - a breach monster is never reserved by the first player on it. The player side keeps the
            // normal rule ("players can also be attacked by another player in a single area if they are not being attacked by
            // the boss").
            val sharedBreachTarget = target is Npc && gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(target) && pawn is Player
            // Breach audit 2026-09-27 (B6): a breach monster that is already fighting a player keeps doing so when other players
            // hit it ("multiple players can attack the same boss") or when a PKer hits its target - it was released by the
            // single-way rules below. Who may start fighting whom is decided by the aggro search and the players' own checks.
            val breachAttacker = pawn is Npc && gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(pawn)
            // ... but in single-way a player still fights one npc at a time (in-engine test run: two monsters shared one player).
            val otherNpcOnTarget =
                !targetInMulti && target.isAttackedByOtherThan(pawn) && target.liveLastHitBy() is Npc
            val traceSingle =
                gg.rsmod.game.model.BreachTrace.active() &&
                    ((pawn is Npc && gg.rsmod.game.model.BreachTrace.watches(pawn.id)) || (target is Npc && gg.rsmod.game.model.BreachTrace.watches(target.id)) ||
                        (pawn is Player && target is Player))
            val ruling = { result: String ->
                if (traceSingle) {
                    gg.rsmod.game.model.BreachTrace.log("single") {
                        "attacker=${(pawn as? Player)?.username ?: pawn.toString()} target=${(target as? Player)?.username ?: target.toString()} " +
                            "pawnMulti=$pawnInMulti targetMulti=$targetInMulti guard=$guardInvolved breachAttacker=$breachAttacker " +
                            "sharedBreachTarget=$sharedBreachTarget otherNpcOnTarget=$otherNpcOnTarget " +
                            "pawnLastHitBy=${pawn.getLastHitBy()?.let { (it as? Player)?.username ?: it.toString() }} " +
                            "targetLastHitBy=${target.getLastHitBy()?.let { (it as? Player)?.username ?: it.toString() }} result=$result"
                    }
                }
            }
            if (breachAttacker && otherNpcOnTarget) {
                ruling("refused:breach-npc-target-busy")
                Combat.reset(pawn)
                return false
            }
            if (guardInvolved || breachAttacker) {
                // no single-combat restriction
            } else if (pawnInMulti || targetInMulti) {
                if (!pawnInMulti && pawn.isAttackedByOtherThan(target)) {
                    ruling("refused:multi-edge-attacker-under-attack")
                    if (pawn is Player) {
                        pawn.message("I'm already under attack!")
                    }
                    Combat.reset(pawn)
                    return false
                }
                if (!targetInMulti && target.isAttackedByOtherThan(pawn) && !sharedBreachTarget) {
                    ruling("refused:multi-edge-target-busy")
                    if (pawn is Player) {
                        if (target is Player) {
                            pawn.message("Someone is already fighting this player.")
                        }
                        else {
                            pawn.message("Someone is already fighting this.")
                        }
                    }
                    Combat.reset(pawn)
                    return false
                }
            }
            else {
                if (pawn.isAttackedByOtherThan(target)) {
                    ruling("refused:single-attacker-under-attack")
                    if (pawn is Player) {
                        pawn.message("I'm already under attack!")
                    }
                    Combat.reset(pawn)
                    return false
                }
                if (target.isAttackedByOtherThan(pawn) && !sharedBreachTarget) {
                    ruling("refused:single-target-busy")
                    if (pawn is Player) {
                        if (target is Player) {
                            pawn.message("Someone is already fighting this player.")
                        }
                        else {
                            pawn.message("Someone is already fighting this.")
                        }
                    }
                    Combat.reset(pawn)
                    return false
                }
            }

            ruling("allowed")

            if (pawn is Player) {
                if (target is Npc && target.combatDef.slayerReq > pawn.skills.getMaxLevel(Skills.SLAYER)) {
                    pawn.message("You need a higher Slayer level to know how to wound this monster.")
                    Combat.reset(pawn)
                    return false
                }

                if (AttackTab.isSpecialEnabled(pawn) &&
                    pawn.getEquipment(EquipmentType.WEAPON) != null
                ) {
                    AttackTab.disableSpecial(pawn)
                    when (SpecialAttacks.perform(pawn, target, world)) {
                        SpecialAttacks.Outcome.PERFORMED -> {
                            Combat.postAttack(pawn, target)
                            return true
                        }
                        // Audit C-15: a special that could not be performed costs nothing and starts no attack delay; the special
                        // bar is already off, so the fight simply continues with a normal attack from the next cycle.
                        SpecialAttacks.Outcome.FAILED -> return true
                        SpecialAttacks.Outcome.NOT_USED -> {}
                    }
                }
            }

            // An attack that throws after its animation, runes and projectile have gone out must still start the attack delay:
            // without it the next cycle attacked again at once (a crash turned into a 1-tick spam).
            try {
                if (pawn is Npc && gg.rsmod.plugins.content.combat.attack.NpcAttacks.handles(pawn)) {
                    // RCV-005: the npc's own attack sections (Void Attack.kt) - style anims, gfx, projectiles,
                    // sounds, hits and impact effects. No valid section from here: keep approaching.
                    if (!gg.rsmod.plugins.content.combat.attack.NpcAttacks.attack(pawn, target)) {
                        return true
                    }
                } else {
                    strategy.attack(pawn, target)
                }
            } catch (e: Exception) {
                Combat.postAttack(pawn, target)
                throw e
            }
            Combat.postAttack(pawn, target)
        } else {
            Combat.reset(pawn)
            return false
        }
    }
    return true
}
