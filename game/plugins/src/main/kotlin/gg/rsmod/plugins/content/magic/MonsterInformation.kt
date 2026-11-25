package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness
import gg.rsmod.plugins.content.quests.tab.N108Ui

/** OSRS Wiki Monster Inspect/Examine, 2686 interface 522 and scripts 1179/1181.
 * Values are a snapshot of the actual NPC, including drained stats and per-instance weakness.
 * Unsupported OSRS properties are omitted rather than guessed from the NPC's name.
 */
object MonsterInformation {
    /** = SpellbookCompletionCacheTool.MONSTER_INFO_INTERFACE (new interface, OSRS 2686 interface 522). */
    const val INTERFACE = 1250
    private val BUTTONS = intArrayOf(5, 8, 11, 14)
    private val LAYERS = intArrayOf(19, 21, 23, 25)
    private val TEXTS = intArrayOf(20, 22, 24, 26)
    private val TABS = intArrayOf(6, 9, 12, 15)
    private val PAGES = AttributeKey<List<List<String>>>()
    private val CAST = TimerKey()

    fun claim(player: Player): Boolean {
        if (player.timers.has(CAST)) return false
        // Reserve this cycle only. The primary sources specify no cast-speed value for
        // Inspect/Examine; do not invent a longer spell cooldown.
        player.timers[CAST] = 1
        return true
    }

    /** Reads current values, never the base-stat list in combatDef. No NPC state is changed. */
    fun pages(npc: Npc, examine: Boolean): List<List<String>> {
        val stats = listOf(
            "Combat level: ${npc.def.combatLevel}", "Life points: ${npc.getCurrentLifepoints()}",
            "Attack: ${npc.stats.getCurrentLevel(NpcSkills.ATTACK)}",
            "Defence: ${npc.stats.getCurrentLevel(NpcSkills.DEFENCE)}",
            "Strength: ${npc.stats.getCurrentLevel(NpcSkills.STRENGTH)}",
            "Magic: ${npc.stats.getCurrentLevel(NpcSkills.MAGIC)}",
            "Ranged: ${npc.stats.getCurrentLevel(NpcSkills.RANGED)}",
        ).toMutableList()
        if (examine) stats += "Main attack style: ${npc.combatDef.attackStyleType.name.lowercase().replaceFirstChar { it.uppercase() }}"
        val aggressive = mutableListOf("Attack speed: ${npc.combatDef.attackSpeed} ticks")
        fun bonus(label: String, value: Int) { if (value != 0) aggressive += "$label: $value" }
        bonus("Attack bonus", npc.getAttackBonus())
        if (examine) {
            bonus("Stab bonus", npc.getBonus(BonusSlot.ATTACK_STAB))
            bonus("Slash bonus", npc.getBonus(BonusSlot.ATTACK_SLASH))
            bonus("Crush bonus", npc.getBonus(BonusSlot.ATTACK_CRUSH))
        }
        bonus("Ranged bonus", npc.getBonus(BonusSlot.ATTACK_RANGED))
        bonus("Magic bonus", npc.getBonus(BonusSlot.ATTACK_MAGIC))
        bonus("Strength bonus", npc.getStrengthBonus())
        bonus("Ranged strength bonus", npc.getRangedStrengthBonus())
        bonus("Magic strength bonus", npc.getMagicDamageBonus())
        val defensive = listOf(
            "Stab bonus" to BonusSlot.DEFENCE_STAB, "Slash bonus" to BonusSlot.DEFENCE_SLASH,
            "Crush bonus" to BonusSlot.DEFENCE_CRUSH, "Magic bonus" to BonusSlot.DEFENCE_MAGIC,
            // This server's ranged formula uses one shared defence bonus for the three projectile weights.
            "Light ranged bonus" to BonusSlot.DEFENCE_RANGED, "Standard ranged bonus" to BonusSlot.DEFENCE_RANGED,
            "Heavy ranged bonus" to BonusSlot.DEFENCE_RANGED,
        ).map { (label, slot) -> "$label: ${npc.getBonus(slot)}" }
        val other = mutableListOf<String>()
        if (npc.combatDef.slayerAssignment != null) other += "Can be killed for a Slayer task."
        other += if (npc.combatDef.poisonImmunity) "Immune to poison." else "Can be poisoned."
        if (npc.combatDef.venomImmunity) other += "Immune to venom."
        if (npc.isSpecies(NpcSpecies.DEMON)) other += "Demonic."
        if (npc.isSpecies(NpcSpecies.DRAGON, NpcSpecies.BASIC_DRAGON, NpcSpecies.BRUTAL_DRAGON, NpcSpecies.METAL_DRAGON)) other += "Draconic."
        if (npc.isSpecies(NpcSpecies.UNDEAD)) other += "Undead."
        val weakness = if (npc.attr[ElementalWeakness.INSTANCE_NONE] == true) null
            else npc.attr[ElementalWeakness.INSTANCE] ?: ElementalWeakness.of(npc.id, npc.def.name)
        weakness?.let { other += "${it.element.name.lowercase().replaceFirstChar { c -> c.uppercase() }} weakness: ${it.percent}%" }
        return listOf(stats, aggressive, defensive, other)
    }

    fun open(player: Player, npc: Npc, examine: Boolean) {
        player.attr[PAGES] = pages(npc, examine)
        player.openInterface(INTERFACE, InterfaceDestination.INVENTORY_TAB)
        player.focusTab(Tabs.INVENTORY)
        player.setComponentText(INTERFACE, 3, npc.def.name.replace(Regex("<[^>]*>"), ""))
        (BUTTONS.toList() + 17).forEach { player.setInterfaceEvents(INTERFACE, it, -1..-1, 2) }
        TEXTS.forEachIndexed { tab, text -> player.setComponentText(INTERFACE, text, player.attr[PAGES]!![tab].joinToString("<br>")) }
        select(player, BUTTONS[0])
    }

    fun select(player: Player, component: Int) {
        if (!player.isInterfaceVisible(INTERFACE)) return
        val pages = player.attr[PAGES] ?: return
        val tab = BUTTONS.indexOf(component).takeIf { it >= 0 } ?: return
        LAYERS.forEachIndexed { i, layer -> player.setComponentHidden(INTERFACE, layer, i != tab) }
        TABS.forEachIndexed { i, graphic ->
            player.setComponentSprite(INTERFACE, graphic, if (i == tab) 9246 else 9245)
        }
        // 17px source line height; reserve enough space for wrapping the longest attributes.
        N108Ui.scrollTop(player, INTERFACE, LAYERS[tab], 27, 1)
        N108Ui.scroll(player, INTERFACE, LAYERS[tab], 27, maxOf(190, pages[tab].sumOf { 17 * ((it.length + 20) / 21).coerceAtLeast(1) } + 10))
    }

    fun close(player: Player) {
        player.attr.remove(PAGES)
        player.openInterface(679, InterfaceDestination.INVENTORY_TAB)
    }

    fun onClosed(player: Player) { player.attr.remove(PAGES) }
}
