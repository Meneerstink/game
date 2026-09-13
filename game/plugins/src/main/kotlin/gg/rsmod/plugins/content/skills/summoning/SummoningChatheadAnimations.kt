package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.model.entity.Player

/**
 * The one cache-authentic animation route shared by Follower Details (662:1) and familiar chat.
 *
 * Void's `follower_details_chathead_animation` map supplies the selector written to varbit 4282.
 * Clientscript 751 then resolves selectors 0..50 through enum 1276 and selectors above 50 through
 * enum 1275 after subtracting 50. DialogueCommon uses that exact same route for familiar chatheads.
 * This is deliberately not an NPC BAS/world-idle lookup: chathead models have their own sequences.
 *
 * Phoenix is absent from Void's map. It therefore resolves to null and is cleared rather than
 * borrowing another familiar's animation. All other selectors below are a direct transcription of
 * `C:\RSPS\Donors\void\data\skill\summoning\summoning.varbits.toml`.
 */
object SummoningChatheadAnimations {
    internal const val SELECTOR_VARBIT = 4282
    internal const val NORMAL_ENUM = 1276
    internal const val SAD_ENUM = 1275

    private val SELECTORS: Map<SummoningPouchData, Int> =
        mapOf(
            SummoningPouchData.SPIRIT_WOLF to 7,
            SummoningPouchData.DREADFOWL to 2,
            SummoningPouchData.SPIRIT_SPIDER to 6,
            SummoningPouchData.THORNY_SNAIL to 12,
            SummoningPouchData.GRANITE_CRAB to 3,
            SummoningPouchData.SPIRIT_MOSQUITO to 8,
            SummoningPouchData.DESERT_WYRM to 0,
            SummoningPouchData.SPIRIT_SCORPION to 6,
            SummoningPouchData.SPIRIT_TZ_KIH to 32,
            SummoningPouchData.ALBINO_RAT to 0,
            SummoningPouchData.SPIRIT_KALPHITE to 8,
            SummoningPouchData.COMPOST_MOUND to 20,
            SummoningPouchData.GIANT_CHINCHOMPA to 0,
            SummoningPouchData.VAMPYRE_BAT to 0,
            SummoningPouchData.HONEY_BADGER to 0,
            SummoningPouchData.BEAVER to 7,
            SummoningPouchData.VOID_RAVAGER to 13,
            SummoningPouchData.VOID_SHIFTER to 0,
            SummoningPouchData.VOID_SPINNER to 79,
            SummoningPouchData.VOID_TORCHER to 2,
            SummoningPouchData.BRONZE_MINOTAUR to 0,
            SummoningPouchData.BULL_ANT to 8,
            SummoningPouchData.MACAW to 2,
            SummoningPouchData.EVIL_TURNIP to 7,
            SummoningPouchData.SPIRIT_COCKATRICE to 16,
            SummoningPouchData.SPIRIT_GUTHATRICE to 16,
            SummoningPouchData.SPIRIT_SARATRICE to 16,
            SummoningPouchData.SPIRIT_ZAMATRICE to 16,
            SummoningPouchData.SPIRIT_PENGATRICE to 16,
            SummoningPouchData.SPIRIT_CORAXATRICE to 16,
            SummoningPouchData.SPIRIT_VULATRICE to 16,
            SummoningPouchData.IRON_MINOTAUR to 0,
            SummoningPouchData.PYRELORD to 23,
            SummoningPouchData.MAGPIE to 2,
            SummoningPouchData.BLOATED_LEECH to 14,
            SummoningPouchData.SPIRIT_TERRORBIRD to 52,
            SummoningPouchData.ABYSSAL_PARASITE to 14,
            SummoningPouchData.SPIRIT_JELLY to 13,
            SummoningPouchData.IBIS to 52,
            SummoningPouchData.STEEL_MINOTAUR to 0,
            SummoningPouchData.SPIRIT_GRAAHK to 0,
            SummoningPouchData.SPIRIT_KYATT to 0,
            SummoningPouchData.SPIRIT_LARUPIA to 0,
            SummoningPouchData.KARAMTHULHU_OVERLORD to 17,
            SummoningPouchData.SMOKE_DEVIL to 22,
            SummoningPouchData.ABYSSAL_LURKER to 2,
            SummoningPouchData.SPIRIT_COBRA to 11,
            SummoningPouchData.STRANGER_PLANT to 21,
            SummoningPouchData.BARKER_TOAD to 1,
            SummoningPouchData.MITHRIL_MINOTAUR to 0,
            SummoningPouchData.WAR_TORTOISE to 0,
            SummoningPouchData.BUNYIP to 4,
            SummoningPouchData.FRUIT_BAT to 0,
            SummoningPouchData.RAVENOUS_LOCUST to 6,
            SummoningPouchData.ARCTIC_BEAR to 0,
            SummoningPouchData.OBSIDIAN_GOLEM to 28,
            SummoningPouchData.GRANITE_LOBSTER to 8,
            SummoningPouchData.PRAYING_MANTIS to 8,
            SummoningPouchData.FORGE_REGENT to 84,
            SummoningPouchData.ADAMANT_MINOTAUR to 0,
            SummoningPouchData.TALON_BEAST to 0,
            SummoningPouchData.GIANT_ENT to 18,
            SummoningPouchData.FIRE_TITAN to 23,
            SummoningPouchData.ICE_TITAN to 26,
            SummoningPouchData.MOSS_TITAN to 24,
            SummoningPouchData.HYDRA to 19,
            SummoningPouchData.SPIRIT_DAGANNOTH to 0,
            SummoningPouchData.LAVA_TITAN to 27,
            SummoningPouchData.SWAMP_TITAN to 0,
            SummoningPouchData.RUNE_MINOTAUR to 0,
            SummoningPouchData.UNICORN_STALLION to 89,
            SummoningPouchData.GEYSER_TITAN to 25,
            SummoningPouchData.WOLPERTINGER to 7,
            SummoningPouchData.ABYSSAL_TITAN to 27,
            SummoningPouchData.IRON_TITAN to 27,
            SummoningPouchData.PACK_YAK to 0,
            SummoningPouchData.STEEL_TITAN to 27,
        )

    fun selector(pouch: SummoningPouchData): Int? = SELECTORS[pouch]

    fun resolve(player: Player, npcId: Int): Int? {
        val pouch = SummoningPouchData.values.firstOrNull { it.npc == npcId } ?: return null
        return resolve(player.world.definitions, pouch)
    }

    internal fun resolve(definitions: DefinitionSet, pouch: SummoningPouchData): Int? {
        val selector = SELECTORS[pouch] ?: return null
        val enumId = if (selector > 50) SAD_ENUM else NORMAL_ENUM
        val key = if (selector > 50) selector - 50 else selector
        return definitions.get(EnumDef::class.java, enumId).getInt(key).takeIf { it != -1 }
    }
}
