package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarcString

/**
 * Server side of the "Items Kept on Death" screen (interface 17).
 *
 * The screen is built entirely by the client: component 17:1's onLoad (client script 1536) binds
 * script 4592, which reads inventory 93, worn equipment 94 and beast-of-burden 530 directly, ranks
 * the stacks itself and highlights the ones that would be kept. The server therefore does not push
 * an item list - it only has to publish the three pieces of state the client cannot derive on its
 * own, all of which script 4592 reads as varbits:
 *
 *  * [WILDERNESS_PREVIEW_VARBIT] (9226) - which of the two panels is showing. `0` titles the screen
 *    "Items kept on death" and uses the "You may choose N of the following items to keep" wording;
 *    non-zero titles it "If you die in the Wilderness..." and switches to the "You will keep the
 *    following items" / "You will drop the following items" wording. Client script 4597 flips the
 *    17:30 button label between "What if I entered the Wilderness?" and "Back" off the same varbit.
 *  * [KEEP_COUNT_VARBIT] (9227) - the number substituted into "You may choose N ...". Script 4592
 *    returns early with "You have no items to lose." when all three containers are empty, and skips
 *    the choose-N wording entirely when this is 0.
 *  * [SKULLED_VARBIT] (9229) - selects between the skulled wording ("and all others will be
 *    dropped") and the unskulled wording ("unless you become skulled").
 *
 * All three varbit ids were decoded from the production cache with
 * `./gradlew :game:runInterfaceHookProbeTool --args="<cache> script 4592"`.
 *
 * The counts themselves come from [DeathItemRiskCalculator], the same pure calculator the real
 * death path uses, so the screen can never disagree with what a death would actually do.
 */
object ItemsKeptOnDeath {
    const val INTERFACE_ID = 17

    /** 17:28, cache op1 "Toggle" (label "What if I entered the Wilderness?" / "Back" set by CS2 4597). */
    const val TOGGLE_COMPONENT = 28

    /** 17:13 "Close". */
    const val CLOSE_COMPONENT = 13

    private const val WILDERNESS_PREVIEW_VARBIT = 9226
    private const val KEEP_COUNT_VARBIT = 9227
    private const val SKULLED_VARBIT = 9229

    /**
     * Publishes the current state and returns the number of stacks the player would keep. Called
     * both when the screen is opened and whenever the preview is toggled, so the two panels always
     * reflect live skull/Protect Item state rather than a snapshot taken when it was opened.
     */
    fun refresh(player: Player): Int {
        val skulled = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(player)
        val protectItem = player.attr[PROTECT_ITEM_ATTR] == true
        val keepCount = DeathItemRiskCalculator.protectedItemCount(skulled, protectItem)
        player.setVarbit(SKULLED_VARBIT, if (skulled) 1 else 0)
        player.setVarbit(KEEP_COUNT_VARBIT, keepCount)
        // Novite PlayerDeathInformation.sendItemsKeptOnDeath publishes this string; CS2
        // 4597 reads it for the right-hand panel. An unset varcstr renders literally "null".
        // Do not copy Novite's gravestone/Edgeville text: this server uses Death's Domain.
        player.setVarcString(352, "The number of items kept on<br>death is normally 3.")
        return keepCount
    }

    fun open(player: Player) {
        player.setVarbit(WILDERNESS_PREVIEW_VARBIT, 0)
        refresh(player)
    }

    /** Switches between the normal panel and the "if you die in the Wilderness" preview. */
    fun toggleWildernessPreview(player: Player) {
        val preview = player.getVarbit(WILDERNESS_PREVIEW_VARBIT) == 0
        player.setVarbit(WILDERNESS_PREVIEW_VARBIT, if (preview) 1 else 0)
        refresh(player)
    }
}
