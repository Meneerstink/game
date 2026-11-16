package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLootItems
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * The CoX weapons' specials (OSRS Wiki, raw in work/claude-logs/cox/Elder_maul.wiki + wiki/dinhs.wiki, 16-11).
 *
 * Elder maul 'Pulverize': 50% energy, accuracy +25%, a successful hit lowers the target's CURRENT Defence by 35% (rounded down, stacking,
 * each from the current level). Against Tekton the first special from an elder maul always hits; a later miss still lowers his Defence
 * by 5%. Look: gameval HUMAN_ELDER_MAUL_SPEC 11124, SPOTANIM_ELDER_MAUL_SPECIAL 2804 on the player, _IMPACT 2805 on the target (batches
 * cox-maul-seq / cox-maul-fx). ONZEKER: the sound (no Wiki audio; the Dragon warhammer's smash sound is used, as its weaker twin).
 *
 * Dinh's bulwark 'Shield Bash': 50% energy, accuracy +20% rolled against crush defence; in multicombat up to 10 targets within 5 tiles
 * (11x11), the chosen target hit twice and the others once; every target hit loses 5% of its highest offensive stat (not on the primary's
 * second hit; ties: Attack and Strength, then Ranged, then Magic). Anim HUMAN_DINHS_BULWARK_BASH 7511. ONZEKER: no damage multiplier is
 * stated (x1), no gfx/sound on the Wiki.
 */
private val tektonHit = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Npc, Boolean>())

fun isTekton(n: Npc) = world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, n.id).name.equals("Tekton", ignoreCase = true)

fun lowerDefence(victim: Pawn, percent: Int) {
    when (victim) {
        is Player -> { val l = victim.skills.getCurrentLevel(Skills.DEFENCE); victim.skills.setCurrentLevel(Skills.DEFENCE, l - l * percent / 100) }
        is Npc -> { val l = victim.stats.getCurrentLevel(NpcSkills.DEFENCE); victim.stats.setCurrentLevel(NpcSkills.DEFENCE, l - l * percent / 100) }
    }
}

CoxLootItems.local(21003)?.let { maul ->
    SpecialAttacks.register(50, maul) {
        val victim = target
        CoxIds.seq(11124)?.let { player.animate(it) }
        CoxIds.spot(2804)?.let { player.graphic(it) }
        player.playSound(Sfx.TZHAAR_KET_OM_CRUSH)
        val maxHit = MeleeCombatFormula.getMaxHit(player, victim)
        val firstOnTekton = victim is Npc && isTekton(victim) && tektonHit.add(victim)
        val landHit = firstOnTekton || MeleeCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = 1.25) >= world.randomDouble()
        val pawnHit = player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
        val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
        pawnHit.hit.addAction {
            CoxIds.spot(2805)?.let { victim.graphic(it) }
            when {
                landHit && dealt > 0 -> lowerDefence(victim, 35)
                victim is Npc && isTekton(victim) -> lowerDefence(victim, 5)
            }
        }
    }
}

/** Wiki: the highest offensive stat loses 5%; ties drain Attack and Strength, then Ranged, then Magic. */
fun drainHighest(victim: Pawn) {
    if (victim !is Npc) return
    val s = victim.stats
    val melee = maxOf(s.getCurrentLevel(NpcSkills.ATTACK), s.getCurrentLevel(NpcSkills.STRENGTH))
    val top = maxOf(melee, s.getCurrentLevel(NpcSkills.RANGED), s.getCurrentLevel(NpcSkills.MAGIC))
    val drain = { k: Int -> val l = s.getCurrentLevel(k); s.setCurrentLevel(k, l - l * 5 / 100) }
    when (top) {
        melee -> { drain(NpcSkills.ATTACK); drain(NpcSkills.STRENGTH) }
        s.getCurrentLevel(NpcSkills.RANGED) -> drain(NpcSkills.RANGED)
        else -> drain(NpcSkills.MAGIC)
    }
}

CoxLootItems.local(21015)?.let { bulwark ->
    SpecialAttacks.register(50, bulwark) {
        val primary = target
        CoxIds.seq(7511)?.let { player.animate(it) }
        fun bash(victim: Pawn, drains: Boolean) {
            val max = MeleeCombatFormula.getMaxHit(player, victim)
            val lands = MeleeCombatFormula.getAccuracyAgainst(player, victim, 1.2, StyleType.CRUSH) >= world.randomDouble()
            val h = player.dealHit(target = victim, maxHit = max, landHit = lands, delay = 1, hitType = HitType.MELEE)
            if (drains && lands) h.hit.addAction { drainHighest(victim) }
        }
        bash(primary, true)
        bash(primary, false)
        if (!player.tile.isMulti(world)) return@register
        val others = (world.npcs.all { n ->
            n != primary && n.nearestTile(player.tile).getDistance(player.tile) <= 5 && n.nearestTile(player.tile).isMulti(world) &&
                gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.canSplash(player, n)
        } + world.players.all { p ->
            p != primary && p != player && p.tile.getDistance(player.tile) <= 5 &&
                gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.canSplash(player, p)
        }).take(9)
        others.forEach { bash(it, true) }
    }
}
