package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.ext.playSound

/**
 * "Renew-points" restores Summoning points, the only way back from an empty pool besides a
 * Summoning potion: "you will often find that you need to recharge. You can do this by heading
 * to any Summoning obelisk, right-clicking on it and selecting 'Renew-Points', or by drinking a
 * Summoning potion."
 *
 * The option is bound from the object definitions in data/cache rather than from a hand-written
 * id list. The previous list named eleven "Summoning obelisk" ids, five of which (54650, 56083,
 * 56084, 56085 and 56086) carry no options at all in this revision, and it named none of the
 * objects players actually renew at - the seven world obelisks (28716-28734), the small obelisks
 * (5787 and 29938-29959) or the player-owned-house obelisks (44837-44842). Renewing at any of
 * those fell through to "Nothing interesting happens" and left the account stuck at zero points.
 *
 * The special-move pool is deliberately not touched here. The knowledge base recharges it two
 * ways - "This will recharge over time" and "Summoning potions also restore a portion of your
 * special move bar" - and that "also" is what separates the potion from the obelisk, which it
 * describes as renewing points only.
 *
 * Presentation follows Void `renewSummoningPoints` (see [Familiar.RENEW_OBELISK_GRAPHIC]): the
 * obelisk graphic, whose cache sequence carries its own sound, then the player's renew animation,
 * graphic and sound once the obelisk graphic has played.
 */
val renewPointObelisks =
    world.definitions
        .getAll(ObjectDef::class.java)
        .values
        .filterIsInstance<ObjectDef>()
        .filter { def -> def.options.any { it?.equals("Renew-points", ignoreCase = true) == true } }
        .map { it.id }
        .sorted()

check(renewPointObelisks.size == 39) {
    "Expected the 39 sourced Renew-points objects, found ${renewPointObelisks.size}: $renewPointObelisks"
}

renewPointObelisks.forEach { obelisk ->
    on_obj_option(obelisk, "Renew-points") {
        if (Familiar.currentPoints(player) >= Familiar.maxPoints(player)) {
            player.message("You already have full Summoning points.")
        } else {
            val obeliskTile = player.getInteractingGameObj().tile
            world.spawn(TileGraphic(obeliskTile, id = Familiar.RENEW_OBELISK_GRAPHIC, height = 0))
            player.queue {
                wait(Familiar.RENEW_GRAPHIC_TICKS)
                Familiar.restorePoints(player)
                player.animate(Familiar.RENEW_ANIMATION)
                world.spawn(TileGraphic(player.tile, id = Familiar.RENEW_PLAYER_GRAPHIC, height = 0))
                FamiliarAudio.play(player, Familiar.RENEW_SOUND)
                player.message("You renew your Summoning points.")
            }
        }
    }
}
