package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.entity.Player

/**
 * Owner answer Q12 (2026-09-14): Ferox's paid Enclave respawn. OSRS Wiki "Ferox Enclave": "Players who have paid Ferox 5000000 coins can
 * set their respawn point to Ferox Enclave"; Transcript:Ferox: "you'll respawn right here in Ferox Enclave, just outside the pub we've set
 * up", "that'd be a one time fee of course", and a paid player can switch back and forth without paying again.
 * The tile is the owner-approved tile next to The Old Nite (OSRS Wiki map: rectangle centred on 3152,3644, 8 x 6).
 */
object FeroxRespawn {
    /** The old price at Ferox. Owner 2026-09-26: Death sells the Ferox respawn now ([gg.rsmod.plugins.content.areas.deathsoffice.RespawnPoints.PRICE]). */
    const val PRICE = 5_000_000

    /**
     * Outside the pub's south wall opening (cache placements 62456 at 3150,3641 / 3151,3641 between the wall pieces 62439 at 3149 and
     * 3152). home_verify proves at every boot that it is safe, standable and reachable on foot from the home arrival tile.
     */
    val TILE = Tile(3150, 3640, 0)

    /** The one-time fee was paid (the respawn can then be switched without paying again). */
    val PAID = AttributeKey<Boolean>(persistenceKey = "ferox_respawn_paid")

    fun isActive(player: Player): Boolean = player.attr[RESPAWN_TILE_ATTR] == TILE.as30BitInteger

    fun activate(player: Player) {
        player.attr[RESPAWN_TILE_ATTR] = TILE.as30BitInteger
    }

    fun deactivate(player: Player) {
        player.attr.remove(RESPAWN_TILE_ATTR)
    }
}
