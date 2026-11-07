package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import java.util.concurrent.CopyOnWriteArrayList

/**
 * N462 (ToA, ChatGPT-A 2026-11-06 22:59): content that makes a player's attack on an npc always land, or always land for the max
 * hit, decides it HERE - before [dealHit] rolls - instead of turning a miss into a hit at impact. Only then is the hit an ordinary
 * landed hit: damage map (points, loot, kill credit), XP and the attacker's style all follow the normal route. The POH combat dummy
 * and the Venenatis spiderlings use the same branch in [dealHit].
 */
object PlayerAttackRules {
    enum class Rule { NORMAL, ACCURATE, MAX_HIT }

    private val rules = CopyOnWriteArrayList<(Player, Npc, HitType) -> Rule?>()

    /** A rule answers null (or [Rule.NORMAL]) for every attack it does not own. */
    fun register(rule: (player: Player, npc: Npc, hitType: HitType) -> Rule?) {
        rules += rule
    }

    fun of(
        player: Player,
        npc: Npc,
        hitType: HitType,
    ): Rule {
        for (rule in rules) {
            val answer = rule(player, npc, hitType)
            if (answer != null && answer != Rule.NORMAL) return answer
        }
        return Rule.NORMAL
    }
}
