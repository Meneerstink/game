package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.dsl.NpcCombatDsl
import java.io.File

/** Wiki/cache identities for every guardian and Mimic minion; no inferred combat levels. */
object ClueGuardianProfiles {
    data class Profile(val source: Int, val local: Int?, val name: String, val level: Int, val size: Int,
                       val hp: Int, val speed: Int, val xp: Double, val style: String, val max: Int,
                       val stats: List<Int>, val bonuses: List<Int>, val examine: String)
    val all by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/guardians.json")).readText()).asJsonArray.map { value ->
            val row=value.asJsonObject;val stats=row["stats"].asJsonObject;val bonus=row["bonuses"].asJsonObject
            Profile(row["osrsId"].asInt,row["existingLocal"].takeUnless { it.isJsonNull }?.asInt,
                row["name"].asString,row["level"].asInt,row["size"].asInt,row["hitpoints"].asInt,
                row["attackSpeed"].asInt,row["experienceBonusPercent"].asDouble,row["attackStyle"].asString,
                row["maxHitText"].asString.toIntOrNull() ?: -1,
                listOf("attack","strength","defence","magic","ranged").map { stats[it].asInt },
                listOf("attackMelee","strengthMelee","attackMagic","magicDamage","attackRanged","strengthRanged",
                    "defenceStab","defenceSlash","defenceCrush","defenceMagic","defenceRangedStandard").map { bonus[it].asInt },
                row["examine"].asString)
        }
    }
    fun definition(p: Profile, attackAnim: Int, blockAnim: Int, deathAnim: Int) = NpcCombatDsl.Builder().apply {
        configs {
            respawnDelay=0;attackSpeed=p.speed;xpMultiplier=1+p.xp/100
            attackStyle=when(p.style) {
                "Stab" -> StyleType.STAB
                "Slash" -> StyleType.SLASH
                "Crush" -> StyleType.CRUSH
                "Ranged" -> StyleType.RANGED
                else -> StyleType.MAGIC
            }
        }
        aggro { neverAggro() }
        stats { hitpoints=p.hp*10;attack=p.stats[0];strength=p.stats[1];defence=p.stats[2];magic=p.stats[3];ranged=p.stats[4] }
        bonuses {
            attackBonus=p.bonuses[0];strengthBonus=p.bonuses[1];attackMagic=p.bonuses[2];magicDamageBonus=p.bonuses[3]
            attackRanged=p.bonuses[4];rangedStrengthBonus=p.bonuses[5];defenceStab=p.bonuses[6];defenceSlash=p.bonuses[7]
            defenceCrush=p.bonuses[8];defenceMagic=p.bonuses[9];defenceRanged=p.bonuses[10]
        }
        anims { attack=attackAnim;block=blockAnim;death=deathAnim }
    }.build()
}
