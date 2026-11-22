package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula

/**
 * PROJECT48 X1-X6 (run 3, 22-11): the OSRS npcs imported for the six quests that were not on this server (batch p48x, OsrsNpcImportTool;
 * OSRS id -> local id). They only exist as the owned foe of a quest fight (QuestFights) or as a quest npc ([BracketNpc]).
 */
object XNpcs {
    const val BASILISK_YOUNGLING = 15337 // The Fremennik Exiles
    const val MONSTROUS_BASILISK = 15338
    const val TYPHOR = 15339
    const val JORMUNGAND = 15340
    const val ABOMINATION = 15341 // A Taste of Hope
    const val MYSTERIOUS_STRANGER = 15342 // A Night at the Theatre
    const val ARAXYTE = 15343 // OSRS 11175
    const val HESPORI = 15344
    const val MOTHER = 15345 // Making Friends with My Arm
    const val DONT_KNOW_WHAT = 15346
    const val RANIS_DRAKAN = 15347 // A Taste of Hope
    const val GARTH = 15348 // A Taste of Hope (Ver Sinhaza); Npcs.GARTH 2330 is another, rev-667 Garth

    // PROJECT48 O03 (run 4, batch p48-fairy): the Fairy Tale I/II npcs and Roving Elves' moss guardian, none of them in the 667 cache.
    const val FAIRY_NUFF = 15349 // OSRS 1841 (Zanaris)
    const val FAIRY_QUEEN = 15350 // OSRS 1842
    const val FAIRY_VERY_WISE = 15351 // OSRS 1847
    const val MOSS_GUARDIAN = 15352 // OSRS 891 (Glarial's Tomb)
    const val MOSS_GUARDIAN_DEFEND = 17851 // GIANT_UPDATE_MOSS_DEFEND 4657
    const val MOSS_GUARDIAN_ATTACK = 17852 // GIANT_UPDATE_MOSS_ATTACK 4658
    const val MOSS_GUARDIAN_DEATH = 17853 // GIANT_UPDATE_MOSS_DEATH 4659
}

/**
 * The fights of X1-X6. Maximum hits, styles and speeds: OSRS Wiki monster infoboxes (22-11, saved in PROJECT48-notities\wiki); the hit
 * roll is the npc's own accuracy against the player's defence. Animations are the imported OSRS sequences (local ids below). Not
 * built (dossier BRACKET-QUESTS): projectiles (their spot animations were not imported - the hit lands after the OSRS ranged/magic
 * delay of 2 ticks), the basilisks' stat drain, Typhor's and Jormungand's petrify/ice phases, Hespori's flower phase, Mother's
 * colour phases, Ranis' bat form.
 */
object XFoes : CombatScript() {
    override val ids =
        intArrayOf(
            XNpcs.BASILISK_YOUNGLING, XNpcs.MONSTROUS_BASILISK, XNpcs.TYPHOR, XNpcs.JORMUNGAND, XNpcs.ABOMINATION,
            XNpcs.ARAXYTE, XNpcs.HESPORI, XNpcs.MOTHER, XNpcs.DONT_KNOW_WHAT, XNpcs.RANIS_DRAKAN, XNpcs.MOSS_GUARDIAN,
        )

    const val BASILISK_ATTACK = 17826
    const val BASILISK_DEATH = 17827
    const val TYPHOR_MELEE = 17828
    const val TYPHOR_MAGIC = 17829
    const val TYPHOR_DEATH = 17830
    const val JORMUNGAND_MELEE = 17831
    const val JORMUNGAND_MAGIC = 17832
    const val JORMUNGAND_DEATH = 17833
    const val ABOMINATION_MELEE = 17834
    const val ABOMINATION_RANGED = 17835
    const val ABOMINATION_DEATH = 17836
    const val ARAXYTE_ATTACK = 17837
    const val ARAXYTE_DEATH = 17838
    const val HESPORI_ATTACK = 17839
    const val HESPORI_DEATH = 17840
    const val TROLL_MELEE = 17841 // Mother and Don't Know What share the OSRS troll set
    const val TROLL_RANGED = 17842
    const val TROLL_DEATH = 17843
    const val RANIS_DEATH = 17844
    const val RANIS_CLAW = 17845
    const val RANIS_MAGIC = 17846

    private const val FAR = 8

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        val far = npc.id == XNpcs.HESPORI
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = if (far) FAR else 1, projectile = far)) {
                val coin = npc.world.random(1) == 0
                when (npc.id) {
                    XNpcs.BASILISK_YOUNGLING -> melee(npc, target, BASILISK_ATTACK, StyleType.SLASH, 5)
                    XNpcs.MONSTROUS_BASILISK -> melee(npc, target, BASILISK_ATTACK, StyleType.SLASH, 11)
                    XNpcs.TYPHOR -> if (coin) melee(npc, target, TYPHOR_MELEE, StyleType.CRUSH, 20) else magic(npc, target, TYPHOR_MAGIC, 20)
                    XNpcs.JORMUNGAND -> if (coin) melee(npc, target, JORMUNGAND_MELEE, StyleType.CRUSH, 22) else magic(npc, target, JORMUNGAND_MAGIC, 22)
                    XNpcs.ABOMINATION -> if (coin) melee(npc, target, ABOMINATION_MELEE, StyleType.SLASH, 23) else ranged(npc, target, ABOMINATION_RANGED, 23)
                    XNpcs.ARAXYTE -> melee(npc, target, ARAXYTE_ATTACK, StyleType.CRUSH, 13)
                    // Hespori: two ranged hits of up to 8 or one magic hit of up to 14 (wiki).
                    XNpcs.HESPORI -> if (coin) { ranged(npc, target, HESPORI_ATTACK, 8); ranged(npc, target, -1, 8) } else magic(npc, target, HESPORI_ATTACK, 14)
                    XNpcs.MOTHER -> if (coin) melee(npc, target, TROLL_MELEE, StyleType.CRUSH, 16) else ranged(npc, target, TROLL_RANGED, 16)
                    XNpcs.DONT_KNOW_WHAT -> if (coin) melee(npc, target, TROLL_MELEE, StyleType.CRUSH, 10) else ranged(npc, target, TROLL_RANGED, 10)
                    // Moss Guardian (O03, wiki): crush / "magical melee", max hit 14.
                    XNpcs.MOSS_GUARDIAN -> melee(npc, target, XNpcs.MOSS_GUARDIAN_ATTACK, StyleType.CRUSH, 14)
                    XNpcs.RANIS_DRAKAN -> if (coin) melee(npc, target, RANIS_CLAW, StyleType.SLASH, 20) else magic(npc, target, RANIS_MAGIC, 20)
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(npc: Npc, target: Pawn, anim: Int, style: StyleType, max: Int) {
        npc.prepareAttack(CombatClass.MELEE, style, WeaponStyle.AGGRESSIVE)
        npc.animate(anim)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
    }

    private fun ranged(npc: Npc, target: Pawn, anim: Int, max: Int) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        if (anim != -1) npc.animate(anim)
        val land = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max.toDouble(), landHit = land, delay = 2, hitType = HitType.RANGE)
    }

    private fun magic(npc: Npc, target: Pawn, anim: Int, max: Int) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(anim)
        val land = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max.toDouble(), landHit = land, delay = 2, hitType = HitType.MAGIC)
    }
}
