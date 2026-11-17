package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.dealHit
import java.lang.ref.WeakReference

/** Separate from pet/familiar/BoB. Wiki greater-thrall pages rev15350895/96/97: four ticks, 0..3, current Magic ticks, no PvP/XP. */
object UndeadThralls {
    const val COOLDOWN_TICKS = 17 // 10 seconds rounded up to the next server tick
    const val ATTACK_TICKS = 4
    enum class Choice(val sourceNpc: Int, val hit: HitType) { ZUK(15569, HitType.MELEE), KAL(15570, HitType.RANGE), WISP(15571, HitType.MAGIC) }
    /** Asset/path adapter is installed only after range, sequences and projectile timing are evidenced by Claude. */
    interface Appearance {
        fun spawn(player: Player, choice: Choice): Npc?
        fun reach(actor: Npc, target: Npc, choice: Choice): Boolean
        fun follow(actor: Npc, player: Player, target: Npc?)
        fun attack(actor: Npc, target: Npc, choice: Choice): Int // evidenced impact delay, in ticks
    }
    @Volatile var appearance: Appearance? = null
    private val ACTIVE = AttributeKey<Npc>()
    val SUMMON_COOLDOWN = TimerKey(persistenceKey = "dmm2_undead_cooldown", tickOffline = false)
    private val LAST_DAMAGED = AttributeKey<WeakReference<Npc>>()
    val THRALL = AttributeKey<Boolean>()
    enum class Result { OK, NOT_READY, NO_TRINKET, COOLDOWN }
    fun recordDamage(player: Player, target: Npc) { if (target.attr[THRALL] != true) player.attr[LAST_DAMAGED] = WeakReference(target) }
    fun clear(player: Player) {
        player.attr[ACTIVE]?.let { if (it.isSpawned()) player.world.remove(it) }
        player.attr.remove(ACTIVE)
        player.attr.remove(LAST_DAMAGED)
    }
    fun summon(player: Player, choice: Choice): Result {
        if (!DeadmanTrinkets.carries(player, Dmm2Ids.ItemId.UNDEAD)) return Result.NO_TRINKET
        if (player.timers.has(SUMMON_COOLDOWN)) return Result.COOLDOWN
        val look = appearance ?: return Result.NOT_READY
        val actor = look.spawn(player, choice) ?: return Result.NOT_READY
        val last = player.attr[LAST_DAMAGED]
        clear(player)
        if (last != null) player.attr[LAST_DAMAGED] = last
        actor.attr[THRALL] = true
        actor.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        actor.respawnOverride = false
        player.attr[ACTIVE] = actor
        player.timers[SUMMON_COOLDOWN] = COOLDOWN_TICKS
        val duration = player.skills.getCurrentLevel(Skills.MAGIC).coerceAtLeast(1)
        player.world.queue {
            try {
                repeat(duration) { age ->
                    wait(1)
                    if (!player.isOnline || player.isDead() || player.attr[ACTIVE] !== actor || !actor.isSpawned()) return@queue
                    val target = player.attr[LAST_DAMAGED]?.get()?.takeIf { it.isSpawned() && !it.isDead() && Combat.canEngage(player, it) }
                    look.follow(actor, player, target)
                    if (age % ATTACK_TICKS == ATTACK_TICKS - 1 && target != null && look.reach(actor, target, choice)) {
                        val delay = look.attack(actor, target, choice)
                        require(delay >= 0)
                        val damage = player.world.random.nextInt(4)
                        actor.dealHit(target = target, minHit = damage - 0.1, maxHit = damage.toDouble(), landHit = true, delay = delay, hitType = choice.hit)
                    }
                }
            } finally {
                if (actor.isSpawned()) player.world.remove(actor)
                if (player.attr[ACTIVE] === actor) player.attr.remove(ACTIVE)
            }
        }
        return Result.OK
    }
}
