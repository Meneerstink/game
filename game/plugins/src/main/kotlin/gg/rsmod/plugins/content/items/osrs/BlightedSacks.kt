package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.zoneTile

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/**
 * OSRS-IMPORT Blighted sacks (owner addition 2026-09-14). Sources (OSRS Wiki raw wikitext, fetched 2026-09-14):
 * - "Blighted entangle sack" (24613): "allows players to cast Entangle, Snare, or Bind without needing the runes for it",
 *   "It is consumed upon cast, regardless of a successful hit"; usable "inside the Wilderness, Ferox Enclave, Mage Arena bank,
 *   PvP worlds, Castle Wars, Clan Wars, and Soul Wars" (13 Nov 2024 / 11 Sep 2024 changelogs).
 * - "Blighted ancient ice sack" (24607): "any ice spell within their Magic level without needing the runes for it", Ancient
 *   Magicks active, "Functions like runes, enabling autocasting"; same locations.
 * - "Blighted vengeance sack" (24621): "cast Vengeance or Vengeance Other without needing the runes for it", "consumed upon
 *   use"; same locations.
 * - "Blighted teleport spell sack" (24615): "Enough power for a Teleblock or a Teleport to Target", level 85 Magic, "Tele Block
 *   still requires using the Standard spellbook", "It can only be used inside the Wilderness".
 * The spells' own level, spellbook and target rules stay in force; a sack only replaces the runes, one sack per cast.
 * Location model (ADAPTED, recorded): this server allows PvP everywhere outside safe zones (AreaState R03.1, a PvP-world
 * model), so the three "PvP worlds" sacks work wherever PvP is allowed, in the Wilderness and in the Ferox Enclave home;
 * bank safe zones are not on the list. OWNER OVERRIDE 2026-09-22: "ALL BLIGHTED SACKS NEEDS TO WORK IN THE WHOLE MAP JUST NOT
 * THE SAFE ZONES" - the teleport spell sack is no longer Wilderness-only; every sack works wherever PvP is allowed.
 * CONTEXT_UNAVAILABLE: Teleport to Target (no 667 spell), Bounty Hunter shop.
 */
object BlightedSacks {
    enum class Sack(
        val item: Int,
        val spells: Set<Int>,
        val wildernessOnly: Boolean,
    ) {
        ANCIENT_ICE(
            Items.BLIGHTED_ANCIENT_ICE_SACK,
            setOf(SpellbookData.ICE_RUSH, SpellbookData.ICE_BURST, SpellbookData.ICE_BLITZ, SpellbookData.ICE_BARRAGE).map { it.uniqueId }.toSet(),
            wildernessOnly = false,
        ),
        ENTANGLE(
            Items.BLIGHTED_ENTANGLE_SACK,
            setOf(SpellbookData.BIND, SpellbookData.SNARE, SpellbookData.ENTANGLE).map { it.uniqueId }.toSet(),
            wildernessOnly = false,
        ),
        TELEPORT_SPELL(
            Items.BLIGHTED_TELEPORT_SPELL_SACK,
            setOf(SpellbookData.TELEPORT_BLOCK.uniqueId),
            wildernessOnly = false,
        ),
        VENGEANCE(
            Items.BLIGHTED_VENGEANCE_SACK,
            setOf(SpellbookData.VENGEANCE, SpellbookData.VENGEANCE_OTHER).map { it.uniqueId }.toSet(),
            wildernessOnly = false,
        ),
    }

    fun sackFor(spellId: Int): Sack? = if (spellId < 0) null else Sack.values().firstOrNull { spellId in it.spells }

    fun allowedAt(
        player: Player,
        sack: Sack,
    ): Boolean {
        if (player.tile.getWildernessLevel() > 0) return true
        if (sack.wildernessOnly) return false
        val home = player.world.gameContext.home
        return AreaState.isPvpAllowed(player.zoneTile(), home)
    }

    /** True when a sack in the inventory replaces the runes of [spellId] here. */
    fun usable(
        player: Player,
        spellId: Int,
    ): Boolean {
        val sack = sackFor(spellId) ?: return false
        return player.inventory.contains(sack.item) && allowedAt(player, sack)
    }

    /** Uses one sack for [spellId]; false when no sack applies (the runes are used instead). */
    fun consume(
        player: Player,
        spellId: Int,
    ): Boolean {
        if (!usable(player, spellId)) return false
        return player.inventory.remove(sackFor(spellId)!!.item, 1).hasSucceeded()
    }

    const val SAFE_ZONE_MESSAGE = "Your blighted sack has no power here - it only works outside the safe zones."

    /**
     * Shown instead of the missing-rune message when the player carries the sack for [spellId] but stands in a safe zone
     * (Deadman guarded city), so the refusal names the real reason. Null when that is not the situation.
     */
    fun safeZoneRefusal(
        player: Player,
        spellId: Int,
    ): String? {
        val sack = sackFor(spellId) ?: return null
        if (!player.inventory.contains(sack.item) || allowedAt(player, sack)) return null
        return SAFE_ZONE_MESSAGE
    }

    /** Client varc read by the patched spellbook scripts (`BlightedSackSpellbookPatchTool.VARC_SACKS_ALLOWED`). */
    const val VARC_SACKS_ALLOWED = 1415
    private val CLIENT_ALLOWED_ATTR = gg.rsmod.game.model.attr.AttributeKey<Boolean>()

    /**
     * Keeps the spellbook in step with where sacks work: varc 1415 = 1 outside the safe zones, 0 inside. On a change the
     * inventory is resent, which re-runs the spell icon script so sack spells light up or grey out at once. Called every
     * cycle from the Deadman HUD refresh; sends nothing while the state is unchanged.
     */
    fun syncClient(player: Player) {
        val allowed = allowedAt(player, Sack.ENTANGLE)
        if (player.attr[CLIENT_ALLOWED_ATTR] == allowed) return
        player.attr[CLIENT_ALLOWED_ATTR] = allowed
        player.setVarc(VARC_SACKS_ALLOWED, if (allowed) 1 else 0)
        player.inventory.dirty = true
    }
}
