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
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab

set_combat_logic {
    if (pawn.getCombatTarget() != null) {
        // Deadman (owner 2026-09-17): every deliberate player-on-player attack - whatever started
        // it (Attack option, spell, special, ranged) - skulls the attacker; engine auto-retaliation
        // is the only exemption. This is the single entry point every attack style passes through.
        val attacker = pawn
        val target = attacker.getCombatTarget()
        if (attacker is Player && target is Player && attacker.attr[PvpSkull.AUTO_RETALIATING_ATTR] != true) {
            PvpSkull.onPlayerInitiatedAttack(attacker = attacker, victim = target)
        }
        // RC-1: a player's combat loop is persistent - it survives prayers, eating, equipping,
        // familiar commands and other STRONG/soft actions; only a hard interruption (walk, new
        // interaction, teleport, death) ends it. NPC loops keep the old head-only scheduling so
        // boss/familiar scripts that queue waiting tasks during combat keep their pacing.
        pawn.queue(persistent = pawn is Player) {
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
                PvpSkull.onPlayerInitiatedAttack(attacker = player, victim = target)
                player.attack(target)
            } else {
                player.message("You decide not to attack.")
            }
        }
        return@on_player_option
    }

    PvpSkull.onPlayerInitiatedAttack(attacker = player, victim = target)
    player.attack(target)
}

on_timer(ACTIVE_COMBAT_TIMER) {
    val pawn = pawn
    if (pawn.attr.has(AGGRESSOR)) {
        pawn.attr.remove(AGGRESSOR)
    }
}

suspend fun cycle(it: QueueTask): Boolean {
    val pawn = it.pawn
    val target = pawn.getCombatTarget() ?: return false

    if (!pawn.lock.canAttack()) {
        Combat.reset(pawn)
        return false
    }

    if (pawn.attr[FACING_PAWN_ATTR] != target) {
        pawn.facePawn(target)
    }

    if (!Combat.canEngage(pawn, target)) {
        Combat.reset(pawn)
        pawn.resetFacePawn()
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
        // Powered staves "cannot be used to autocast spells" (OSRS Wiki "Powered staff").
        if (!pawn.attr.has(Combat.CASTING_SPELL) && pawn.getVarp(Combat.SELECTED_AUTOCAST_VARP) != 0 &&
            gg.rsmod.plugins.content.items.osrs.PoweredStaves.wielded(pawn) == null
        ) {
            val spell = CombatSpell.values.firstOrNull { it.autoCastId == pawn.getVarp(Combat.SELECTED_AUTOCAST_VARP) }
            if (spell != null) {
                pawn.attr[Combat.CASTING_SPELL] = spell
            }
        }
    }

    val strategy = CombatConfigs.getCombatStrategy(pawn)
    // RCV-005: an npc with data-driven attacks (NpcAttacks, Void Attack.kt) approaches to its longest usable attack.
    val dataAttackRange = (pawn as? Npc)?.let { gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackRange(it) }
    val attackRange = dataAttackRange ?: strategy.getAttackRange(pawn)

    // RCV-005 root cause: npcs had no leash model. Void CombatMovement.withinAggro - an npc fights on
    // while the target stays inside its spawn leash, and gives up only once the target leaves it.
    if (pawn is Npc && !NpcLeash.mayPursue(pawn, target, attackRange)) {
        pawn.stopMovement()
        pawn.resetFacePawn()
        Combat.reset(pawn)
        return false
    }

    var pathFound = PawnPathAction.walkTo(it, pawn, target, interactionRange = attackRange, lineOfSight = false)

    if (pawn is Npc && dataAttackRange != null) {
        // RCV-005 root cause (owner: GWD generals/minions "niet meer aggressive"): the melee shortcut below treated a
        // large boss within `size` tiles, or a minion standing diagonally, as in reach; it then stopped walking but no
        // attack section could hit from there, so it stood still forever. A data npc is in reach only when one of
        // its sections can actually hit (Void Attack.withinRange); otherwise it keeps approaching.
        if (gg.rsmod.plugins.content.combat.attack.NpcAttacks.hasValidAttack(pawn, target)) {
            pathFound = true
        }
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
            pawn.stopMovement()
            // Check if either the attacker or the target is in a multi-combat area
            val pawnInMulti = pawn.tile.isMulti(pawn.world)
            val targetInMulti = target.tile.isMulti(target.world)

            // Deadman guards (OSRS Wiki): "Multiple guards are able to attack the player regardless
            // of the location's multicombat area status" - a guard is never held back by, and never
            // holds back, the single-combat "already under attack" rule.
            val guardInvolved = CityGuards.ignoresSingleCombat(pawn) || CityGuards.ignoresSingleCombat(target)
            if (guardInvolved) {
                // no single-combat restriction
            } else if (pawnInMulti || targetInMulti) {
                if (!pawnInMulti && pawn.isBeingAttacked() && pawn.getLastHitBy() != target) {
                    if (pawn is Player) {
                        pawn.message("I'm already under attack!")
                    }
                    Combat.reset(pawn)
                    return false
                }
                if (!targetInMulti && target.isBeingAttacked() && target.getLastHitBy() != pawn) {
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
                if (pawn.isBeingAttacked() && pawn.getLastHitBy() != target) {
                    if (pawn is Player) {
                        pawn.message("I'm already under attack!")
                    }
                    Combat.reset(pawn)
                    return false
                }
                if (target.isBeingAttacked() && target.getLastHitBy() != pawn) {
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
                    if (SpecialAttacks.execute(pawn, target, world)) {
                        Combat.postAttack(pawn, target)
                        return true
                    }
                }
            }

            if (pawn is Npc && gg.rsmod.plugins.content.combat.attack.NpcAttacks.handles(pawn)) {
                // RCV-005: the npc's own attack sections (Void Attack.kt) - style anims, gfx, projectiles,
                // sounds, hits and impact effects. No valid section from here: keep approaching.
                if (!gg.rsmod.plugins.content.combat.attack.NpcAttacks.attack(pawn, target)) {
                    return true
                }
            } else {
                strategy.attack(pawn, target)
            }
            Combat.postAttack(pawn, target)
        } else {
            Combat.reset(pawn)
            return false
        }
    }
    return true
}
