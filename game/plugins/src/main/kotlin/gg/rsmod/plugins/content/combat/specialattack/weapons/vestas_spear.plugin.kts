package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.attr.SPEAR_WALL
import gg.rsmod.game.model.timer.SPEAR_WALL_TIMER
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks


on_timer(SPEAR_WALL_TIMER) {
    player.attr.remove(SPEAR_WALL)
}

/** Audit I-05: OSRS Spear Wall - melee immunity for 8 ticks (was 9). */
val SPEAR_WALL_TICKS = 8

/** Audit I-05: OSRS Spear Wall hits up to 16 targets in total, the primary target included (was 10). */
val SPEAR_WALL_MAX_TARGETS = 16

SpecialAttacks.register(50, Items.VESTAS_SPEAR, Items.VESTAS_SPEAR_DEG) {
    player.animate(Anims.VESTAS_SPEAR_SPECIAL)
    player.graphic(Gfx.VESTAS_SPEAR_SPECIAL, 0, 0)
    player.playSound(Sfx.CLEAVE)

    // The x1.2 accuracy/damage and the 20 % splash damage are kept from before (not wiki-verified, see audit I-05).
    val primary = target
    val maxHit = MeleeCombatFormula.getMaxHit(player, primary, specialAttackMultiplier = 1.2)
    val accuracy = MeleeCombatFormula.getAccuracy(player, primary, specialAttackMultiplier = 1.2)
    val landHit = accuracy >= world.randomDouble()
    val delay = 1
    player.dealHit(target = primary, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.MELEE)
    player.attr[SPEAR_WALL] = true
    player.timers[SPEAR_WALL_TIMER] = SPEAR_WALL_TICKS

    if (player.tile.isMulti(player.world)) {
        val attacker = player
        val targets = ArrayList<Pawn>()
        // Audit I-05: never the attacker itself (it stood at distance 0 and was a candidate before).
        val playerTargets = attacker.world.players.all { p ->
            p != primary &&
                p != attacker &&
                p.tile.getDistance(attacker.tile) <= 1 &&
                p.tile.isMulti(attacker.world)
        }
        val npcTargets = attacker.world.npcs.all { npc ->
            val nearestTile = npc.nearestTile(attacker.tile)
            npc != primary &&
                nearestTile.getDistance(attacker.tile) <= 1 &&
                nearestTile.isMulti(attacker.world)
        }
        for (other in playerTargets + npcTargets) {
            if (targets.size < SPEAR_WALL_MAX_TARGETS - 1) {
                targets.add(other)
            }
        }

        for (other in targets) {
            // Audit I-05: every extra target gets its own accuracy roll instead of sharing the primary target's.
            val otherMax = MeleeCombatFormula.getMaxHit(attacker, other, specialAttackMultiplier = 1.2)
            val otherLands = MeleeCombatFormula.getAccuracy(attacker, other, specialAttackMultiplier = 1.2) >= world.randomDouble()
            attacker.dealHit(target = other, maxHit = (otherMax * .2), landHit = otherLands, delay = delay, hitType = HitType.MELEE)
        }
    }
}
