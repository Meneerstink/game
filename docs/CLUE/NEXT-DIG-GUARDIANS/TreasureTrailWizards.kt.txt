package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.dsl.NpcCombatDsl
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import java.io.File

/** The two source hard-coordinate wizards, using the native human rig and verified god-spell imports. */
object TreasureTrailWizards {
    data class Profile(val source: Int, val native: Int, val name: String, val level: Int, val hp: Int,
                       val speed: Int, val xp: Double, val examine: String, val stats: List<Int>, val bonuses: List<Int>)
    val profiles by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/guardians.json")).readText()).asJsonArray.map { it.asJsonObject }
            .filter { it["osrsId"].asInt in setOf(2954,2955) }.map { row ->
                val stat=row["stats"].asJsonObject; val bonus=row["bonuses"].asJsonObject
                Profile(row["osrsId"].asInt,row["existingLocal"].asInt,row["name"].asString,row["level"].asInt,
                    row["hitpoints"].asInt,row["attackSpeed"].asInt,row["experienceBonusPercent"].asDouble,row["examine"].asString,
                    listOf("attack","strength","defence","magic","ranged").map { stat[it].asInt },
                    listOf("attackMelee","strengthMelee","attackMagic","magicDamage","attackRanged","strengthRanged",
                        "defenceStab","defenceSlash","defenceCrush","defenceMagic","defenceRangedStandard").map { bonus[it].asInt })
            }
    }
    private val attacks = listOf(386,424,836,15773)
    fun actor(world: World, source: Int): Int? = profiles.singleOrNull { it.source == source }?.let { p ->
        p.native.takeIf { id -> world.definitions.getNullable(NpcDef::class.java,id)?.let {
            it.name.equals(p.name,true) && it.combatLevel == p.level && it.size == 1 && it.options.any { op -> op.equals("Attack",true) }
        } == true && attacks.all { world.definitions.getNullable(AnimDef::class.java,it) != null } &&
            world.definitions.getNullable(SpotAnimDef::class.java,if(source==2955)3159 else 3161) != null }
    }
    fun available(world: World, row: TreasureTrailStepCatalogue.Step): Boolean =
        row.action == TreasureTrailStepCatalogue.Action.DIG && row.challenge == TreasureTrailStepCatalogue.Challenge.GUARD &&
            !row.chooseGuard && row.guards.isNotEmpty() && row.guards.all { actor(world,it) != null && ClueGuardianDropCatalogue.current.available(world,it) }

    fun definition(p: Profile) = NpcCombatDsl.Builder().apply {
        configs { respawnDelay=0; attackSpeed=p.speed; attackStyle=StyleType.STAB; xpMultiplier=1+p.xp/100 }
        aggro { neverAggro() }
        stats { hitpoints=p.hp*10; attack=p.stats[0]; strength=p.stats[1]; defence=p.stats[2]; magic=p.stats[3]; ranged=p.stats[4] }
        bonuses { attackBonus=p.bonuses[0]; strengthBonus=p.bonuses[1]; attackMagic=p.bonuses[2]; magicDamageBonus=p.bonuses[3]
            attackRanged=p.bonuses[4]; rangedStrengthBonus=p.bonuses[5]; defenceStab=p.bonuses[6]; defenceSlash=p.bonuses[7]
            defenceCrush=p.bonuses[8]; defenceMagic=p.bonuses[9]; defenceRanged=p.bonuses[10] }
        anims { attack=386; block=424; death=836 }
    }.build()

    /** Source: Saradomin changes style against protection prayers only inside melee reach. */
    fun melee(source: Int, inReach: Boolean, protected: Boolean): Boolean = source == 2955 && inReach && !protected
    fun attackRow(p: Profile): NpcAttacks.Row {
        val magic = NpcAttacks.Attack(id="magic",range=10,condition=if(p.source==2955)"clue_sara_magic" else "",
            anim=15773, hits=listOf(NpcAttacks.HitDef(offense="magic",defence="magic",max=200)),
            impactGfx=listOf(NpcAttacks.Gfx(id=if(p.source==2955)3159 else 3161,defHeight=if(p.source==2955)100 else 0)))
        val sections=if(p.source==2955) listOf(NpcAttacks.Attack(id="melee",range=1,condition="clue_sara_melee",anim=386,
            hits=listOf(NpcAttacks.HitDef(offense="stab",defence="stab",max=140)),poison=60),magic) else listOf(magic)
        return NpcAttacks.Row(id=p.native,name=p.name,combatDef="clue_wizard_${p.source}",attackRange=10,attacks=sections)
    }
    fun installConditions() {
        NpcAttacks.condition("clue_sara_melee") { npc,target -> melee(2955,Combat.inMeleeReach(npc,target,1),target.isProtectedFrom(CombatClass.MELEE)) }
        NpcAttacks.condition("clue_sara_magic") { npc,target -> !melee(2955,Combat.inMeleeReach(npc,target,1),target.isProtectedFrom(CombatClass.MELEE)) }
    }

    fun dig(player: Player, spawn: (Player,List<Int>) -> List<Npc>? = ::spawn): Boolean {
        for(tier in ClueScrollTier.values()) {
            val step=ClueScrollManager.activeStep(player,tier) as? TreasureTrailSourceStep ?: continue
            val row=step.source
            if(row.action != TreasureTrailStepCatalogue.Action.DIG || row.challenge != TreasureTrailStepCatalogue.Challenge.GUARD || !row.at(player.tile)) continue
            if(row.chooseGuard || row.guards.any { id -> profiles.none { it.source==id } }) continue
            when(TreasureTrailGuardians.attempt(player,step,row.guards,spawn=spawn)) {
                TreasureTrailGuardians.Result.READY -> TreasureTrailGuardians.dig(player,step)
                TreasureTrailGuardians.Result.FIGHTING -> TreasureTrailGuardians.session(player)?.actors?.forEach { if(!it.isDead())it.attack(player) }
                TreasureTrailGuardians.Result.UNAVAILABLE -> println("PROBLEM CLUE coordinate wizard unavailable at${player.tile}; clue retained")
                TreasureTrailGuardians.Result.UNHANDLED -> return false
            }
            return true
        }
        return false
    }
    private fun spawn(player: Player, sources: List<Int>): List<Npc>? {
        if(sources.size != 1) return null
        val source=sources.single(); val id=actor(player.world,source) ?: return null
        if(!ClueGuardianDropCatalogue.current.available(player.world,source))return null
        val direction=Direction.values().firstOrNull { it != Direction.NONE && player.world.collision.canStep(player.tile,it) } ?: return null
        val npc=Npc(id,player.tile.step(direction),player.world);npc.respawns=false
        TreasureTrailAgents.claim(player,npc)
        if(!player.world.spawn(npc))return null
        npc.forceChat(if(source==2955)"For Saradomin!" else "Die, human!")
        return listOf(npc)
    }
}
