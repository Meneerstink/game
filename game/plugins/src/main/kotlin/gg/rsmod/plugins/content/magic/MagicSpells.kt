package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MagicSpells {
    const val INF_RUNES_VARBIT = 4145

    private val STAFF_ITEMS =
        arrayOf(
            Items.IBANS_STAFF,
            Items.SLAYERS_STAFF,
            Items.SARADOMIN_STAFF,
            Items.GUTHIX_STAFF,
            Items.ZAMORAK_STAFF,
        )

    private val metadata = Int2ObjectOpenHashMap<SpellMetadata>()

    fun getMetadata(spellId: Int): SpellMetadata? = metadata[spellId]

    fun getCombatSpells(): Map<Int, SpellMetadata> =
        metadata.filter { it.value.spellType == SpellType.COMBAT_SPELL_TYPE }

    private fun usingStaff(
        p: Player,
        rune: Int,
    ): Boolean {
        // A charged Tome of Fire / Water is an infinite source of its rune while worn (Tomes).
        if (gg.rsmod.plugins.content.items.osrs.Tomes.suppliesRune(p, rune)) return true
        val weapon: Item = p.equipment[3] ?: return false
        val staff: MagicStaves = MagicStaves.values().firstOrNull { rune == it.runeId } ?: return false
        staff.staves.forEach {
            if (weapon.id == it) {
                return true
            }
        }
        return false
    }

    fun canCast(
        p: Player,
        lvl: Int,
        items: List<Item>,
        /** The spell's unique id; lets a Blighted sack replace the runes (`BlightedSacks`). -1 = no sack applies. */
        spellId: Int = -1,
    ): Boolean {
        if (p.skills.getCurrentLevel(Skills.MAGIC) < lvl) {
            p.message("Your Magic level is not high enough for this spell.")
            p.setVarp(Combat.SELECTED_AUTOCAST_VARP, 0)
            p.attr.remove(Combat.CASTING_SPELL)
            return false
        }
        if (p.getVarbit(INF_RUNES_VARBIT) == 0 && !gg.rsmod.plugins.content.items.osrs.BlightedSacks.usable(p, spellId)) {
            for (item in items) {
                if (usingStaff(p, item.id)) {
                    continue
                }
                if (p.inventory.getItemCount(item.id) < item.amount &&
                    p.equipment.getItemCount(item.id) < item.amount
                ) {
                    p.message(
                        "You do not have enough ${item.getDef(
                            p.world.definitions,
                        ).name.lowercase()}s to cast this spell.",
                    )
                    p.setVarp(Combat.SELECTED_AUTOCAST_VARP, 0)
                    p.attr.remove(Combat.CASTING_SPELL)
                    return false
                }
            }
        }
        return true
    }

    fun removeRunes(
        p: Player,
        items: List<Item>,
        spellId: Int,
    ) {
        if (p.getVarbit(INF_RUNES_VARBIT) == 0) {
            // A usable Blighted sack is used up instead of the runes ("It is consumed upon cast").
            if (!gg.rsmod.plugins.content.items.osrs.BlightedSacks.consume(p, spellId)) {
                for (item in items) {
                    /*
                     * Do not remove staff item requirements.
                     */
                    if (item.id in STAFF_ITEMS) {
                        continue
                    }
                    if (usingStaff(p, item.id)) {
                        continue
                    }
                    p.inventory.remove(item)
                }
            }

            // Play the sound associated with the spell
            val spellMetadata = getMetadata(spellId)
            if (spellMetadata != null) {
                p.playSound(spellMetadata.sound)
            }
        }
        SpellbookSwap.onSpellCast(p, spellId)
    }

    fun isLoaded(): Boolean = metadata.isNotEmpty()

    fun loadSpellRequirements() {
        for (spell in SpellbookData.values()) {
            val spellMetadata =
                SpellMetadata(
                    interfaceId = spell.interfaceId,
                    component = spell.component,
                    sprite = spell.uniqueId,
                    spellType = spell.spellType,
                    name = spell.spellName,
                    lvl = spell.level,
                    runes = spell.runes,
                    sound = spell.sound, // Load the sound ID
                    hitSound = spell.hitSound, // load hitsound
                )
            metadata[spellMetadata.sprite] = spellMetadata
        }
    }

    fun KotlinPlugin.on_magic_spell_button(
        name: String,
        plugin: Plugin.(SpellMetadata) -> Unit,
    ) {
        if (!isLoaded()) {
            loadSpellRequirements()
        }

        // Prefer the standard book when a name exists in several books (e.g. "Trollheim Teleport").
        val spell = metadata.values.filter { it.name == name }.minByOrNull { it.interfaceId }
            ?: error("No spell named '$name'")

        on_button(spell.interfaceId, spell.component) {
            plugin(this, spell)
        }
    }
}
