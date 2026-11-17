package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.dsl.NpcCombatDsl
import gg.rsmod.plugins.api.ext.*
import java.io.File
import java.lang.ref.WeakReference

/** Double agent Wiki variants and cache2686 identities; the general melee strategy handles prayers. */
object TreasureTrailAgents {
    data class Profile(val source: Int, val native: Int?, val level: Int, val hp: Int, val speed: Int,
                       val stats: List<Int>, val attack: Int, val strength: Int, val defence: List<Int>,
                       val xp: Double, val examine: String)
    val profiles by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/guardians.json")).readText()).asJsonArray
            .map { it.asJsonObject }.filter { it["osrsId"].asInt in setOf(1777, 1778, 7312) }.map { row ->
                val stats = row["stats"].asJsonObject
                val bonus = row["bonuses"].asJsonObject
                Profile(row["osrsId"].asInt, row["existingLocal"].takeUnless { it.isJsonNull }?.asInt,
                    row["level"].asInt, row["hitpoints"].asInt, row["attackSpeed"].asInt,
                    listOf("attack", "strength", "defence", "magic", "ranged").map { stats[it].asInt },
                    bonus["attackMelee"].asInt, bonus["strengthMelee"].asInt,
                    listOf("defenceStab", "defenceSlash", "defenceCrush", "defenceMagic", "defenceRangedStandard").map { bonus[it].asInt },
                    row["experienceBonusPercent"].asDouble, row["examine"].asString)
            }.also { check(it.map(Profile::source).toSet() == setOf(1777, 1778, 7312)) }
    }
    private val OWNER = AttributeKey<WeakReference<Player>>()
    private val LOOTED = AttributeKey<Boolean>()
    fun local(profile: Profile): Int? = ClueNpcs.local(profile.source) ?: profile.native
    fun actor(world: World, source: Int): Int? {
        val profile = profiles.singleOrNull { it.source == source } ?: return null
        return local(profile)?.takeIf { id ->
            world.definitions.getNullable(NpcDef::class.java, id)?.let {
                it.name.equals("Double agent", true) && it.combatLevel == profile.level && it.size == 1 &&
                    it.options.any { option -> option.equals("Attack", true) }
            } == true && listOf(422, 424, 836).all { world.definitions.getNullable(AnimDef::class.java, it) != null }
        }
    }
    fun available(world: World, row: TreasureTrailStepCatalogue.Step): Boolean =
        row.action == TreasureTrailStepCatalogue.Action.EMOTE && row.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT &&
            row.equipment.all { group -> group.any { it.local() != null } } &&
            TreasureTrailUri.current.actor(world, row.tier) != null &&
            row.points.all { point -> TreasureTrailGuardians.agentSource(row.tier, point.getWildernessLevel() > 0)
                ?.let { actor(world, it) } != null }

    fun definition(profile: Profile) = NpcCombatDsl.Builder().apply {
        configs { attackSpeed = profile.speed; respawnDelay = 0; attackStyle = StyleType.CRUSH; xpMultiplier = 1 + profile.xp / 100 }
        // Spawn starts combat with its owner; never find an unrelated nearby player.
        aggro { neverAggro() }
        stats { hitpoints = profile.hp * 10; attack = profile.stats[0]; strength = profile.stats[1]
            defence = profile.stats[2]; magic = profile.stats[3]; ranged = profile.stats[4] }
        bonuses { attackBonus = profile.attack; strengthBonus = profile.strength
            defenceStab = profile.defence[0]; defenceSlash = profile.defence[1]; defenceCrush = profile.defence[2]
            defenceMagic = profile.defence[3]; defenceRanged = profile.defence[4] }
        // Cache/gameval HUMAN_UNARMEDPUNCH, HUMAN_UNARMEDBLOCK, HUMAN_DEATH (same native human rig).
        anims { attack = 422; block = 424; death = 836 }
    }.build()

    fun emote(player: Player, component: Int,
              spawn: (Player, List<Int>) -> List<Npc>? = ::spawn,
              uri: (Player, TreasureTrailSourceStep) -> Boolean = { p, s -> TreasureTrailEmotes.afterGuardian(p, s) }): Boolean {
        for (tier in ClueScrollTier.values()) {
            val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: continue
            val row = step.source
            if (row.action != TreasureTrailStepCatalogue.Action.EMOTE || row.challenge != TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT ||
                row.firstEmote != component || !row.at(player.tile) || !TreasureTrailEmotes.attire(player, row)) continue
            val wilderness = player.tile.getWildernessLevel() > 0
            val source = TreasureTrailGuardians.agentSource(tier, wilderness) ?: continue
            val result = TreasureTrailGuardians.attempt(player, step, listOf(source), wilderness, component, spawn)
            if (result == TreasureTrailGuardians.Result.READY) uri(player, step)
            else if (result == TreasureTrailGuardians.Result.FIGHTING)
                TreasureTrailGuardians.session(player)?.actors?.forEach {
                    it.attr[OWNER] = WeakReference(player)
                    if (!it.isDead()) it.attack(player)
                }
            else if (result == TreasureTrailGuardians.Result.UNAVAILABLE)
                println("PROBLEM CLUE double agent spawn/assets unavailable at ${player.tile}; clue retained")
            return result != TreasureTrailGuardians.Result.UNHANDLED
        }
        return false
    }

    private fun spawn(player: Player, sources: List<Int>): List<Npc>? {
        if (sources.size != 1) return null
        val id = actor(player.world, sources.single()) ?: return null
        val direction = Direction.values().firstOrNull { it != Direction.NONE && player.world.collision.canStep(player.tile, it) }
            ?: return null
        val npc = Npc(id, player.tile.step(direction), player.world)
        npc.respawns = false
        npc.attr[OWNER] = WeakReference(player)
        if (!player.world.spawn(npc)) return null
        npc.forceChat("I expect you to die!")
        return listOf(npc)
    }

    /** The actual spawned identity owns the fight; ordinary same-id NPCs remain ordinary NPCs. */
    fun canAttack(attacker: Pawn, target: Pawn): Boolean {
        val actor = (target as? Npc)?.takeIf { it.attr.has(OWNER) }
        if (actor != null) {
            val owner = actor.attr[OWNER]?.get()
            val credited = attacker as? Player ?: attacker.attr[DAMAGE_CREDIT_ATTR]?.get()
            return owner != null && owner.isOnline && credited === owner && TreasureTrailGuardians.owner(actor) === owner
        }
        if (attacker is Npc && attacker.attr.has(OWNER))
            return attacker.attr[OWNER]?.get() === target && TreasureTrailGuardians.owner(attacker) === target
        return true
    }

    fun killed(npc: Npc, uri: (Player, TreasureTrailSourceStep) -> Unit = { p, s -> TreasureTrailEmotes.afterGuardian(p, s); Unit }) =
        TreasureTrailGuardians.killed(npc, uri)

    fun dropOwner(npc: Npc): Player? {
        if (npc.attr[LOOTED] == true || !npc.isDead()) return null
        val owner = if (npc.attr.has(OWNER)) npc.attr[OWNER]?.get() else npc.damageMap.getMostDamage() as? Player
        if (owner == null || !owner.isOnline) return null
        npc.attr[LOOTED] = true
        return owner
    }
}
