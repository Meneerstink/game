package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.npc
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.prepareAttack
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula
import gg.rsmod.plugins.content.combat.formula.DragonfireTable
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * The bosses of the short quests' fights (owner 2026-09-26: "perfectioneer alle quests"). Mechanics as the OSRS Wiki
 * Desert Treasure quick guide and the 2009scape Desert Treasure behaviours (Kamil/Damis/Fareed/Dessous, same-era ids and
 * animations) describe them:
 *
 *  - Dessous: a fast melee fighter; when the player protects from melee he turns into his hissing form (1915) and hits
 *    twice for 5 (magic and ranged) instead, and he teleports next to a player who runs 5+ tiles away.
 *  - Kamil: melee; one hit in three is his ice spell ("Sallamakar Ro!"): two 5-damage hits (blocked by Protect from
 *    Magic / Missiles) and the player is frozen.
 *  - Damis: the first form (103) becomes the second (174) when it dies; the second form drains 5 Prayer with every hit.
 *  - Fareed: only water spells hurt him with magic; without ice gloves the heat makes the player drop the weapon.
 *  - Elvarg: melee and dragonfire (an anti-dragon shield / antifire protects, DragonfireFormula).
 */
object QuestBosses {
    private fun protects(target: Pawn, style: CombatClass): Boolean =
        PrayerIcon.values().any { it.id == target.prayerIcon && style in it.protects }

    private fun melee(npc: Npc, target: Pawn, anim: Int) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        if (anim >= 0) npc.animate(anim)
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }

    private fun flat(npc: Npc, target: Pawn, damage: Int, type: HitType, blockedBy: CombatClass, delay: Int) {
        val dealt = if (protects(target, blockedBy)) 0.0 else damage.toDouble()
        npc.dealHit(target = target, minHit = dealt, maxHit = dealt, landHit = true, delay = delay, hitType = type)
    }

    private suspend fun fight(it: QueueTask, beforeMove: (Npc, Pawn) -> Unit = { _, _ -> }, attack: suspend (Npc, Pawn) -> Unit) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (npc.canEngageCombat(target)) {
            npc.facePawn(target)
            beforeMove(npc, target)
            if (npc.moveToAttackRange(it, target, distance = 1, projectile = false) && npc.isAttackDelayReady()) {
                attack(npc, target)
                npc.postAttackLogic(target)
            }
            it.wait(1)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    object Dessous : CombatScript() {
        override val ids = intArrayOf(Npcs.DESSOUS, Npcs.DESSOUS_1915)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(
                it,
                // 2009scape DessousBehavior: "Teleport nearer, if too far" - 5 tiles or more.
                beforeMove = { npc, target ->
                    if (!npc.tile.isWithinRadius(target.tile, 4) && npc.tile.height == target.tile.height) {
                        npc.graphic(86)
                        npc.moveTo(target.tile.x, target.tile.z, target.tile.height)
                        npc.graphic(86)
                    }
                },
            ) { npc, target ->
                if (protects(target, CombatClass.MELEE)) {
                    if (npc.getTransmogId() != Npcs.DESSOUS_1915) {
                        npc.setTransmogId(Npcs.DESSOUS_1915)
                        npc.graphic(86)
                    }
                    npc.forceChat("Hssssssssssss")
                    npc.animate(1914)
                    flat(npc, target, 5, HitType.MAGIC, CombatClass.MAGIC, 2)
                    flat(npc, target, 5, HitType.RANGE, CombatClass.RANGED, 2)
                } else {
                    if (npc.getTransmogId() == Npcs.DESSOUS_1915) {
                        npc.setTransmogId(-1)
                        npc.graphic(86)
                    }
                    melee(npc, target, 1264)
                }
            }
    }

    object Kamil : CombatScript() {
        override val ids = intArrayOf(Npcs.KAMIL)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it) { npc, target ->
                if (npc.world.random(2) == 0) {
                    npc.forceChat("Sallamakar Ro!")
                    npc.animate(440)
                    flat(npc, target, 5, HitType.MAGIC, CombatClass.MAGIC, 1)
                    flat(npc, target, 5, HitType.RANGE, CombatClass.RANGED, 1)
                    target.graphic(539)
                    target.freeze(8)
                    (target as? Player)?.message("You've been frozen!")
                } else {
                    melee(npc, target, 440)
                }
            }
    }

    object Damis : CombatScript() {
        override val ids = intArrayOf(Npcs.DAMIS, Npcs.DAMIS_1975)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it) { npc, target ->
                melee(npc, target, 440)
                if (npc.id == Npcs.DAMIS_1975 && target is Player) {
                    target.skills.decrementCurrentLevel(gg.rsmod.plugins.api.Skills.PRAYER, 5, capped = false)
                }
            }
    }

    object Fareed : CombatScript() {
        override val ids = intArrayOf(Npcs.FAREED)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it) { npc, target ->
                // The normal unequip route (stats, weapon tab and appearance update; refused when the inventory is full).
                if (target is Player && !target.equipment.contains(Items.ICE_GLOVES) && target.equipment[3] != null) {
                    if (gg.rsmod.game.action.EquipAction.unequip(target, 3) == gg.rsmod.game.action.EquipAction.Result.SUCCESS) {
                        target.message("The heat from the warrior causes you to drop your weapon.")
                    }
                }
                melee(npc, target, 799)
            }
    }

    object Elvarg : CombatScript() {
        override val ids = intArrayOf(Npcs.ELVARG)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it) { npc, target ->
                if (npc.world.random(3) == 0 || !npc.tile.isWithinRadius(target.tile, 1)) {
                    npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                    npc.animate(id = Anims.DRAGON_DRAGONFIRE_ATTACK, priority = true)
                    npc.graphic(Gfx.DRAGON_DRAGONFIRE_HOSE)
                    if (target is Player) target.playSound(Sfx.DRAGONSLAYER_DRAGONBREATH, delay = 2)
                    npc.dealHit(target = target, formula = DragonfireFormula(DragonfireTable.Type.CHROMATIC), delay = 2)
                } else {
                    melee(npc, target, npc.combatDef.attackAnimation)
                }
            }
    }

    val SCRIPTS: List<CombatScript> = listOf(Dessous, Kamil, Damis, Fareed, Elvarg)

    /**
     * Fareed: magic other than a water spell does nothing to him ("water-based magic works"). The Temple at Senntisten's
     * ice demon is the other way round: "immune to water spells" (RuneScape Wiki quick guide).
     */
    fun modifyIncomingDamage(target: Npc, attacker: Pawn, hitType: HitType, damage: Int): Int {
        if (hitType != HitType.MAGIC || (target.id != Npcs.FAREED && target.id != Npcs.ICE_DEMON)) return damage
        val spell = attacker.attr[Combat.SPELL_OF_THIS_ATTACK] ?: attacker.attr[Combat.CASTING_SPELL]
        val water = spell != null && spell.name.startsWith("WATER_")
        return when (target.id) {
            Npcs.FAREED -> if (water) damage else 0
            else -> if (water) 0 else damage
        }
    }
}
