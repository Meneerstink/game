package gg.rsmod.plugins.content.combat.magic

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.AutocastInterfaceLayout
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.items.osrs.AncientSceptres
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/**
 * The one weapon <-> spellbook <-> spell compatibility registry for combat magic (OSRS rules, applied only to content this server has).
 *
 * Sources (OSRS Wiki, read 2026-09-24):
 * - "Autocast": "an attack option available for all Magic weapons (except powered staves and salamanders)".
 * - "Ancient Magicks": "Unlike the standard spells, Ancient Magicks combat spells can only be autocast with particular Magic weapons" -
 *   ancient staff, ancient sceptres, Blue moon spear, master wand, kodai wand, dragon hunter wand, nightmare / eldritch / volatile
 *   nightmare staff, Thammaron's / Accursed sceptre (a) (not on this server), Ahrim's staff only with the amulet of the damned (not on
 *   this server, so Ahrim's staff never autocasts Ancient Magicks here). Zuriel's staff is a 667 weapon built for Ancient Magicks (its
 *   Miasmic spells need it), so it keeps Ancient autocast.
 * - "God spells": Saradomin Strike needs a Saradomin staff or staff of light; Claws of Guthix a Guthix staff, void knight mace or
 *   staff of balance; Flames of Zamorak a Zamorak staff, staff of the dead or toxic staff of the dead - to cast and to autocast.
 * - Iban Blast / Magic Dart / Miasmic spells: the weapons in [CombatSpell.requiredWeapons].
 * - "Harmonised nightmare staff": "cannot autocast any other spells (including Ancient Magicks)" -> standard spells only.
 */
object AutocastWeapons {
    private val STAFF_OF_LIGHT = setOf(Items.STAFF_OF_LIGHT, Items.STAFF_OF_LIGHT_22207, Items.STAFF_OF_LIGHT_22209, Items.STAFF_OF_LIGHT_22211, Items.STAFF_OF_LIGHT_22213)

    /** God spells: the weapons that may cast them - manually and on autocast. */
    val GOD_SPELL_WEAPONS: Map<CombatSpell, Set<Int>> =
        mapOf(
            CombatSpell.SARADOMIN_STRIKE to setOf(Items.SARADOMIN_STAFF) + STAFF_OF_LIGHT,
            CombatSpell.CLAWS_OF_GUTHIX to setOf(Items.GUTHIX_STAFF, Items.VOID_KNIGHT_MACE, Items.STAFF_OF_BALANCE),
            CombatSpell.FLAMES_OF_ZAMORAK to setOf(Items.ZAMORAK_STAFF, Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD),
        )

    /** Weapons that may autocast Ancient Magicks. */
    val ANCIENT_WEAPONS: Set<Int> =
        setOf(
            Items.ANCIENT_STAFF, Items.MASTER_WAND, Items.KODAI_WAND, Items.DRAGON_HUNTER_WAND,
            Items.NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.BLUE_MOON_SPEAR,
            Items.ZURIELS_STAFF, Items.ZURIELS_STAFF_DEG, Items.CORRUPT_ZURIELS_STAFF, Items.CORRUPT_ZURIELS_STAFF_DEG,
        ) + AncientSceptres.ALL

    /** Magic weapons whose 667 weapon class is not a staff (they still use the magic weapon rules). */
    private val NON_STAFF_MAGIC_WEAPONS: Set<Int> = setOf(Items.VOID_KNIGHT_MACE, Items.BLUE_MOON_SPEAR)

    /** Spells that need a specific weapon to be cast at all (manual cast and autocast alike); empty = any weapon. */
    fun requiredWeapons(spell: CombatSpell): Set<Int> = GOD_SPELL_WEAPONS[spell] ?: spell.requiredWeapons.toSet()

    fun requiredWeaponMessage(spell: CombatSpell): String =
        spell.requiredWeaponMessage.ifEmpty { "You need to wield the right staff to cast this spell." }

    /**
     * True for a weapon that uses spellbook autocast: staves, bladed staves, wands, sceptres and the listed specials - never a powered
     * staff (its own built-in spell, [PoweredStaves]) and never a salamander.
     */
    fun isAutocastWeapon(definition: ItemDef?): Boolean {
        if (definition == null) return false
        if (PoweredStaves.staffFor(definition.id) != null) return false
        if (definition.id in NON_STAFF_MAGIC_WEAPONS || definition.id in ANCIENT_WEAPONS) return true
        val name = definition.name.lowercase()
        val weaponClass = weaponClass(definition)
        return weaponClass == WeaponType.STAFF.id || weaponClass == WeaponType.BLADED_STAFF.id ||
            name.endsWith(" wand") || name.endsWith("sceptre")
    }

    /** The weapon style set: the server metadata value, else the cache's own item param 686 (what the client's combat tab reads). */
    private fun weaponClass(definition: ItemDef): Int =
        definition.weaponType.takeIf { it >= 0 } ?: (definition.params.get(WEAPON_CLASS_PARAM) as? Int ?: -1)

    private const val WEAPON_CLASS_PARAM = 686

    /** "Normal" spellbook combat casts take 5 ticks (OSRS Wiki "Attack speed"). */
    const val SPELL_ATTACK_SPEED = 5

    /**
     * Weapon exceptions to [SPELL_ATTACK_SPEED]: weapon -> (ticks, applies only to autocast standard spells). Harmonised nightmare
     * staff: "attack speed is increased to 4" for standard spells, and "The 4-tick spell speed only applies when autocasting".
     * Twinflame staff (6 ticks) is not on this server.
     */
    val SPELL_SPEED_EXCEPTIONS: Map<Int, Pair<Int, Boolean>> = mapOf(Items.HARMONISED_NIGHTMARE_STAFF to (4 to true))

    fun spellAttackSpeed(weaponId: Int?, spell: CombatSpell?, autocast: Boolean): Int {
        val (ticks, autocastStandardOnly) = SPELL_SPEED_EXCEPTIONS[weaponId] ?: return SPELL_ATTACK_SPEED
        if (autocastStandardOnly && (!autocast || spell == null || spell.interfaceId != Autocast.STANDARD_BOOK)) return SPELL_ATTACK_SPEED
        return ticks
    }

    fun isPoweredStaff(itemId: Int?): Boolean = PoweredStaves.staffFor(itemId) != null

    /** Why [weaponId] can or cannot autocast [spell] - null means it can. */
    fun incompatibility(definition: ItemDef?, spell: CombatSpell): String? {
        if (definition == null || !isAutocastWeapon(definition)) return "You need a magic weapon to autocast spells."
        if (spell.autoCastId <= 0 || spell.componentId == -1) return "That spell can't be autocast."
        val required = requiredWeapons(spell)
        if (required.isNotEmpty() && definition.id !in required) return requiredWeaponMessage(spell)
        if (spell.interfaceId == Autocast.ANCIENT_BOOK && definition.id !in ANCIENT_WEAPONS) return "You can't autocast Ancient Magicks with this weapon."
        return null
    }
}

/**
 * OSRS autocast state and rules (OSRS Wiki "Autocast", update history read 2026-09-24). The server owns it; the client only shows it.
 *
 * - [SELECTED] (persisted): the chosen spell, by [CombatSpell.autoCastId]. Kept while the spell is temporarily unusable: "Autocast will
 *   now remain selected if you do not have the required level or runes to cast the selected spell" (9 April 2025), and through death
 *   (22 July 2026). Forgotten only when "you equip a staff that cannot autocast the chosen spell", on the PvP swap rule below, on a
 *   spellbook change. Picking a melee style only switches autocast OFF ([MODE]); the choice is kept.
 * - [MODE] (persisted): OFF / STANDARD / DEFENSIVE - chosen with the Combat Options "Spell" box.
 * - [AUTO_CAST] (transient): the spell in [Combat.CASTING_SPELL] came from autocast, not from a manual cast.
 * - PvP swap rule (25 March 2026): "In PvP areas, if you have not attacked another player in the last 20 ticks, swapping between
 *   weapons will retain any autocast settings" - equipping a magic weapon within [AutocastPolicy.pvpSwapWindowTicks] of the player's
 *   own last attack on a player, while standing on a PvP tile, forgets the spell.
 *
 * Every state change goes through [select] / [deactivate] / [clear] with a [Reason], logged when [AutocastPolicy.debug] is on.
 * The display mirrors (varp 108 = active spell for the 667 spellbook highlight, varp 43 = style box, the 884 Spell box) are written
 * only by [sync].
 */
object Autocast {
    const val STANDARD_BOOK = 192
    const val ANCIENT_BOOK = 193

    val SELECTED = AttributeKey<Int>("autocast_spell")
    val MODE = AttributeKey<Int>("autocast_mode")
    val AUTO_CAST = AttributeKey<Boolean>()
    /** World cycle of this player's own last attack on another player (outgoing only - being attacked never sets it). */
    val LAST_PLAYER_ATTACK_CYCLE = AttributeKey<Int>()
    /** The target a pending manual cast was aimed at; the spell never fires at anyone else. */
    val MANUAL_TARGET = AttributeKey<java.lang.ref.WeakReference<gg.rsmod.game.model.entity.Pawn>>()
    /** Mode the open selection panel will set (1 standard, 2 defensive). */
    val PENDING_MODE = AttributeKey<Int>()

    /** Style-varp value that marks the Spell box as the chosen style: slot 14 is hidden for every autocast weapon. */
    const val AUTOCAST_STYLE = 3

    enum class Mode { OFF, STANDARD, DEFENSIVE }

    enum class Reason {
        SELECTED_SPELL, MELEE_STYLE, CANCELLED, INCOMPATIBLE_WEAPON, PVP_WEAPON_SWAP, SPELLBOOK_CHANGED, LEGACY_MIGRATION, INVALID_REQUEST,
    }

    fun selected(player: Player): CombatSpell? {
        val id = player.attr[SELECTED] ?: return null
        return autocastable().firstOrNull { it.autoCastId == id }
    }

    fun mode(player: Player): Mode = Mode.values().getOrElse(player.attr[MODE] ?: 0) { Mode.OFF }

    fun isDefensiveCast(player: Player): Boolean = player.attr[AUTO_CAST] == true && mode(player) == Mode.DEFENSIVE

    /** Every spell that can be autocast at all, in spellbook-table order (NPC-only spells never). */
    fun autocastable(): List<CombatSpell> = CombatSpell.values.filter { it.autoCastId > 0 && it.componentId != -1 }

    fun bookOf(spell: CombatSpell): Spellbook = if (spell.interfaceId == ANCIENT_BOOK) Spellbook.ANCIENT else Spellbook.STANDARD

    fun weaponDef(player: Player): ItemDef? =
        player.getEquipment(EquipmentType.WEAPON)?.let { player.world.definitions.get(ItemDef::class.java, it.id) }

    /** selectionValid: the spell belongs to the open spellbook and the wielded weapon may autocast it (no level / rune check). */
    fun isSelectionValid(player: Player, spell: CombatSpell): Boolean =
        bookOf(spell) == player.getSpellbook() && AutocastWeapons.incompatibility(weaponDef(player), spell) == null

    /**
     * The spell an attack right now would autocast: mode on, a selection, and a weapon that may cast it. Level and runes are NOT
     * checked here (currentlyCastable is the shared spell engine's job), so a temporarily unusable spell makes the attack fail with
     * the engine's message instead of falling back to a staff bash.
     */
    fun resolve(player: Player): CombatSpell? {
        if (mode(player) == Mode.OFF) return null
        val spell = selected(player) ?: return null
        return spell.takeIf { isSelectionValid(player, it) }
    }

    fun select(player: Player, spell: CombatSpell, mode: Mode, reason: Reason = Reason.SELECTED_SPELL) {
        player.attr[SELECTED] = spell.autoCastId
        player.attr[MODE] = mode.ordinal
        trace(player, "select ${spell.name} mode=$mode reason=$reason")
        sync(player)
    }

    /** Autocast off; the chosen spell is remembered. */
    fun deactivate(player: Player, reason: Reason) {
        if (mode(player) == Mode.OFF) return
        player.attr[MODE] = Mode.OFF.ordinal
        dropAutoSpell(player)
        trace(player, "deactivate reason=$reason")
        sync(player)
    }

    /** Forget the chosen spell (and switch autocast off). */
    fun clear(player: Player, reason: Reason) {
        if (player.attr[SELECTED] == null && mode(player) == Mode.OFF) return
        player.attr.remove(SELECTED)
        player.attr[MODE] = Mode.OFF.ordinal
        dropAutoSpell(player)
        trace(player, "clear reason=$reason")
        sync(player)
    }

    private fun dropAutoSpell(player: Player) {
        if (player.attr[AUTO_CAST] == true) {
            player.attr.remove(Combat.CASTING_SPELL)
            player.attr.remove(AUTO_CAST)
        }
    }

    /** A weapon was equipped or removed. */
    fun onWeaponChanged(player: Player) {
        val spell = selected(player)
        val weapon = player.getEquipment(EquipmentType.WEAPON)
        val def = weaponDef(player)
        if (spell != null && weapon != null && (AutocastWeapons.isAutocastWeapon(def) || AutocastWeapons.isPoweredStaff(weapon.id))) {
            when {
                attackedPlayerRecently(player) && AreaState.isDangerous(player.tile) -> clear(player, Reason.PVP_WEAPON_SWAP)
                AutocastWeapons.incompatibility(def, spell) != null -> clear(player, Reason.INCOMPATIBLE_WEAPON)
            }
        }
        // A spell cast by autocast with the old weapon must never fire with the new one.
        if (player.attr[AUTO_CAST] == true && resolve(player) == null) dropAutoSpell(player)
        sync(player)
    }

    fun onSpellbookChanged(player: Player) {
        val spell = selected(player) ?: return sync(player)
        if (bookOf(spell) != player.getSpellbook()) clear(player, Reason.SPELLBOOK_CHANGED) else sync(player)
    }

    fun onOutgoingPlayerAttack(player: Player) {
        player.attr[LAST_PLAYER_ATTACK_CYCLE] = player.world.currentCycle
    }

    fun attackedPlayerRecently(player: Player): Boolean {
        val last = player.attr[LAST_PLAYER_ATTACK_CYCLE] ?: return false
        return player.world.currentCycle - last < AutocastPolicy.pvpSwapWindowTicks()
    }

    /**
     * The combat loop's single autocast entry point: loads the autocast spell into [Combat.CASTING_SPELL] (unless a manual cast is
     * pending) and drops a stale autocast spell once autocast no longer applies (mode off, weapon switched, spellbook changed).
     */
    fun prepareAttack(player: Player, target: gg.rsmod.game.model.entity.Pawn) {
        val manualPending = player.attr.has(Combat.CASTING_SPELL) && player.attr[AUTO_CAST] != true
        if (manualPending) {
            // A manual cast that was interrupted (walked away, target died, teleport) must not fire later at another target.
            if (player.attr[MANUAL_TARGET]?.get() === target) return
            player.attr.remove(Combat.CASTING_SPELL)
            player.attr.remove(MANUAL_TARGET)
        }
        val spell = resolve(player)
        if (spell != null) {
            player.attr[Combat.CASTING_SPELL] = spell
            player.attr[AUTO_CAST] = true
        } else {
            dropAutoSpell(player)
        }
    }

    /** A manual cast (spell used on a target): single cast, the saved autocast choice is untouched. */
    fun markManualCast(player: Player, spell: CombatSpell, target: gg.rsmod.game.model.entity.Pawn) {
        player.attr[Combat.CASTING_SPELL] = spell
        player.attr[MANUAL_TARGET] = java.lang.ref.WeakReference(target)
        player.attr.remove(AUTO_CAST)
    }

    /** Death / respawn: no cast survives; the saved choice does (OSRS 22 July 2026), and the client is re-synced. */
    fun onDeath(player: Player) {
        player.attr.remove(Combat.CASTING_SPELL)
        player.attr.remove(AUTO_CAST)
        player.attr.remove(MANUAL_TARGET)
        player.attr.remove(PENDING_MODE)
        sync(player)
    }

    /** Legacy saves: varp 108 held the spell and varp 439 bit 8 the Defensive Casting flag (shared with the client spellbook bits). */
    fun migrateLegacy(player: Player) {
        val legacySpell = player.getVarp(Combat.SELECTED_AUTOCAST_VARP)
        val legacyDefensive = player.getVarp(LEGACY_DEFENSIVE_VARP) and LEGACY_DEFENSIVE_BIT != 0
        if (legacyDefensive) player.setVarp(LEGACY_DEFENSIVE_VARP, player.getVarp(LEGACY_DEFENSIVE_VARP) and LEGACY_DEFENSIVE_BIT.inv())
        if (player.attr[MODE] == null && legacySpell > 0) {
            val spell = autocastable().firstOrNull { it.autoCastId == legacySpell }
            if (spell != null) {
                player.attr[SELECTED] = spell.autoCastId
                player.attr[MODE] = (if (legacyDefensive) Mode.DEFENSIVE else Mode.STANDARD).ordinal
                trace(player, "migrate ${spell.name}")
            }
        }
    }

    const val LEGACY_DEFENSIVE_VARP = 439
    const val LEGACY_DEFENSIVE_BIT = 256

    /** Writes every display mirror from the authoritative state. */
    fun sync(player: Player) {
        val active = resolve(player)
        player.setVarp(Combat.SELECTED_AUTOCAST_VARP, active?.autoCastId ?: 0)
        val weaponAutocasts = AutocastWeapons.isAutocastWeapon(weaponDef(player))
        if (weaponAutocasts && mode(player) != Mode.OFF && selected(player) != null) {
            if (player.getVarp(AttackTab.ATTACK_STYLE_VARP) != AUTOCAST_STYLE) player.setVarp(AttackTab.ATTACK_STYLE_VARP, AUTOCAST_STYLE)
        } else if (player.getVarp(AttackTab.ATTACK_STYLE_VARP) == AUTOCAST_STYLE && weaponAutocasts) {
            player.setVarp(AttackTab.ATTACK_STYLE_VARP, 0)
        }
        refreshCombatTab(player)
    }

    /** The 884 Spell box: shown only for an autocast weapon; icon, shield marker and highlight follow the state. */
    fun refreshCombatTab(player: Player) {
        val l = AutocastInterfaceLayout
        val show = AutocastWeapons.isAutocastWeapon(weaponDef(player))
        player.setComponentHidden(l.COMBAT_TAB, l.BOX_LAYER, !show)
        if (!show) return
        player.setInterfaceEvents(l.COMBAT_TAB, l.BOX_BUTTON, -1..-1, OP1 or OP2)
        val spell = selected(player)
        val mode = mode(player)
        val on = mode != Mode.OFF && spell != null
        player.setComponentSprite(l.COMBAT_TAB, l.BOX_BUTTON, if (on) l.SPRITE_BOX_SELECTED else l.SPRITE_BOX)
        player.setComponentSprite(l.COMBAT_TAB, l.BOX_ICON, spell?.uniqueId ?: -1)
        player.setComponentHidden(l.COMBAT_TAB, l.BOX_SHIELD, !(on && mode == Mode.DEFENSIVE))
        player.setComponentText(l.COMBAT_TAB, l.BOX_LABEL, if (on && mode == Mode.DEFENSIVE) "Defensive" else "Spell")
    }

    /** Opens the selection panel in the combat tab, listing only what the wielded weapon may autocast in the open spellbook. */
    fun openSelection(player: Player, mode: Mode) {
        val l = AutocastInterfaceLayout
        val def = weaponDef(player)
        if (!AutocastWeapons.isAutocastWeapon(def)) {
            player.message(if (AutocastWeapons.isPoweredStaff(def?.id)) PoweredStaves.NO_AUTOCAST_MESSAGE else "You need a magic weapon to autocast spells.")
            return
        }
        val book = player.getSpellbook()
        val entries = if (book == Spellbook.ANCIENT) l.ANCIENT else if (book == Spellbook.STANDARD) l.STANDARD else emptyList()
        if (entries.none { entry -> spellFor(entry)?.let { AutocastWeapons.incompatibility(def, it) == null } == true }) {
            player.message("You can't autocast any spells from your current spellbook with this weapon.")
            return
        }
        player.attr[PENDING_MODE] = mode.ordinal
        player.openInterface(l.SELECT_INTERFACE, InterfaceDestination.ATTACK_TAB)
        player.setComponentText(l.SELECT_INTERFACE, l.SELECT_TITLE, if (mode == Mode.DEFENSIVE) "Choose a defensive spell" else "Choose a spell")
        player.setComponentHidden(l.SELECT_INTERFACE, l.SELECT_STANDARD_LAYER, book != Spellbook.STANDARD)
        player.setComponentHidden(l.SELECT_INTERFACE, l.SELECT_ANCIENT_LAYER, book != Spellbook.ANCIENT)
        l.ALL.forEach { entry ->
            val component = l.componentOf(entry)
            val allowed = entry in entries && spellFor(entry)?.let { AutocastWeapons.incompatibility(def, it) == null } == true
            player.setComponentHidden(l.SELECT_INTERFACE, component, !allowed)
            if (allowed) player.setInterfaceEvents(l.SELECT_INTERFACE, component, -1..-1, OP1)
        }
        player.setInterfaceEvents(l.SELECT_INTERFACE, l.SELECT_CANCEL, -1..-1, OP1)
        trace(player, "open selection mode=$mode book=$book")
    }

    /** A click in the selection panel. Everything is re-validated here: the client's component id proves nothing. */
    fun onSelectionClick(player: Player, component: Int) {
        val l = AutocastInterfaceLayout
        if (player.getInterfaceAt(InterfaceDestination.ATTACK_TAB) != l.SELECT_INTERFACE) return
        val mode = Mode.values().getOrNull(player.attr[PENDING_MODE] ?: 0)?.takeIf { it != Mode.OFF }
        if (component == l.SELECT_CANCEL || mode == null) {
            closeSelection(player)
            return
        }
        val spell = l.entryAt(component)?.let(::spellFor)
        if (spell == null || !isSelectionValid(player, spell)) {
            trace(player, "refused selection component=$component spell=${spell?.name} reason=${Reason.INVALID_REQUEST}")
            AutocastWeapons.incompatibility(weaponDef(player), spell ?: return closeSelection(player))?.let(player::message)
            closeSelection(player)
            return
        }
        closeSelection(player)
        select(player, spell, mode)
    }

    fun closeSelection(player: Player) {
        player.attr.remove(PENDING_MODE)
        if (player.getInterfaceAt(InterfaceDestination.ATTACK_TAB) == AutocastInterfaceLayout.SELECT_INTERFACE) {
            player.openInterface(AutocastInterfaceLayout.COMBAT_TAB, InterfaceDestination.ATTACK_TAB)
        }
    }

    fun spellFor(entry: AutocastInterfaceLayout.Entry): CombatSpell? =
        CombatSpell.forComponent(entry.book, entry.bookComponent)?.takeIf { it.autoCastId > 0 }

    private val logger = org.apache.logging.log4j.LogManager.getLogger("Autocast")
    private const val OP1 = 2
    private const val OP2 = 4

    private fun trace(player: Player, text: String) {
        if (AutocastPolicy.debug) logger.info("[autocast] {}: {}", player.username, text)
    }
}

/**
 * Deadman / PvP autocast policy in one place. This server is a Deadman world: every dangerous tile ([AreaState.isDangerous]) is a PvP
 * area, so the OSRS PvP-area swap rule (25 March 2026, 20 ticks) applies there. OSRS publishes no separate Permanent Deadman or
 * Annihilation value, so both default to the same 20 ticks; each is its own setting so a ruleset can differ without touching combat code.
 */
object AutocastPolicy {
    enum class Ruleset { PERMANENT_DEADMAN, ANNIHILATION, OSRS_PVP }

    var ruleset: Ruleset = Ruleset.PERMANENT_DEADMAN
    var osrsPvpSwapWindowTicks: Int = 20
    var permanentDeadmanSwapWindowTicks: Int = 20
    var annihilationSwapWindowTicks: Int = 20

    /** Temporary QA instrumentation: log every selection, reset and refusal with its reason. */
    var debug: Boolean = false

    fun pvpSwapWindowTicks(): Int =
        when (ruleset) {
            Ruleset.PERMANENT_DEADMAN -> permanentDeadmanSwapWindowTicks
            Ruleset.ANNIHILATION -> annihilationSwapWindowTicks
            Ruleset.OSRS_PVP -> osrsPvpSwapWindowTicks
        }
}
