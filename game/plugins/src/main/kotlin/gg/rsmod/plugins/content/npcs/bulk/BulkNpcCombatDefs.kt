package gg.rsmod.plugins.content.npcs.bulk

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.NpcCombatBuilder
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.ext.NPC_ATTACK_BONUS_INDEX
import gg.rsmod.plugins.api.ext.NPC_MAGIC_DAMAGE_BONUS_INDEX
import gg.rsmod.plugins.api.ext.NPC_RANGED_STRENGTH_BONUS_INDEX
import gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX
import java.io.File
import java.io.FileReader

/**
 * Bulk, data-sourced npc combat definitions (`data/cfg/npcs/combat-defs.json`).
 *
 * Every attackable npc definition in the rev-667 cache that has no hand-written
 * `set_combat_def` gets a definition from this table, so that a level-90 Dagannoth no longer
 * fights with [NpcCombatDef.DEFAULT]'s 10 hitpoints, level-1 stats and no animations.
 *
 * The table is generated offline by `C:\RSPS\tools\npc-combat-defs\generate.js` from three donors
 * kept outside the repository (see that script's header for provenance and tiering): OSRS/2007-era
 * monster stats matched to the cache by name and combat level, and the original 2012 Matrix
 * `NPCCombatDefinitions` list for attack/block/death animations, timing, projectiles and
 * aggression. Each row records its `source` tier; `matrix` and `derived` rows carry PROVISIONAL
 * stat levels back-solved from the cache combat level rather than sourced values.
 *
 * Hand-written definitions always win: rows are registered through
 * `set_combat_def_fallback`, which the plugin repository only applies to ids that no plugin
 * script claimed, independent of script load order.
 */
object BulkNpcCombatDefs {
    const val DEFAULT_PATH = "./data/cfg/npcs/combat-defs.json"

    /** Aggressive npcs use the same search radius the hand-written definitions default to. */
    private const val DEFAULT_AGGRO_RADIUS = 4

    /** Applied when the source recorded an npc as poisonous but not its poison strength. */
    private const val DEFAULT_RESPAWN_DELAY = 60

    class Row(
        val id: Int = -1,
        val name: String = "",
        @SerializedName("combat_level") val combatLevel: Int = 0,
        val lifepoints: Int = 0,
        val attack: Int = 1,
        val strength: Int = 1,
        val defence: Int = 1,
        val magic: Int = 1,
        val ranged: Int = 1,
        @SerializedName("max_hit") val maxHit: Int = 0,
        @SerializedName("attack_speed") val attackSpeed: Int = 4,
        val aggressive: Boolean = false,
        val poison: Int = 0,
        @SerializedName("poison_immune") val poisonImmune: Boolean = false,
        @SerializedName("slayer_req") val slayerReq: Int = 1,
        @SerializedName("slayer_xp") val slayerXp: Double = 0.0,
        val style: String = "CRUSH",
        val species: List<String> = emptyList(),
        val bonuses: Bonuses? = null,
        val source: String = "",
        @SerializedName("attack_anim") val attackAnim: Int = -1,
        @SerializedName("block_anim") val blockAnim: Int = -1,
        @SerializedName("death_anim") val deathAnim: Int = -1,
        @SerializedName("death_delay") val deathDelay: Int = 0,
        @SerializedName("respawn_delay") val respawnDelay: Int = DEFAULT_RESPAWN_DELAY,
        @SerializedName("attack_gfx") val attackGfx: Int = -1,
        @SerializedName("attack_projectile") val attackProjectile: Int = -1,
    )

    class Bonuses(
        @SerializedName("attack_stab") val attackStab: Int = 0,
        @SerializedName("attack_slash") val attackSlash: Int = 0,
        @SerializedName("attack_crush") val attackCrush: Int = 0,
        @SerializedName("attack_magic") val attackMagic: Int = 0,
        @SerializedName("attack_ranged") val attackRanged: Int = 0,
        @SerializedName("defence_stab") val defenceStab: Int = 0,
        @SerializedName("defence_slash") val defenceSlash: Int = 0,
        @SerializedName("defence_crush") val defenceCrush: Int = 0,
        @SerializedName("defence_magic") val defenceMagic: Int = 0,
        @SerializedName("defence_ranged") val defenceRanged: Int = 0,
        @SerializedName("attack_bonus") val attackBonus: Int = 0,
        @SerializedName("strength_bonus") val strengthBonus: Int = 0,
        @SerializedName("ranged_strength_bonus") val rangedStrengthBonus: Int = 0,
        @SerializedName("magic_damage_bonus") val magicDamageBonus: Int = 0,
    )

    data class Result(
        val defs: Map<Int, NpcCombatDef>,
        val skippedUnknownNpc: Int,
        val droppedAnimations: Int,
        val bySource: Map<String, Int>,
    )

    fun load(
        definitions: DefinitionSet,
        file: File = File(DEFAULT_PATH),
    ): Result {
        val rows: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        return build(rows.asList(), definitions)
    }

    fun build(
        rows: List<Row>,
        definitions: DefinitionSet,
    ): Result {
        val defs = LinkedHashMap<Int, NpcCombatDef>()
        var skipped = 0
        var droppedAnims = 0
        val bySource = HashMap<String, Int>()
        val animCount = definitions.getCount(AnimDef::class.java)

        fun anim(id: Int): Int {
            if (id < 0) return -1
            if (id >= animCount || definitions.getNullable(AnimDef::class.java, id) == null) {
                droppedAnims++
                return -1
            }
            return id
        }

        rows.forEach { row ->
            if (row.id < 0 || definitions.getNullable(NpcDef::class.java, row.id) == null) {
                skipped++
                return@forEach
            }
            require(row.lifepoints > 0 && row.lifepoints % 10 == 0) {
                "combat-defs.json row ${row.id} (${row.name}): source lifepoints must be real HP * 10, was ${row.lifepoints}"
            }
            val style = StyleType.valueOf(row.style)
            val builder = NpcCombatBuilder()
            // The generated source table keeps its historical x10 values; runtime combat defs do
            // not. Convert before publishing the definition so hitpoints and hitmarks share 1:1.
            builder.setHitpoints((row.lifepoints / 10).coerceAtLeast(1))
            builder.setAttackSpeed(row.attackSpeed.coerceAtLeast(1))
            builder.setLevels(
                // A magic-style npc without a scripted spell fights through the melee strategy
                // (see cycle() in combat.plugin.kts), whose accuracy roll reads the attack skill;
                // the OSRS donor gives such npcs attack=1 / magic=N, which would make them unable
                // to land a hit. Use the magic level for that roll until a spell is sourced.
                attack = if (style == StyleType.MAGIC) maxOf(row.attack, row.magic) else row.attack,
                strength = row.strength,
                defence = row.defence,
                magic = row.magic,
                ranged = row.ranged,
            )
            builder.setDefaultAttackAnimation(anim(row.attackAnim))
            builder.setDefaultBlockAnimation(anim(row.blockAnim))
            builder.setDeathAnimation(anim(row.deathAnim))
            builder.setRespawnDelay(if (row.respawnDelay > 0) row.respawnDelay else DEFAULT_RESPAWN_DELAY)
            builder.setDeathDelay(row.deathDelay.coerceAtLeast(0))
            if (row.aggressive) {
                builder.setAggroRadius(DEFAULT_AGGRO_RADIUS)
            }
            if (row.poison > 0) {
                builder.setPoisonDamage(row.poison)
            }
            if (row.poisonImmune) {
                builder.setPoisonImmunity()
            }
            builder.setSlayerRequirement(row.slayerReq.coerceAtLeast(1))
            builder.setSlayerXp(row.slayerXp.coerceAtLeast(0.0))
            builder.setAttackStyle(style)
            val species = row.species.mapNotNull { name -> NpcSpecies.values().firstOrNull { it.name == name } }
            if (species.isNotEmpty()) {
                builder.setSpecies(*species.toTypedArray())
            }
            row.bonuses?.let { b ->
                builder.setBonus(BonusSlot.ATTACK_STAB.id, b.attackStab)
                builder.setBonus(BonusSlot.ATTACK_SLASH.id, b.attackSlash)
                builder.setBonus(BonusSlot.ATTACK_CRUSH.id, b.attackCrush)
                builder.setBonus(BonusSlot.ATTACK_MAGIC.id, b.attackMagic)
                builder.setBonus(BonusSlot.ATTACK_RANGED.id, b.attackRanged)
                builder.setBonus(BonusSlot.DEFENCE_STAB.id, b.defenceStab)
                builder.setBonus(BonusSlot.DEFENCE_SLASH.id, b.defenceSlash)
                builder.setBonus(BonusSlot.DEFENCE_CRUSH.id, b.defenceCrush)
                builder.setBonus(BonusSlot.DEFENCE_MAGIC.id, b.defenceMagic)
                builder.setBonus(BonusSlot.DEFENCE_RANGED.id, b.defenceRanged)
                builder.setBonus(NPC_ATTACK_BONUS_INDEX, b.attackBonus)
                builder.setBonus(NPC_STRENGTH_BONUS_INDEX, b.strengthBonus)
                builder.setBonus(NPC_RANGED_STRENGTH_BONUS_INDEX, b.rangedStrengthBonus)
                builder.setBonus(NPC_MAGIC_DAMAGE_BONUS_INDEX, b.magicDamageBonus)
            }
            if (row.attackProjectile > -1) {
                builder.setAttackProjectile(row.attackProjectile)
            }
            if (row.attackGfx > -1) {
                builder.setAttackGfx(row.attackGfx)
            }
            defs[row.id] = builder.build()
            bySource[row.source] = (bySource[row.source] ?: 0) + 1
        }
        return Result(defs, skipped, droppedAnims, bySource)
    }
}
