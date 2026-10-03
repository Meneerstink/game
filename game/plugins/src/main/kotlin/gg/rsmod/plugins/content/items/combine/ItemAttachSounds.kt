package gg.rsmod.plugins.content.items.combine

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.playSound

/**
 * The one sound rule for attaching one item to another (owner 2026-10-03, bug 211127: "when attaching something to something,
 * for example harmonised orb to nightmare staff, it should make a sound - check every item").
 *
 * Root cause: no combine route played any sound. The reverse (Dismantle / Revert / Uncharge / Split) already sounds once in
 * [gg.rsmod.plugins.content.items.ItemActionGuard]; every attach / assemble route now calls [attached] (and the item-on-item
 * detach routes - knife on a max cape, cleaning cloth on an ornamented item - call [detached]). The guard test
 * `ItemAttachSoundsTests` fails when a new item-on-item attach route in the item plugins does not.
 *
 * Sounds (667 synth ids, checked byte-for-byte against the pinned OSRS cache openrs2-2686 index 4 on 2026-10-03):
 * - [GODSWORD_FIX]: a hilt on the godsword blade. SOURCE: OSRS Wiki "File:Godsword attach hilt.wav" =
 *   `godwars_godsword_fix` id 3857, used on the Armadyl/Bandos/Saradomin/Zamorak godsword pages; the 667 synth with the same
 *   bytes is 3889.
 * - [ATTACH_ORB]: an orb on a staff (Nightmare staff orbs, Orb of Armadyl). ADAPTED by Jagex name: `attach_orb` 2585
 *   (OSRS Wiki "List of sound IDs"; same id and bytes in OSRS and 667). No OSRS page names the Nightmare staff's own sound.
 * - [ATTACH]: every other attach (ornament / colour kits, upgrade kits, max cape variants, boot crystals, ...). ADAPTED: no
 *   OSRS source names a sound for these; owner decision 2026-10-03 that every attach makes a sound. `attach_handle` 2287
 *   (same id and bytes in OSRS and 667).
 * - [DETACH]: the item-on-item detach routes reuse ItemActionGuard's Dismantle sound (`hunting_dismantle` 2632).
 */
object ItemAttachSounds {
    const val GODSWORD_FIX = 3889
    const val ATTACH_ORB = Sfx.ATTACH_ORB
    const val ATTACH = Sfx.ATTACH_HANDLE
    const val DETACH = Sfx.HUNTING_DISMANTLE

    val GODSWORD_HILTS =
        setOf(Items.ARMADYL_HILT, Items.BANDOS_HILT, Items.SARADOMIN_HILT, Items.ZAMORAK_HILT, Items.ANCIENT_HILT)

    val ORBS = setOf(Items.HARMONISED_ORB, Items.VOLATILE_ORB, Items.ELDRITCH_ORB, Items.ORB_OF_ARMADYL)

    /** The sound for attaching [inputs] together (the result does not change it). */
    fun forAttach(inputs: Collection<Int>): Int =
        when {
            inputs.any { it in GODSWORD_HILTS } && Items.GODSWORD_BLADE in inputs -> GODSWORD_FIX
            inputs.any { it in ORBS } -> ATTACH_ORB
            else -> ATTACH
        }

    /** Plays the attach sound once the items are really combined (call after the result is in the inventory). */
    fun attached(
        player: Player,
        vararg inputs: Int,
    ) {
        player.playSound(forAttach(inputs.toList()))
    }

    /** A chisel used on an item to carve it into gear (Tanzanite fang, Serpentine visage, chisel combination rows): `chisel` 2586. */
    fun carved(player: Player) {
        player.playSound(Sfx.CHISEL)
    }

    /** Plays the detach sound once an item-on-item detach really happened. */
    fun detached(player: Player) {
        player.playSound(DETACH)
    }
}
