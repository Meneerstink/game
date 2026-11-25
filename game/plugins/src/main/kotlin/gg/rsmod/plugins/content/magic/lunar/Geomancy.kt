package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.quests.tab.N108Ui
import gg.rsmod.plugins.content.skills.farming.data.*
import gg.rsmod.plugins.content.skills.farming.logic.PatchState

/** Wiki Geomancy; OSRS2686 farming_view179, human7118/spot1285/synth2891.
 * Reads the existing owner's PatchState/compost varbits. Does not grow, cure or protect a patch.
 * Pending local AV ids come from paired OsrsFxImportTool n72c-geomancy dry-run; Claude applies
 * that exact batch before activating the spell cache. Never substitute another native667 seq.
 */
object Geomancy {
    const val INTERFACE = 1252
    const val ANIMATION = 17929 // OSRS7118, n72c-geomancy (not the broader n72c-processing batch).
    const val GRAPHIC = 3524 // OSRS1285
    const val SOUND = 11100 // OSRS2891
    private val CAST = TimerKey()
    private val TAB = AttributeKey<Int>()
    private val SENT = AttributeKey<List<String>>()
    private val HIDDEN = AttributeKey<Int>()
    private val GROUPS = listOf(setOf(SeedType.Allotment, SeedType.Flower), setOf(SeedType.Herb), setOf(SeedType.Hops),
        setOf(SeedType.Bush), setOf(SeedType.Tree, SeedType.FruitTree), setOf(SeedType.Calquat))

    data class View(val crop: String, val status: String, val growth: String, val protectedFromDisease: Boolean,
                    val compost: String, val watered: Boolean, val yield: Int, val replenishes: Boolean) {
        fun lines() = listOf("Crop: $crop", status, "Growth: $growth", "Protected: ${if (protectedFromDisease) "Yes" else "No"}",
            "Compost: $compost", "Watered: ${if (watered) "Yes" else "No"}", "${if (replenishes) "Available" else "Minimum"} yield: $yield")
    }

    fun view(state: PatchState): View {
        val seed = state.seed
        val growing = state.growthStage
        return View(seed?.name?.replace(Regex("([a-z])([A-Z])"), "$1 $2") ?: "None",
            when { state.isDead -> "Dead"; state.isDiseased -> "Diseased"; state.isPlantFullyGrown -> "Fully grown"
                state.isWeedy -> "Weeds"; state.isEmpty -> "Empty"; else -> "Growing" },
            if (seed == null || state.isDead) "-" else "${growing ?: seed.growth.growthStages}/${seed.growth.growthStages}",
            state.isProtected, state.compostState.name.replace("SuperCompost", "Supercompost"), state.isWatered,
            if (seed == null || state.isDead) 0 else state.livesLeft.coerceAtLeast(0) * seed.produce.amount,
            seed?.seedType?.harvest?.livesReplenish == true)
    }

    fun cards(player: Player, tab: Int): List<String> {
        val types = GROUPS.getOrNull(tab) ?: return emptyList()
        val cards = Patch.values().filter { p -> p.seedTypes.any { it in types } }.map { patch ->
            val name = patch.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            "<col=ff981f>$name</col><br>" + view(player.farmingManager().getPatchManager(patch).state).lines().joinToString("<br>")
        }.toMutableList()
        if (tab == 5) for (bin in CompostBin.values()) {
            val raw = player.getVarbit(bin.varbit)
            val state = CompostBinState.values().firstOrNull { raw in it.varbits }
            cards += "<col=ff981f>${bin.name} compost bin</col><br>" +
                (state?.name?.replace(Regex("([a-z])([A-Z])"), "$1 $2") ?: "Unknown state: $raw")
        }
        return cards
    }

    fun cast(player: Player): Boolean {
        if (player.isInterfaceVisible(INTERFACE)) return false // opening the same view again is not another cast/XP drop.
        if (player.timers.has(CAST) || player.isDead() || !player.lock.canItemInteract()) return false
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(player,
                gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.UTILITY_SPELL)) return false
        if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
        val data = MagicSpells.getMetadata(SpellbookData.GEOMANCY.uniqueId) ?: return false
        if (!MagicSpells.canCast(player, data.lvl, data.runes, data.sprite)) return false
        player.timers[CAST] = 1
        MagicSpells.removeRunes(player, data.runes, data.sprite)
        player.addXp(Skills.MAGIC, 60.0, checkBrawlingGloves = true)
        player.animate(ANIMATION); player.graphic(GRAPHIC); player.playSound(SOUND)
        player.attr[TAB] = 0
        player.attr.remove(SENT); player.attr.remove(HIDDEN)
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        (listOf(2) + (5..10)).forEach { player.setInterfaceEvents(INTERFACE, it, -1..-1, 2) }
        refresh(player)
        return true
    }

    fun select(player: Player, tab: Int) {
        if (!player.isInterfaceVisible(INTERFACE) || tab !in GROUPS.indices) return
        player.attr[TAB] = tab
        player.attr.remove(SENT)
        N108Ui.scrollTop(player, INTERFACE, 3, 4, 260)
        refresh(player)
    }

    fun refresh(player: Player) {
        if (!player.isInterfaceVisible(INTERFACE)) return
        val cards = cards(player, player.attr[TAB] ?: 0)
        if (cards == player.attr[SENT]) return
        check(cards.size <= 40)
        if (cards.size != player.attr[HIDDEN]) {
            repeat(40) { i -> player.setComponentHidden(INTERFACE, 11 + i * 3, i >= cards.size) }
            N108Ui.scroll(player, INTERFACE, 3, 4, maxOf(260, ((cards.size + 2) / 3) * 125))
            player.attr[HIDDEN] = cards.size
        }
        cards.forEachIndexed { i, text -> N108Ui.text(player, INTERFACE, 12 + i * 3, text) }
        player.attr[SENT] = cards
    }
}
