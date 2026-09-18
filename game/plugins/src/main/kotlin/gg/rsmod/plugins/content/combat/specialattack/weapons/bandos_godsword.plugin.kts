package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Bandos Godsword ("Warstrike") - one of the variants armadyl_godsword.plugin.kts's own comment
 * flagged as not implemented that pass. Real mechanic (OSRS Wiki, verified): 50% energy, doubled
 * accuracy, +21% max hit, single hit; on a successful hit drains the real damage dealt 1:1 from
 * the target's stats in order Defence -> Strength -> Prayer(points) -> Attack -> Magic -> Ranged,
 * with any leftover once a stat hits 0 spilling into the next one in the list.
 *
 * NPC targets have no modeled Prayer stat in this codebase (NpcSkills has no PRAYER constant), so
 * the Npc drain order skips Prayer: Defence -> Strength -> Attack -> Magic -> Ranged. Disclosed
 * simplification, not a guess - NpcSkills was checked directly.
 *
 * Player Prayer points, lifepoints and hitmark damage all use the same 1:1 real-value unit.
 */
// Special look: animation 11991 + graphic 2114 (Novite PlayerCombat case 11696; Void `warstrike_special` = 2114), sound 3865 (Void).
SpecialAttacks.register(50, Items.BANDOS_GODSWORD, Items.BANDOS_GODSWORD_OR) {
    // The (or) godsword plays the OSRS ornate special (gameval BGS_SPECIAL_ORNATE_PLAYER, imported); the 667 godsword keeps its 667 special.
    player.animate(if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.BANDOS_GODSWORD_OR) gg.rsmod.plugins.content.items.osrs.OsrsSeq.BGS_SPECIAL_ORNATE_PLAYER else 11991)
    player.graphic(2114)
    // The 667 special sequence carries synth 3865 as a frame sound; only the silent imported ornate sequence needs the server cue.
    if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.BANDOS_GODSWORD_OR) player.playSound(3865)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.21)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 2.0)
    val landHit = accuracy >= world.randomDouble()
    val hit = player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )

    if (landHit) {
        var remaining = hit.hit.hitmarks.sumOf { it.damage }
        if (target is Player) {
            val p = target as Player
            val order = intArrayOf(Skills.DEFENCE, Skills.STRENGTH, Skills.PRAYER, Skills.ATTACK, Skills.MAGIC, Skills.RANGED)
            for (skill in order) {
                if (remaining <= 0) break
                if (skill == Skills.PRAYER) {
                    val currentRealPoints = p.getCurrentPrayerPoints()
                    if (currentRealPoints <= 0) continue
                    val drain = currentRealPoints.coerceAtMost(remaining)
                    p.decreasePrayerPoints(drain)
                    remaining -= drain
                } else {
                    val currentLevel = p.skills.getCurrentLevel(skill)
                    if (currentLevel <= 0) continue
                    val drain = currentLevel.coerceAtMost(remaining)
                    p.skills.setCurrentLevel(skill, currentLevel - drain)
                    remaining -= drain
                }
            }
        } else {
            val npc = target as Npc
            val order = intArrayOf(NpcSkills.DEFENCE, NpcSkills.STRENGTH, NpcSkills.ATTACK, NpcSkills.MAGIC, NpcSkills.RANGED)
            for (skill in order) {
                if (remaining <= 0) break
                val currentLevel = npc.stats.getCurrentLevel(skill)
                if (currentLevel <= 0) continue
                val drain = currentLevel.coerceAtMost(remaining)
                npc.stats.setCurrentLevel(skill, currentLevel - drain)
                remaining -= drain
            }
        }
    }
}
