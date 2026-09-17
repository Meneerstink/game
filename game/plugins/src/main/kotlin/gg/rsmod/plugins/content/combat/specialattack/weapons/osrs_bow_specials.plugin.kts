package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.items.osrs.RevenantBows
import gg.rsmod.plugins.content.items.osrs.ScorchingBow
import gg.rsmod.plugins.content.items.osrs.Tonalztics

/*
 * OSRS-IMPORT batch bows specials (rules and sources in RevenantBows, ScorchingBow, Tonalztics). Looks: the imported OSRS sequences
 * HUMAN_SPECIAL01_WEBWEAVER and HUMAN_GLAIVE_RALOS01_(UN)CHARGED_SPECIAL, FX_WEBWEAVER01_LAUNCH / IMPACT and the VFX_SCORCHING_BOW_*
 * spotanims; the projectiles in flight keep their 667 graphics where OSRS has no named travel spotanim (ADAPTED_TO_667). Successive hits land one tick apart (ADAPTED, spacing unsourced).
 */

/* Webweaver bow - Swarm: 50 %, four hits with doubled accuracy, each up to 40 % of the max hit rounded up; one ether charge. */
SpecialAttacks.register(RevenantBows.SWARM_ENERGY, Items.WEBWEAVER_BOW) {
    val victim = target
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_SPECIAL01_WEBWEAVER)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WEBWEAVER_LAUNCH, 90) // Zenyte-lineage SWARM: Graphics(2354, 0, 90)
    player.playSound(Sfx.SHORTBOW)
    val delay = RangedCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
    val maxHit = RevenantBows.swarmMaxHit(RangedCombatFormula.getMaxHit(player, victim))
    repeat(RevenantBows.SWARM_HITS) { index ->
        world.spawn(player.createProjectile(victim, 249, RangedProjectile.RUNE_ARROW.type))
        val landHit = RangedCombatFormula.getAccuracy(player, victim, RevenantBows.SWARM_ACCURACY) >= world.randomDouble()
        val swarmHit = player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = delay + index, hitType = HitType.RANGE)
        if (index == 0) swarmHit.hit.addAction { victim.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WEBWEAVER_IMPACT) }
    }
    RevenantBows.afterShot(player)
}

/* Scorching bow - Scorching shackles: 25 %, demons only; a normal shot, a 20-tick bind and 5 burn damage (1 every 4 ticks). */
SpecialAttacks.register(ScorchingBow.SHACKLES_ENERGY, Items.SCORCHING_BOW) {
    val victim = target
    if (!ScorchingBow.isDemon(victim)) {
        player.message(ScorchingBow.NOT_DEMON_MESSAGE)
        // ADAPTED: this engine drains the energy before the special runs; the special does not work, so the energy is returned.
        AttackTab.setEnergy(player, minOf(100, AttackTab.getEnergy(player) + ScorchingBow.SHACKLES_ENERGY))
        return@register
    }
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.SCORCHING_BOW_SPECIAL_ATTACK)
    player.playSound(Sfx.SHORTBOW)
    val delay = RangedCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
    if (gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.rangedShot(
            player,
            victim,
            delay = delay,
            projectileGfx = gg.rsmod.plugins.content.items.osrs.OsrsGfx.SCORCHING_BOW_PROJECTILE,
        ) == -1
    ) {
        return@register
    }
    victim.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.SCORCHING_BOW_IMPACT, delay = delay * 30)
    victim.freeze(ScorchingBow.BIND_TICKS)
    repeat(ScorchingBow.BURN_HITS) { index -> victim.hit(damage = 1, delay = delay + index * ScorchingBow.BURN_INTERVAL_TICKS) }
}

fun divisionDrain(victim: Pawn) {
    when (victim) {
        is Npc -> {
            val drain = Tonalztics.divisionDrain(victim.stats.getCurrentLevel(NpcSkills.MAGIC))
            val level = victim.stats.getCurrentLevel(NpcSkills.DEFENCE)
            victim.stats.setCurrentLevel(NpcSkills.DEFENCE, maxOf(0, level - drain))
        }
        is Player -> victim.skills.alterCurrentLevel(Skills.DEFENCE, -Tonalztics.divisionDrain(victim.skills.getCurrentLevel(Skills.MAGIC)))
    }
}

/*
 * Tonalztics of Ralos - Division: 50 %, accuracy x1.5, each successful hit lowers Defence by 1/8 of the target's Magic
 * level. OSRS Wiki "Tonalztics of Ralos" (raw wikitext, re-checked 2026-09-17), quoting Mod Ash: "When used on NPCs, the
 * defence reduction from a successful first hit will apply for the calculation of the second hit. However, this is not
 * the case when used in PvP" - "on an NPC ... any defence reduction from the 1st calculated hit can help with the 2nd
 * roll even though it's in the same tick. However, on a player ... the target's effective defence would not be
 * recalculated within that tick, so the 2nd roll may not be helped." NPC targets therefore drain immediately (so the
 * live stat read inside the next accuracy roll sees it); Player targets defer every landed hit's drain until after
 * both accuracy rolls, so neither roll reads the other hit's reduction.
 */
Tonalztics.ALL.forEach { weapon ->
    SpecialAttacks.register(Tonalztics.DIVISION_ENERGY, weapon) {
        val victim = target
        player.animate(
            if (weapon == Items.TONALZTICS_OF_RALOS) {
                gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL
            } else {
                gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_GLAIVE_RALOS01_UNCHARGED_SPECIAL
            },
        )
        player.playSound(Sfx.THROWN)
        val delay = RangedCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
        var deferredPlayerDrains = 0
        repeat(Tonalztics.hits(player.getEquipment(EquipmentType.WEAPON))) { index ->
            world.spawn(player.createProjectile(victim, RangedProjectile.DRAGON_THROWNAXE.gfx, RangedProjectile.DRAGON_THROWNAXE.type))
            val landHit = RangedCombatFormula.getAccuracy(player, victim, Tonalztics.DIVISION_ACCURACY) >= world.randomDouble()
            player.dealHit(target = victim, maxHit = RangedCombatFormula.getMaxHit(player, victim), landHit = landHit, delay = delay + index, hitType = HitType.RANGE)
            if (landHit) {
                if (victim is Player) deferredPlayerDrains++ else divisionDrain(victim)
            }
        }
        repeat(deferredPlayerDrains) { divisionDrain(victim) }
        Tonalztics.afterThrow(player)
    }
}
