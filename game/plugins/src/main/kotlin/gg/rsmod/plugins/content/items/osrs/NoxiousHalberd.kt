package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.content.combat.venom

/**
 * OSRS-IMPORT Noxious halberd (OSRS Wiki "Noxious halberd" raw wikitext and "Special attacks", 2026-09-14).
 *
 * - Passive: "has a 33% chance (50% with a Serpentine helm equipped) to envenom the target (if not venom immune)". No
 *   Serpentine helm exists in this server, so the chance is 33%. The page gives no hit condition: the chance is rolled
 *   for every attack when its hit resolves, as for the Toxic blowpipe (SOURCE_GAP recorded). Venom immunity is checked
 *   by `Venom.envenom`.
 * - Virulence (special) is NOT built: "Minimum hit equal to the damage the cured poison or venom would have dealt" does
 *   not say whether that is the next poison/venom hit or all remaining damage (venom never wears off, so a total is
 *   unbounded). Recorded as an owner question in OSRS_IMPORT_STATUS.md.
 */
object NoxiousHalberd {
    const val VENOM_CHANCE = 0.33

    fun isWielding(player: Player): Boolean = player.hasEquipped(EquipmentType.WEAPON, Items.NOXIOUS_HALBERD)

    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        if (player.world.randomDouble() < VENOM_CHANCE) target.venom()
    }
}
