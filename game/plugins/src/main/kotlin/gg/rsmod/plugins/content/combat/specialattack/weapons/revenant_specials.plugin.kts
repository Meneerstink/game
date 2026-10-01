package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.items.osrs.OsrsGfx
import gg.rsmod.plugins.content.items.osrs.OsrsSeq
import gg.rsmod.plugins.content.items.osrs.RevenantWeapons

/*
 * Revenant weapon specials (owner 2026-09-29, OSRS Wiki item pages; rules of the weapons in RevenantWeapons).
 *
 * Ursine chainmace - "Bear Down": 50 %, "only usable whilst charged"; "Doubled accuracy, and upon a successful hit, also deals 20
 * damage over 6 seconds, prevents running for 3.6 seconds (6 ticks), and reduces the target's Agility level by 20". ADAPTED: the
 * 20 damage lands as 5 hits of 4, one every 2 ticks (10 ticks = 6 seconds; the page gives the total only); the look is the imported
 * HUMAN_SPECIAL02_URSINE with FX_URSINE02_SPECIAL (gameval names; which of the two sets OSRS plays is not stated).
 *
 * Accursed sceptre - "Condemn": "consumes 50% of the player's special attack energy and 1 revenant ether", "a 50% increase to max
 * hit and accuracy"; on a successful hit it "drains the target's Defence and Magic by 15%", "capping at 15% total reduction". Look:
 * HUMAN_SPECIAL_ACCURSED with SPELLS_VETION01_TRAVEL02 / SPELLS_VETION01_SPECIAL (gameval).
 */

val BEAR_DOWN_ENERGY = 50
val BLEED_HITS = 5
val BLEED_DAMAGE = 4
val BLEED_INTERVAL = 2
val NO_RUN_TICKS = 6
val AGILITY_DRAIN = 20
val CONDEMN_ENERGY = 50
val CONDEMN_MULTIPLIER = 1.5
val CONDEMN_DRAIN = 0.15

fun gg.rsmod.plugins.content.combat.specialattack.CombatContext.refuseSpecial(message: String) {
    player.message(message)
    specialFailed()
}

SpecialAttacks.register(BEAR_DOWN_ENERGY, Items.URSINE_CHAINMACE) {
    val victim: Pawn = target
    player.animate(OsrsSeq.HUMAN_SPECIAL02_URSINE)
    player.graphic(OsrsGfx.URSINE_SPECIAL02)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim)
    val landHit = MeleeCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = 2.0) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    RevenantWeapons.afterAttack(player)
    if (!landHit) return@register
    val world = player.world
    world.queue {
        wait(1)
        // "reduces the target's Agility level by 20" and "prevents running for 3.6 seconds (6 ticks)".
        if (victim is Player) {
            victim.skills.setCurrentLevel(Skills.AGILITY, maxOf(0, victim.skills.getCurrentLevel(Skills.AGILITY) - AGILITY_DRAIN))
            val wasRunning = victim.varps[173].state == 1
            world.queue {
                repeat(NO_RUN_TICKS) {
                    if (victim.isOnline && victim.varps[173].state == 1) victim.varps.setState(173, 0)
                    wait(1)
                }
                if (wasRunning && victim.isOnline && victim.varps[173].state == 0) victim.varps.setState(173, 1)
            }
        }
        // "deals 20 damage over 6 seconds".
        repeat(BLEED_HITS) {
            if (victim.isDead() || (victim is Player && !victim.isOnline)) return@queue
            victim.hit(damage = BLEED_DAMAGE, type = HitType.REGULAR_HIT)
            wait(BLEED_INTERVAL)
        }
    }
}

listOf(Items.ACCURSED_SCEPTRE, Items.ACCURSED_SCEPTRE_A).forEach { sceptre ->
    SpecialAttacks.register(CONDEMN_ENERGY, sceptre) {
        val victim: Pawn = target
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == sceptre }
        if (weapon == null || RevenantWeapons.ether(weapon) <= 0) {
            refuseSpecial("Your sceptre has no revenant ether left.")
            return@register
        }
        player.animate(OsrsSeq.HUMAN_SPECIAL_ACCURSED)
        val projectile = player.createProjectile(victim, gfx = OsrsGfx.ACCURSED_TRAVEL02, type = ProjectileType.MAGIC)
        world.spawn(projectile)
        val hitDelay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(player, victim, CONDEMN_MULTIPLIER) >= world.randomDouble()
        val maxHit = kotlin.math.floor(MagicCombatFormula.getMaxHit(player, victim) * CONDEMN_MULTIPLIER)
        player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = hitDelay, hitType = HitType.MAGIC)
            .hit.addAction {
                if (!landHit) return@addAction
                victim.graphic(OsrsGfx.ACCURSED_SPECIAL)
                // "drains the target's Defence and Magic by 15%", never below 85 % of the base level (the 15 % cap).
                listOf(Skills.DEFENCE to NpcSkills.DEFENCE, Skills.MAGIC to NpcSkills.MAGIC).forEach { (skill, npcSkill) ->
                    when (victim) {
                        is Player -> {
                            val base = victim.skills.getMaxLevel(skill)
                            val floor = base - (base * CONDEMN_DRAIN).toInt()
                            val now = victim.skills.getCurrentLevel(skill)
                            if (now > floor) victim.skills.setCurrentLevel(skill, maxOf(floor, now - (base * CONDEMN_DRAIN).toInt()))
                        }
                        is Npc -> {
                            val base = victim.stats.getMaxLevel(npcSkill)
                            val floor = base - (base * CONDEMN_DRAIN).toInt()
                            val now = victim.stats.getCurrentLevel(npcSkill)
                            if (now > floor) victim.stats.alterCurrentLevel(npcSkill, -(now - maxOf(floor, now - (base * CONDEMN_DRAIN).toInt())))
                        }
                    }
                }
            }
        // "1 revenant ether" for the special.
        RevenantWeapons.afterAttack(player)
    }
}
