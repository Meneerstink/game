package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestSlots
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestVarps
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q41: Secrets of the North (bracket 111+, Master). OSRS Wiki: 69 Agility, 64 Thieving, 56 Hunter (none boostable); Making
// Friends with My Arm, The General's Shadow, Devious Minds, Hazeel Cult; 2 QP, 60,000 Agility, 50,000 Thieving, 40,000 Hunter XP.
//  - The owner (21-11): "JA, NPC's importeren": Evelot, the Assassin, Big Fish and the Strange Creature are the OSRS npcs (cache round
//    21-11, tools OsrsNpcImportTool sotn / OsrsPetImportTool skeletal-npc sotn / OsrsFxImportTool sotn; ids in [SotnNpcs]).
//  - Making Friends with My Arm and The General's Shadow are not on this server: they are shown as lines, they do not block.
//  - Weiss and the Ghorrock Dungeon do not exist on this map: the steps "in the North" are played on the frozen shore that lies on
//    the OSRS coordinates of Weiss (2868,3938 - open snow, not Wilderness). Hazeel is met there in his Big Fish shape, as in OSRS.
//  - Not built: the crime-scene search, the tracking of Evelot, the lock and the dungeon puzzles (dialogue only); Evelot's prayer
//    drain, the assassin's smoke bombs / vials / ranged attack and the creature's melee, teleport and shield phases (dossier).
//  - ONTGR: the ancient essence crystals and the Phantom Muspah do not exist here; Desert Treasure II asks this quest already.
//  All tiles that are not a spawn are estimates (dossier BRACKET-QUESTS).

/** The imported OSRS npcs of the quest (OSRS id -> local id, import ledger / OSRS_IMPORT_MASTER.yml). */
object SotnNpcs {
    const val EVELOT = 15333 // OSRS 12047
    const val ASSASSIN = 15334 // OSRS 12062
    const val BIG_FISH = 15335 // OSRS 12053
    const val STRANGE_CREATURE = 15336 // OSRS 12073
}

private val GUARD_POST = Tile(2570, 3277, 0)
private val BAR = Tile(2567, 3144, 0)
private val EVELOT_SHORE = Tile(2644, 3202, 0)
private val HAZEEL_POST = Tile(2607, 9673, 0)
private val CLAUS_POST = Tile(2570, 9668, 0)
private val WEISS_SHORE = Tile(2868, 3938, 0)
private val ASSASSIN_FIGHT = Tile(2872, 3936, 0)
private val CREATURE_FIGHT = Tile(2866, 3932, 0) // south-west corner of a free 5x5 (clipmap 21-11): the creature is size 5

private const val BRIBE = 100

object SecretsOfTheNorth : ShortQuest(
    name = "Secrets of the North",
    startPoint = "Speak to the guard outside the Carnillean Mansion in the south-west of East Ardougne.",
    rewards = "2 Quest Points, 60,000 Agility XP, 50,000 Thieving XP, 40,000 Hunter XP.",
    pointReward = 2,
    questId = OsrsQuestVarps.SECRETS_OF_THE_NORTH,
    slot = OsrsQuestSlots.SECRETS_OF_THE_NORTH,
    completedValue = 9, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.GUARD_887,
    startNpcName = "Guard",
    startPlace = "the Carnillean Mansion",
    startLocation = GUARD_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 69, Skills.THIEVING to 64, Skills.HUNTER to 56),
            quests = listOf("Devious Minds", "Hazeel Cult"),
            extra = listOf("Making Friends with My Arm (not on this server)", "The General's Shadow (not on this server)"),
        )

    override val recommendation = Recommendation.combat(100)

    override val reward =
        QuestReward(
            icon = Items.LOCKPICK,
            xp = listOf(Skills.AGILITY to 60_000.0, Skills.THIEVING to 50_000.0, Skills.HUNTER to 40_000.0),
            lines = listOf("2 Quest Points", "60,000 Agility XP", "50,000 Thieving XP", "40,000 Hunter XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Ceril Carnillean is dead - stabbed in his own study, and",
            "the family vault emptied. We found an amulet of the",
            "Khazard army by the broken window. Will you help?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find the killer.", "Not right now.", title = "Start Secrets of the North?") != 1) return false
        chatPlayer("I'll look into it.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc(
            "The amulet has a name on the back: 'Evelot'. I cannot",
            "question Khazard's people. Try the bar at the Fight Arena.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ANGOR,
                journal = listOf("I should ask the ${red("Khazard barman")} at the Fight Arena about 'Evelot'.", "He will want ${red("$BRIBE coins")}."),
                done = listOf("The barman sent me after Evelot, round the arena to the shore."),
                place = "the Fight Arena bar",
                location = BAR,
                tip = "Bring $BRIBE coins for the barman.",
                talk = {
                    chatPlayer("Do you know anyone called Evelot?", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc("Might do. My memory is not what it was... $BRIBE coins would help.", facialExpression = FacialExpression.CALM_TALK)
                    if (player.inventory.getItemCount(Items.COINS_995) < BRIBE) {
                        chatPlayer("I don't have $BRIBE coins on me.", facialExpression = FacialExpression.SAD)
                        false
                    } else {
                        player.inventory.remove(Items.COINS_995, BRIBE)
                        chatNpc(
                            "She left in a hurry, just now. Follow her tracks round",
                            "the arena: she keeps a rowboat south of the Tower of Life.",
                            facialExpression = FacialExpression.HAPPY,
                        )
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.ANGOR,
                journal = listOf("I must defeat ${red("Evelot")} (level 148) at her rowboat on the shore south", "of the Tower of Life."),
                done = listOf("I defeated Evelot. She stole a scroll for the Hazeel cult, but she", "did not kill Ceril."),
                place = "the shore south of the Tower of Life",
                location = EVELOT_SHORE,
                tip = "Evelot hits up to 34 with melee and is weakest to magic. Bring food.",
                fight = QuestFight(SotnNpcs.EVELOT, "Evelot", 148, EVELOT_SHORE, "You should not have followed me!"),
                talk = {
                    chatNpc("I told you: her rowboat, south of the Tower of Life.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.HAZEEL,
                journal = listOf("I should ask ${red("Hazeel")} in the cult's hideout in the Ardougne sewers", "who hired Evelot."),
                done = listOf("Hazeel did not hire Evelot: somebody else wanted the scroll."),
                place = "the Hazeel cult's hideout",
                location = HAZEEL_POST,
                talk = {
                    chatPlayer("Evelot says she stole a scroll for your cult.", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc(
                        "She brought it, yes - but none of mine hired her, and",
                        "none of mine killed Ceril. A third hand is at work.",
                        "Question the household: somebody saw something.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.CLAUS_THE_CHEF,
                journal = listOf("I should question ${red("Claus the Chef")} in the basement of the Carnillean", "Mansion."),
                done = listOf("Claus felt a sudden cold and saw a shadow. Behind the basement wall I", "found a dusty scroll with a faded symbol."),
                place = "the Carnillean Mansion's basement",
                location = CLAUS_POST,
                talk = {
                    chatNpc(
                        "The night the master died it went cold down here, bitter",
                        "cold, and a shadow went past the shelves - into the wall!",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    messageBox("Behind the shelves a button opens the wall. In the hidden room you pick the lock of a chest and find a dusty scroll with a faded symbol.")
                    true
                },
            ),
            QuestStep(
                npc = SotnNpcs.BIG_FISH,
                journal = listOf("Hazeel wants the scroll in the North. I should meet him - in the shape", "of a ${red("Big Fish")} - on the frozen shore of ${red("Weiss")}."),
                done = listOf("The scroll's symbol is half of the crest that seals the prison of", "Ghorrock. Somebody is trying to open it."),
                place = "the frozen shore of Weiss",
                location = WEISS_SHORE,
                tip = "Be ready to fight before you travel north: food, prayer potions and antipoison.",
                talk = {
                    chatNpc(
                        "Do not stare. It is I, Hazeel. This symbol is half of the",
                        "crest that seals the prison below old Ghorrock.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    chatNpc("Whoever holds the other half is close. Look - we are not alone.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = SotnNpcs.BIG_FISH,
                journal = listOf("I must defeat the ${red("assassin")} (level 262) on the frozen shore of Weiss."),
                done = listOf("The assassin vanished into the shadows when I beat him."),
                place = "the frozen shore of Weiss",
                location = ASSASSIN_FIGHT,
                tip = "The assassin hits up to 39 with his bow-sword. Bring food and antipoison.",
                fight = QuestFight(SotnNpcs.ASSASSIN, "The assassin", 262, ASSASSIN_FIGHT, "We meet again."),
                talk = {
                    chatNpc("The assassin! Deal with him - I cannot be seen here.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = SotnNpcs.BIG_FISH,
                journal = listOf("Something guards the way to the prison. I must defeat the ${red("Strange Creature")}", "(level 368) on the frozen shore of Weiss."),
                done = listOf("The Strange Creature was Jhallan, a Mahjarrat. The assassin took him", "away before he could speak."),
                place = "the frozen shore of Weiss",
                location = CREATURE_FIGHT,
                tip = "The Strange Creature attacks from a distance with ranged and magic (up to 33). Bring your best gear, food and prayer potions.",
                fight = QuestFight(SotnNpcs.STRANGE_CREATURE, "The Strange Creature", 368, CREATURE_FIGHT, ""),
                talk = {
                    chatNpc("That... thing is no beast of the ice. Destroy it!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GUARD_887,
                journal = listOf("I should report to the ${red("guard")} outside the Carnillean Mansion."),
                done = listOf("I told the guard what I found. The case is closed - for now."),
                place = "the Carnillean Mansion",
                location = GUARD_POST,
                talk = {
                    chatPlayer(
                        "Evelot stole the scroll, but the killer was an assassin",
                        "who serves somebody in the far North. He got away.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    chatNpc("Then Ardougne owes you its thanks. I will write it up.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

/**
 * The three fights of Secrets of the North. OSRS Wiki monster pages (21-11): Evelot - crush melee, speed 4, max 34; Assassin - slash
 * (and ranged, not built), speed 4, max 39; Strange Creature (ranged form) - ranged max 32 and magic max 33, speed 6. The hit roll is
 * the npc's own accuracy against the player's defence; the maximum is the wiki's. Animations: Evelot uses the rev-667 human set (her
 * OSRS definition stands and walks on 808/819, the shared ids); the assassin's bow-sword is RuneLite gameval AnimationID
 * M_ASSASSIN_ATTACK 1413; the creature's are NPC_MUSPAH_ATTACK_RANGED_01 9922 / _MAGIC_01 9918 / DEATH_01 9939, with SpotanimID
 * PROJECTILE_MUSPAH_ATTACK_RANGED_01 2329, PROJECTILE_MUSPAH_ATTACK_MAGIC_01 2327 and IMPACT_MUSPAH_ATTACK_MAGIC_01 2328.
 */
object SotnFoes : CombatScript() {
    override val ids = intArrayOf(SotnNpcs.EVELOT, SotnNpcs.ASSASSIN, SotnNpcs.STRANGE_CREATURE)

    const val EVELOT_ATTACK = 401 // rev-667 human crush
    const val EVELOT_BLOCK = 424
    const val HUMAN_DEATH = 836
    const val EVELOT_MAX = 34

    const val ASSASSIN_ATTACK = 17798 // OSRS 1413
    const val ASSASSIN_MAX = 39

    const val CREATURE_RANGED = 17808 // OSRS 9922
    const val CREATURE_MAGIC = 17807 // OSRS 9918
    const val CREATURE_DEATH = 17810 // OSRS 9939
    const val GFX_RANGED = 3508 // OSRS 2329
    const val GFX_MAGIC = 3506 // OSRS 2327
    const val GFX_MAGIC_IMPACT = 3507 // OSRS 2328
    const val CREATURE_RANGED_MAX = 32
    const val CREATURE_MAGIC_MAX = 33
    const val CREATURE_RANGE = 8

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        val far = npc.id == SotnNpcs.STRANGE_CREATURE
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = if (far) CREATURE_RANGE else 1, projectile = far)) {
                when (npc.id) {
                    SotnNpcs.EVELOT -> melee(npc, target, EVELOT_ATTACK, StyleType.CRUSH, EVELOT_MAX)
                    SotnNpcs.ASSASSIN -> melee(npc, target, ASSASSIN_ATTACK, StyleType.SLASH, ASSASSIN_MAX)
                    else -> if (npc.world.random(1) == 0) ranged(npc, target) else magic(npc, target)
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

    private fun ranged(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(CREATURE_RANGED)
        npc.world.spawn(npc.createProjectile(target, GFX_RANGED, ProjectileType.ARROW))
        val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val land = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = CREATURE_RANGED_MAX.toDouble(), landHit = land, delay = delay, hitType = HitType.RANGE)
    }

    private fun magic(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(CREATURE_MAGIC)
        npc.world.spawn(npc.createProjectile(target, GFX_MAGIC, ProjectileType.MAGIC))
        val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val land = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        target.graphic(GFX_MAGIC_IMPACT, 0, delay * 30)
        npc.dealHit(target = target, maxHit = CREATURE_MAGIC_MAX.toDouble(), landHit = land, delay = delay, hitType = HitType.MAGIC)
    }
}
