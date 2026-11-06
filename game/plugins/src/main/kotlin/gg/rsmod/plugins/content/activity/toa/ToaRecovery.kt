package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom

/** Sources: 01-route.tsv restoration rows, OSRS Wiki ToA/Strategies, Update:Summer Sweep Up: Combat. */
object ToaRecovery {
    fun challenge(player: Player) {
        Venom.cure(player, immunityTicks = 0, announce = false)
        Poison.cure(player)
    }

    fun bossEntry(player: Player) { player.runEnergy = 100.0 }

    fun full(player: Player) {
        challenge(player)
        // Restore drained levels without removing boosts such as smelling salts.
        for (skill in 0 until player.skills.maxSkills) {
            if (player.skills.getCurrentLevel(skill) < player.skills.getMaxLevel(skill)) player.skills.restore(skill)
        }
        player.setCurrentLifepoints(maxOf(player.getCurrentLifepoints(), player.getMaximumLifepoints()))
        player.setCurrentPrayerPoints(maxOf(player.getCurrentPrayerPoints(), player.getMaximumPrayerPoints()))
        player.runEnergy = 100.0
        AttackTab.setEnergy(player, 100)
    }
}
