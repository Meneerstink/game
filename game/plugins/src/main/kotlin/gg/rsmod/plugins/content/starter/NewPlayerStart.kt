package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig

/** New-account rewards whose values are confirmed product requirements. */
object NewPlayerStart {
    /**
     * Owner 2026-10-10: 1m (was 50k) - the same as one vote. Owner 2026-10-22 (N179): it comes out of the Deadman starter pack
     * into the inventory, no longer into the bank at account creation.
     */
    const val STARTER_CASH = 1_000_000

    /** The Deadman starter pack has been handed out (saved: replaying the introduction never gives a second one). */
    val STARTER_PACK_GIVEN = AttributeKey<Boolean>(persistenceKey = "deadman_starter_pack_given")

    /**
     * Owner 2026-10-08: a new player starts with an empty inventory and gets only the Deadman starter pack (OSRS item 22330,
     * here [NewPlayerConfig.starterChestItem]) once the introduction is over - finished, skipped or interrupted.
     */
    fun grantStarterPack(player: Player) {
        DeadmanStarterPack.migrate(player)
        if (player.attr[STARTER_PACK_GIVEN] == true) return
        val result = DeadmanStarterPack.reclaim(player)
        if (result != DeadmanStarterPack.Result.OK && result != DeadmanStarterPack.Result.ALREADY_OWNED) return
        player.attr[STARTER_PACK_GIVEN] = true
        DeadmanStarterPack.grantIntroductionReward(player)
        player.persistNow()
    }

    /** DMM2 V2: wiki equipment only; legacy config cash rows are no longer repeatable pack contents. */
    fun openStarterPack(player: Player) {
        when (DeadmanStarterPack.open(player)) {
            DeadmanStarterPack.Result.NO_SPACE -> player.message("You need 11 free inventory spaces to open this.")
            else -> Unit // NOT_READY deliberately leaves the pack intact until Claude installs the real cache mapping.
        }
    }
}
