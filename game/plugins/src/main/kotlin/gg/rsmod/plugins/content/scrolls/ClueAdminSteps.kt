package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.ItemAttribute

/** ADMIN client-verification helper requested by Claude17-Nov. Uses the installed pool only. */
object ClueAdminSteps {
    fun tier(text: String): ClueScrollTier? = if(text.equals("78",true))ClueScrollTier.MASTER
        else ClueScrollTier.values().singleOrNull { it.key.equals(text,true) || it.name.equals(text,true) }
    fun label(step: ClueStep): String = when(step) {
        is TreasureTrailSourceStep -> step.source.key
        is TreasureTrailThreePartStep -> "Three-part cryptic clue"
        else -> step.hint
    }
    fun matching(rows: List<ClueStep>,query: String): List<ClueStep> {
        query.toIntOrNull()?.let { id -> return rows.filter { it.persistenceId==id } }
        return rows.filter { label(it).contains(query,true) }
    }
    fun assign(player: Player,tier: ClueScrollTier,key: Int): Boolean {
        val step=ClueScrollManager.registeredSteps(tier).singleOrNull { it.persistenceId==key } ?: return false
        val scroll=ClueOwnership.carried(player,tier) ?: return false
        if(player.isDead() || scroll.id!=tier.scrollId ||
            player.inventory.count { it!=null && ClueOwnership.tier(it.id)==tier }!=1 ||
            scroll.getAttr(ItemAttribute.CLUE_TASK_KEY)!=null || TreasureTrailGuardians.session(player)!=null ||
            ClueOwnership.stored(player).any { TreasureTrailPuzzleBoxes.tier(it.id)==tier })return false
        TreasureTrailEmotes.remove(player)
        // A fresh normal-length trail; no completed step, solved puzzle, reward or teleport is granted.
        scroll.removeAttr(ItemAttribute.CLUE_STEP_KEY)
            .removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
            .removeAttr(ItemAttribute.CLUE_GUARD_KEY).removeAttr(ItemAttribute.CLUE_GUARD_PROGRESS)
        ClueScrollManager.prepare(player,scroll,tier)
        scroll.putAttr(ItemAttribute.CLUE_STEP_KEY,step.persistenceId)
        player.inventory.dirty=true
        return true
    }
}
