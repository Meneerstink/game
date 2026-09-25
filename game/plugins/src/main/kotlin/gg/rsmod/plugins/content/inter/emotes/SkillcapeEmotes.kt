package gg.rsmod.plugins.content.inter.emotes

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.items.osrs.MaxCapes
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.Skillcapes

/**
 * The Skill Cape emote (owner 2026-09-24, max capes like OSRS). It used to play nothing at all (Emote.SKILLCAPE had no animation and its
 * unlock varbit was never set).
 * - Skillcapes (trimmed or not) and the Quest point cape: their own emote - Void `emotes.anims.toml` / `emotes.gfx.toml` (the revision
 *   634/667 skillcape emotes, the same 2007 sequences OSRS plays: attack 4959 / 823, ... quest point 4945 / 816).
 * - Every max cape, plain or variant: OSRS Wiki "Max cape" - the max cape emote, which "can be performed" with every variant too -
 *   OSRS MAX_CAPE_PLAYER_ANIM 7121 with spotanim MAX_CAPE 1286 (imported, batch maxcapeemote).
 * ADAPTED: the Dungeoneering cape (no OSRS counterpart) plays the start of its Void emote only; the rest transforms the player into
 * npcs, which this engine cannot draw.
 */
object SkillcapeEmotes {
    class Look(val anim: Int, val gfx: Int)

    val BY_SKILL: Map<Skillcapes, Look> =
        mapOf(
            Skillcapes.ATTACK to Look(4959, 823), Skillcapes.DEFENCE to Look(4961, 824), Skillcapes.STRENGTH to Look(4981, 828),
            Skillcapes.CONSTITUTION to Look(14242, 2745), Skillcapes.RANGED to Look(4973, 832), Skillcapes.PRAYER to Look(4979, 829),
            Skillcapes.MAGIC to Look(4939, 813), Skillcapes.COOKING to Look(4955, 821), Skillcapes.WOODCUTTING to Look(4957, 822),
            Skillcapes.FLETCHING to Look(4937, 812), Skillcapes.FISHING to Look(4951, 819), Skillcapes.FIREMAKING to Look(4975, 831),
            Skillcapes.CRAFTING to Look(4949, 818), Skillcapes.SMITHING to Look(4943, 815), Skillcapes.MINING to Look(4941, 814),
            Skillcapes.HERBLORE to Look(4969, 835), Skillcapes.AGILITY to Look(4977, 830), Skillcapes.THIEVING to Look(4965, 826),
            Skillcapes.SLAYER to Look(4967, 1656), Skillcapes.FARMING to Look(4963, 825), Skillcapes.RUNECRAFTING to Look(4947, 817),
            Skillcapes.HUNTER to Look(5158, 907), Skillcapes.CONSTRUCTION to Look(4953, 820), Skillcapes.SUMMONING to Look(8525, 1515),
            Skillcapes.DUNGEONEERING to Look(13190, 2442),
        )

    val QUEST_POINT = Look(4945, 816)

    /** OSRS max cape emote: MAX_CAPE_PLAYER_ANIM 7121 (local 15819) and spotanim MAX_CAPE 1286 (local 3172). */
    val MAX_CAPE = Look(15819, 3172)

    private val byCape: Map<Int, Look> by lazy {
        val map = HashMap<Int, Look>()
        BY_SKILL.forEach { (cape, look) ->
            map[cape.untrimmedCape] = look
            map[cape.trimmedCape] = look
        }
        map[Items.QUEST_POINT_CAPE] = QUEST_POINT
        map[Items.QUEST_POINT_CAPE_10662] = QUEST_POINT
        map[MaxCapes.MAX_CAPE] = MAX_CAPE
        MaxCapes.WEARABLE_VARIANTS.forEach { map[it] = MAX_CAPE }
        map
    }

    fun lookFor(capeId: Int?): Look? = capeId?.let { byCape[it] }

    /** Keeps the emote tab's Skill Cape button unlocked exactly while such a cape is worn. */
    fun sync(player: Player) {
        player.setVarbit(EmotesTab.SKILLCAPE_EMOTE_VARBIT, if (lookFor(SkillcapePerks.wornCape(player)) != null) 1 else 0)
    }

    fun perform(player: Player): Boolean {
        val look = lookFor(SkillcapePerks.wornCape(player))
        if (look == null) {
            player.message("You need to be wearing a skillcape in order to perform this emote.")
            return false
        }
        player.animate(look.anim)
        player.graphic(look.gfx)
        return true
    }
}
