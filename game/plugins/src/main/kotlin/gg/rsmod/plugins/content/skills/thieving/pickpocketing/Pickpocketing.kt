package gg.rsmod.plugins.content.skills.thieving.pickpocketing

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.DropTableType

object Pickpocketing {
    private const val waitTime = 2

    /**
     * OSRS-audit 2026-09-17b, "armour" family - Rogue equipment (top/mask/trousers/gloves/boots, ids 5553-5557,
     * native, from the Rogues' Den minigame's equipment crates): OSRS Wiki "Thieving" raw wikitext - "The rogue
     * outfit grants a chance of getting double loot when pickpocketing NPCs. Wearing the full set guarantees
     * getting double loot. It does not increase success rate." OSRS Wiki "Rogue equipment" raw wikitext - "Each
     * piece increases the chance of receiving double loot by 15%, unless all five components are worn together,
     * in which case the odds of doubling the loot becomes 100%." This was grep-confirmed completely unwired
     * anywhere in this codebase before this fix (every stat on the 5 items already matched the wiki exactly;
     * only this set-effect was missing).
     * SOURCE_GAP, deliberately NOT changed here: this codebase's pre-existing `getMultiplier` (thieving/agility
     * level-overshoot 1-in-8 lucky x2/x3/x4) has no equivalent anywhere in the current OSRS Wiki "Thieving"
     * article (searched for "lucky"/"triple"/"quadruple"/"overshoot" - zero matches), which only ever describes
     * double loot as the Rogue outfit's own effect. This strongly suggests `getMultiplier`'s mechanic may not be
     * OSRS-authentic (possibly a legacy/donor-only feature), but removing or reworking an entire pre-existing,
     * independently-functioning Thieving mechanic is a materially larger, separate change than this armour-family
     * fix and is left untouched pending an explicit owner decision - recorded here rather than silently deleted
     * or silently left conflated with the Rogue outfit's own, clearly-sourced effect. The two are therefore kept
     * as two independent, stacking rolls below (the conservative default when no source states otherwise): the
     * Rogue outfit's guaranteed/chance double always multiplies whatever the existing roll already produced.
     */
    private val ROGUE_EQUIPMENT =
        intArrayOf(Items.ROGUE_TOP, Items.ROGUE_MASK, Items.ROGUE_TROUSERS, Items.ROGUE_GLOVES, Items.ROGUE_BOOTS)
    private const val ROGUE_PIECE_CHANCE = 0.15

    /** 15% per piece worn, but guaranteed (100%) once all 5 are worn - not simply 5 x 15%. */
    fun rogueOutfitDoubleLootChance(player: Player): Double {
        val worn = ROGUE_EQUIPMENT.count { player.hasEquipped(intArrayOf(it)) }
        return if (worn >= ROGUE_EQUIPMENT.size) 1.0 else worn * ROGUE_PIECE_CHANCE
    }
    private val multiplierAnimations =
        mapOf(
            2 to Anims.DOUBLE_PICKPOCKET,
            3 to Anims.TRIPLE_PICKPOCKET,
            4 to Anims.QUADRUPLE_PICKPOCKET,
        )
    private val multiplierGfx =
        mapOf(
            2 to Gfx.DOUBLE_PICKPOCKET,
            3 to Gfx.TRIPLE_PICKPOCKET,
            4 to Gfx.QUADRUPLE_PICKPOCKET,
        )
    private val messages =
        mapOf(
            1 to "You successfully pick the {npc}'s pocket.",
            2 to "Your lightning-fast reactions allow you to steal double loot.",
            3 to "Your lightning-fast reactions allow you to steal triple loot.",
            4 to "Your lightning-fast reactions allow you to steal quadruple loot.",
        )

    suspend fun pickpocket(
        task: QueueTask,
        target: Npc,
        targetInfo: PickpocketTarget,
    ) {
        val player = task.player
        if (!canPickpocket(player, targetInfo)) {
            return
        }
        if (target.isDead()) {
            player.filterableMessage("Too late; they're dead.")
            return
        }
        player.animate(Anims.PICKPOCKET)
        if (rollForSuccess(targetInfo, player)) {
            onSuccess(task, player, target, targetInfo)
        } else {
            onFailure(player, target, targetInfo)
        }
    }

    private fun rollForSuccess(
        targetInfo: PickpocketTarget,
        player: Player,
    ): Boolean {
        // OSRS Wiki "Thieving cape": "When worn, additional 10% chance of being successful when pickpocketing" (max cape too),
        // applied like the gloves of silence as a factor on the success chance.
        val capeFactor = if (gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.THIEVING)) 1.1 else 1.0
        val adjustmentFactor = (if (player.hasEquipped(EquipmentType.GLOVES, Items.GLOVES_OF_SILENCE)) 1.05 else 1.0) * capeFactor
        return targetInfo.roll(player.skills.getCurrentLevel(Skills.THIEVING), adjustmentFactor)
    }

    private suspend fun onSuccess(
        task: QueueTask,
        player: Player,
        target: Npc,
        targetInfo: PickpocketTarget,
    ) {
        task.wait(waitTime)
        player.playSound(2581)
        val multiplier = getMultiplier(player, targetInfo)
        // Rogue equipment (see rogueOutfitDoubleLootChance's source note): an independent roll that doubles
        // whatever `multiplier` already produced - not exclusive with, and not the same roll as, `getMultiplier`.
        val rogueDoubled = player.world.randomDouble() < rogueOutfitDoubleLootChance(player)
        val totalMultiplier = if (rogueDoubled) multiplier * 2 else multiplier
        // Display is capped at the quadruple tier: the wiki never documents the visual/message result of both
        // an existing lucky proc AND the Rogue outfit's own double triggering on the same pickpocket (a rare
        // simultaneous-procs edge case with no sourced wording), so the closest existing tier is shown while the
        // actual loot below always matches `totalMultiplier` exactly.
        val displayMultiplier = totalMultiplier.coerceAtMost(4)
        if (displayMultiplier > 1) {
            player.animate(multiplierAnimations[displayMultiplier]!!)
            player.graphic(multiplierGfx[displayMultiplier]!!)
        }
        repeat(totalMultiplier) { DropTableFactory.createDropInventory(player, target.id, DropTableType.PICKPOCKET) }
        player.addXp(Skills.THIEVING, targetInfo.xp, checkBrawlingGloves = true)
        player.filterableMessage(
            messages[displayMultiplier]!!.replace(
                "{npc}",
                player.world.definitions
                    .get(NpcDef::class.java, target.id)
                    .name
                    .lowercase(),
            ).replace("martin the ", "") // Fix martin's name in pickpocket message
            ,
        )
    }

    private fun getMultiplier(
        player: Player,
        targetInfo: PickpocketTarget,
    ): Int {
        val thievingLevelOvershoot = player.skills.getCurrentLevel(Skills.THIEVING) - targetInfo.level
        val agilityLevelOvershoot = player.skills.getCurrentLevel(Skills.AGILITY) - targetInfo.level

        return if (thievingLevelOvershoot >= 10 && agilityLevelOvershoot >= 0 && player.world.random(7) == 1) {
            when {
                thievingLevelOvershoot >= 30 && agilityLevelOvershoot >= 20 -> 4
                thievingLevelOvershoot >= 20 && agilityLevelOvershoot >= 10 -> 3
                else -> 2
            }
        } else {
            1
        }
    }

    private fun onFailure(
        player: Player,
        target: Npc,
        targetInfo: PickpocketTarget,
    ) {
        if (isHamTeleport(player, targetInfo)) {
            handleHamFailure(player)
            return
        }
        target.facePawn(player)
        target.animate(Anims.ATTACK_PUNCH)
        target.forceChat(targetInfo.onCaught.random().replace("{name}", player.username))
        player.stun(targetInfo.stunnedTicks)
        player.hit(targetInfo.rollDamage(), HitType.REGULAR_HIT)
        player.facePawn(target)
        target.resetFacePawn()
    }

    private fun isHamTeleport(
        player: Player,
        targetInfo: PickpocketTarget,
    ): Boolean {
        return if (targetInfo == PickpocketTarget.FemaleHamMember || targetInfo == PickpocketTarget.MaleHamMember) {
            val chance = 0.2 * (1 - hamWear.count { player.hasEquipped(intArrayOf(it)) } * 0.04)
            player.world.randomDouble() < chance
        } else {
            false
        }
    }

    private fun handleHamFailure(player: Player) {
        player.message("You're beaten unconscious and bundled out of the HAM camp.")
        player.moveTo(hamDestinations.random())
        // TODO: find the correct animation for this
        // TODO: chance to be locked up instead
    }

    private fun canPickpocket(
        player: Player,
        targetInfo: PickpocketTarget,
    ): Boolean {
        if (player.getCombatTarget() != null) {
            player.message("You can't pickpocket while in combat.")
            return false
        }
        if (player.skills.getCurrentLevel(Skills.THIEVING) < targetInfo.level) {
            player.message("You need a Thieving level of ${targetInfo.level} to do that.")
            return false
        }
        if (!targetInfo.hasInventorySpace(player)) {
            player.message("You don't have enough inventory space to do that.")
            return false
        }
        return true
    }

    private val hamWear =
        listOf(
            Items.HAM_SHIRT,
            Items.HAM_ROBE,
            Items.HAM_HOOD,
            Items.HAM_CLOAK,
            Items.HAM_LOGO,
            Items.HAM_GLOVES,
            Items.HAM_BOOTS,
        )

    private val hamDestinations =
        listOf(
            Tile(3185, 3211),
            Tile(3147, 3217),
            Tile(3140, 3228),
            Tile(3163, 3238),
            Tile(3139, 3261),
        )
}
