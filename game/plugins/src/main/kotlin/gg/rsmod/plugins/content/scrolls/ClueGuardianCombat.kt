package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.poison
import java.io.File

/** Source combat numbers are complete. Visual roles must be independently approved before install.
 * No spell/projectile identity or elite choice probability is manufactured from a max hit.
 */
object ClueGuardianCombat {
    data class Visuals(val attack: Int,val block: Int,val death: Int,
                       val projectiles: List<NpcAttacks.Proj> = emptyList(),
                       val casting: List<NpcAttacks.Gfx> = emptyList(),
                       val impact: List<NpcAttacks.Gfx> = emptyList())
    /** A source gap is explicit; merely importing a candidate sequence cannot certify its role. */
    fun visuals(world: World,source: Int): Visuals? {
        val file=File(RspsPaths.data("cfg/clue/combat-visuals.json"))
        if(!file.isFile)return null
        val row=JsonParser().parse(file.readText()).asJsonObject[source.toString()]?.asJsonObject ?: return null
        if(row["verified"]?.asBoolean!=true)return null
        fun seq(key: String)=ClueCombatAssets.seq(world,row[key].asInt)
        val attack=seq("attack") ?: return null;val block=seq("block") ?: return null;val death=seq("death") ?: return null
        val gson=Gson()
        fun gfx(key: String)=row[key]?.asJsonArray?.map { gson.fromJson(it,NpcAttacks.Gfx::class.java) }.orEmpty()
        val projectiles=row["projectiles"]?.asJsonArray?.map { gson.fromJson(it,NpcAttacks.Proj::class.java) }.orEmpty()
        val casting=gfx("casting");val impact=gfx("impact")
        if((projectiles.map { it.id }+casting.map { it.id }+impact.map { it.id }).any {
                world.definitions.getNullable(SpotAnimDef::class.java,it)==null })return null
        return Visuals(attack,block,death,projectiles,casting,impact)
    }
    fun row(p: ClueGuardianProfiles.Profile,id: Int,visual: Visuals): NpcAttacks.Row {
        require(p.max>0)
        val melee=p.style in setOf("Stab","Slash","Crush")
        val offence=when(p.style) { "Stab"->"stab";"Slash"->"slash";"Crush"->"crush";"Ranged"->"range";else->"magic" }
        return NpcAttacks.Row(id=id,name=p.name,combatDef="clue_guard_${p.source}",attackRange=if(melee)1 else 10,
            attacks=listOf(NpcAttacks.Attack(id="attack",range=if(melee)1 else 10,anim=visual.attack,
                projectiles=visual.projectiles,gfx=visual.casting,impactGfx=visual.impact,
                hits=if(p.source==7310)emptyList() else listOf(NpcAttacks.HitDef(offense=offence,defence=offence,max=p.max*10)))))
    }
    fun hooks(p: ClueGuardianProfiles.Profile) {
        val key="clue_guard_${p.source}"
        if(p.source==7309) NpcAttacks.onLandedImpact(key,"attack") { _,target -> target.poison(8);true }
        if(p.source==7310) NpcAttacks.onAttack(key,"attack") { npc,target -> brassican(npc,target) }
    }
    /** Mod Ash2Mar2020: standard magic accuracy against magical defence, no protection prayers. */
    fun brassican(npc: Npc,target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC,StyleType.MAGIC,WeaponStyle.ACCURATE)
        npc.dealHit(target,maxHit=16.0,landHit=MagicCombatFormula.getUnprotectedAccuracy(npc,target)>=npc.world.randomDouble(),
            delay=1,hitType=HitType.MAGIC,applyDeflectProtection=false)
    }
    /** Wiki23Jul2025: the guard permits halberd melee; other melee weapons cannot reach it. */
    fun canHitFlying(attacker: Pawn,target: Pawn): Boolean {
        if(target !is Npc || target.id!=ClueNpcs.local(6587) || CombatConfigs.getCombatClass(attacker)!=CombatClass.MELEE)return true
        return attacker is Player && attacker.hasWeaponType(WeaponType.HALBERD)
    }
}
