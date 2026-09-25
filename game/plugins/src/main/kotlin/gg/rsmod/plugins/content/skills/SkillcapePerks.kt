package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.items.osrs.MaxCapes

/**
 * The one place every skillcape perk asks "does this player have it?" (owner 2026-09-24: "Veel maxcapes die we hebben missen nog
 * de juiste functies ... zorg dat alle max capes precies t zelfde is als de osrs max cape").
 *
 * OSRS Wiki "Cape of Accomplishment" (perks table) and "Max cape":
 * - a skillcape (trimmed or not) gives its own perks, most "when worn", some also "in the inventory";
 * - "A max cape will provide all the perks of all skillcapes" - the plain max cape;
 * - a max cape variant (fire, infernal, assembler, god, Dizana's ...) forfeits the perks and keeps only: Cooks' Guild access (as the
 *   Cooking cape), Crafting Guild access (as the Crafting cape), essence pouches that do not degrade (as the Runecraft cape), warm
 *   clothing (as the Firemaking cape) and the Skill Cape emote ([VARIANT_PERKS]).
 */
object SkillcapePerks {
    /** The perks every max cape variant keeps ("Max cape", Variants), each from the skillcape it comes from. */
    enum class VariantPerk(val cape: Skillcapes) {
        COOKS_GUILD(Skillcapes.COOKING),
        CRAFTING_GUILD(Skillcapes.CRAFTING),
        ESSENCE_POUCHES(Skillcapes.RUNECRAFTING),
        WARM_CLOTHING(Skillcapes.FIREMAKING),
    }

    private fun capesOf(cape: Skillcapes) = setOf(cape.untrimmedCape, cape.trimmedCape)

    private fun variantCapes(): Set<Int> = MaxCapes.WEARABLE_VARIANTS

    fun wornCape(player: Player): Int? = player.getEquipment(EquipmentType.CAPE)?.id

    /** Wearing the plain max cape (all perks). */
    fun wearingMaxCape(player: Player): Boolean = wornCape(player) == MaxCapes.MAX_CAPE

    /** Wearing any max cape: the plain one or a variant. */
    fun wearingAnyMaxCape(player: Player): Boolean = wornCape(player).let { it == MaxCapes.MAX_CAPE || it in variantCapes() }

    /** The perk of [cape] while the cape (trimmed or not) or the plain max cape is worn. */
    fun worn(
        player: Player,
        cape: Skillcapes,
    ): Boolean {
        val id = wornCape(player) ?: return false
        return id in capesOf(cape) || id == MaxCapes.MAX_CAPE
    }

    /** One of the [VariantPerk]s: its skillcape, the plain max cape or any max cape variant worn. */
    fun worn(
        player: Player,
        perk: VariantPerk,
    ): Boolean = worn(player, perk.cape) || wearingAnyMaxCape(player)

    /** Per-day uses of the limited perks (stamina, spellbook swaps, hunter teleports, searches): reset at 00:00 UTC like OSRS. */
    enum class DailyUse(val limit: Int) {
        STAMINA(gg.rsmod.plugins.content.items.osrs.MaxCapeTeleports.STAMINA_PER_DAY),
        SPELLBOOK(gg.rsmod.plugins.content.items.osrs.MaxCapeTeleports.SPELLBOOK_SWAPS_PER_DAY),
        HUNTER_TELEPORT(gg.rsmod.plugins.content.items.osrs.MaxCapeTeleports.HUNTER_TELEPORTS_PER_DAY),
        FLETCHING_SEARCH(gg.rsmod.plugins.content.items.osrs.MaxCapeTeleports.SEARCHES_PER_DAY),
        ;

        /** "epochDay:used" (persistent). */
        val key = gg.rsmod.game.model.attr.AttributeKey<String>(persistenceKey = "skillcape_daily_${name.lowercase()}")
    }

    private fun today(): Long = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()

    fun usedToday(
        player: Player,
        use: DailyUse,
    ): Int {
        val (day, used) = (player.attr[use.key] ?: return 0).split(':').let { (it.getOrNull(0)?.toLongOrNull() ?: 0L) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        return if (day == today()) used else 0
    }

    fun remainingToday(
        player: Player,
        use: DailyUse,
    ): Int = (use.limit - usedToday(player, use)).coerceAtLeast(0)

    /** Takes one use of [use] for today; false when none are left. */
    fun takeDailyUse(
        player: Player,
        use: DailyUse,
    ): Boolean {
        val used = usedToday(player, use)
        if (used >= use.limit) return false
        player.attr[use.key] = "${today()}:${used + 1}"
        return true
    }

    /** The perk of [cape] while the cape (or the plain max cape) is worn or carried in the inventory. */
    fun wornOrCarried(
        player: Player,
        cape: Skillcapes,
    ): Boolean =
        worn(player, cape) || capesOf(cape).any { player.inventory.contains(it) } || player.inventory.contains(MaxCapes.MAX_CAPE)
}
