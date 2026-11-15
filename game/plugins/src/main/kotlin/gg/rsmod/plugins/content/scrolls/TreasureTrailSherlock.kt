package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.message
import kotlin.random.Random

/** Successful actions only, with the issued challenge physically carried (Wiki Sherlock). */
object TreasureTrailSherlock {
    enum class Action {
        NATURE_RUNE, SUPER_DEFENCE, GREEN_DHIDE_BODY, YEW_LONGBOW, MITHRIL_ORE, SHARK, YEW_LOG,
        RUNE_MED_HELM, NECHRYAEL_TOWER, DRAGONSTONE_AMULET, MAGIC_LOG, ARDOUGNE_GEM_STALL, RUNE_DART,
    }
    data class Assignment(val contract: ClueStepContract, val task: TreasureTrailTaskCatalogue.Sherlock, val first: Boolean) {
        val done get() = contract.scroll.getAttr(ItemAttribute.CLUE_TASK_COMPLETE) == 1
    }
    private val skillIds = mapOf("Runecraft" to Skills.RUNECRAFTING, "Herblore" to Skills.HERBLORE,
        "Crafting" to Skills.CRAFTING, "Fletching" to Skills.FLETCHING, "Mining" to Skills.MINING,
        "Fishing" to Skills.FISHING, "Woodcutting" to Skills.WOODCUTTING, "Smithing" to Skills.SMITHING,
        "Slayer" to Skills.SLAYER, "Firemaking" to Skills.FIREMAKING, "Thieving" to Skills.THIEVING)

    fun current(player: Player, tier: ClueScrollTier): Assignment? {
        val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: return null
        if (step.source.action != TreasureTrailStepCatalogue.Action.SHERLOCK) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        if (tier == ClueScrollTier.ELITE && contract.scroll.id != ClueItems.local(12128)) return null
        val key = contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) ?: return null
        val task = TreasureTrailTaskCatalogue.current.sherlock.singleOrNull { it.tier == tier && it.key.hashCode() == key } ?: return null
        return Assignment(contract, task, false)
    }

    fun begin(player: Player, tier: ClueScrollTier, random: Random = Random.Default): Assignment? {
        current(player, tier)?.let { return it }
        val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: return null
        if (step.source.action != TreasureTrailStepCatalogue.Action.SHERLOCK) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        if (contract.scroll.id != tier.scrollId || contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) != null) {
            println("PROBLEM CLUE corrupt Sherlock task state; challenge retained"); return null
        }
        val tasks = TreasureTrailTaskCatalogue.current.sherlock.filter { it.tier == tier }
        if (tasks.isEmpty()) return null
        val selected = tasks.random(random)
        val item = if (tier == ClueScrollTier.ELITE) {
            val id = ClueItems.local(12128) ?: run { println("PROBLEM CLUE Sherlock challenge 12128 not imported"); return null }
            if (player.world.definitions.getNullable(ItemDef::class.java, id) == null) return null
            Item(id).copyAttr(contract.scroll)
        } else contract.scroll
        item.putAttr(ItemAttribute.CLUE_TASK_KEY, selected.key.hashCode()).removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
        if (item !== contract.scroll) {
            val slot = player.inventory.rawItems.indexOfFirst { it === contract.scroll }
            if (slot < 0 || !contract.valid(player)) return null
            player.inventory[slot] = item
        }
        player.inventory.dirty = true
        return current(player, tier)?.copy(first = true)
    }

    fun succeeded(player: Player, action: Action): Int {
        var marked = 0
        for (tier in listOf(ClueScrollTier.ELITE, ClueScrollTier.MASTER)) {
            val assignment = current(player, tier) ?: continue
            if (assignment.task.key != action.name || assignment.done || !assignment.contract.valid(player)) continue
            if (assignment.task.requirements.any { (name, level) -> player.skills.getCurrentLevel(skillIds.getValue(name)) < level }) continue
            assignment.contract.scroll.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1)
            player.inventory.dirty = true
            player.message(completedText(tier)); marked++
        }
        return marked
    }

    fun finish(player: Player, assignment: Assignment): Boolean = assignment.done && assignment.contract.valid(player) &&
        assignment.contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) == assignment.task.key.hashCode() && assignment.contract.advance(player)

    /** Wiki change 8-Dec-2016: an incomplete other-tier task must not hide a completed task. */
    fun forDialogue(player: Player): Assignment? {
        val tiers = listOf(ClueScrollTier.ELITE, ClueScrollTier.MASTER)
        val existing = tiers.mapNotNull { current(player, it) }
        return existing.firstOrNull { it.done } ?: existing.firstOrNull() ?: tiers.firstNotNullOfOrNull { begin(player, it) }
    }

    fun completedText(tier: ClueScrollTier): String =
        "<col=ff0000>You have completed your ${if (tier == ClueScrollTier.MASTER) "78" else "elite"} level challenge!</col>"

    fun read(player: Player, step: ClueStep): Boolean {
        if (step !is TreasureTrailSourceStep || step.source.action != TreasureTrailStepCatalogue.Action.SHERLOCK) return false
        val assignment = current(player, step.tier) ?: return false
        ClueInterfaces.text(player, assignment.task.text)
        return true
    }

    fun check(player: Player) {
        val assignment = current(player, ClueScrollTier.ELITE) ?: return
        if (assignment.done) player.message(completedText(ClueScrollTier.ELITE)) else ClueInterfaces.text(player, assignment.task.text)
    }

    /** Regions are derived from the pinned Wiki Nechryael top-floor/basement map pins. */
    fun tower(tile: Tile): Boolean = tile.regionId == 13623 && tile.height == 2 || tile.regionId == 13723 && tile.height == 0
    fun killed(player: Player, npc: Npc) {
        val def = npc.world.definitions.get(NpcDef::class.java, npc.id)
        if (def.name.equals("Nechryael", true) && def.combatLevel == 115 && tower(npc.tile)) succeeded(player, Action.NECHRYAEL_TOWER)
    }
    /** Source Gem stall's Ardougne pin 2668,3303, distinct from its Keldagrim/Kourend variants. */
    fun ardougneGem(tile: Tile): Boolean = tile.regionId == 10547 && tile.height == 0
}
