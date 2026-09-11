package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.content.inter.emotes.Emote

/**
 * Real Treasure Trail step data, ported from Novite's `player/content/scrolls/impl` classes
 * (41 real step implementations found there). Per this project's own prior batch-46 finding,
 * Novite's [ScrollSystem] pools every step across all four tiers regardless of the scroll's real
 * tier (`getRandomScroll(ScrollType type)` ignores its own `type` parameter) - there is no
 * per-tier step classification anywhere in this donor. That real (if quirky) donor behavior is
 * preserved bug-for-bug here rather than inventing a tier split: every location below is
 * registered under all four [ClueScrollTier]s by `treasure_trail_steps.plugin.kts`.
 *
 * Every tile's region and every map-clue interface id was independently verified against this
 * project's real 667 cache this batch (`runRev667RegionProbeTool ... roundtrip` for all 40
 * distinct regions across the 41 tiles, `runInterfaceHookProbeTool` for all 24 map interface
 * ids) - all confirmed present, none guessed. Every emote-clue equipment item id was verified
 * present in this project's own generated `Items.kt`.
 */

/**
 * A classic "dig at the X" map clue: reading the scroll shows [interfaceId] (the real map
 * graphic), and digging with a spade on [tile] completes the step.
 */
data class MapClueLocation(
    val tile: Tile,
    val interfaceId: Int,
)

/**
 * A classic emote clue: reading the scroll shows [hints] as plain text, and performing [emote]
 * while standing on [tile] wearing every item in [requiredEquipment] completes the step.
 */
data class EmoteClueLocation(
    val tile: Tile,
    val emote: Emote,
    val hints: List<String>,
    val requiredEquipment: Map<EquipmentType, Int>,
)

class MapClueStep(
    override val tier: ClueScrollTier,
    private val location: MapClueLocation,
) : ClueStep {
    override val hint: String = "You should dig at the location shown on the map."
    override val mapInterfaceId: Int = location.interfaceId

    override fun onAttempt(player: Player): Boolean = player.tile == location.tile
}

class EmoteClueStep(
    override val tier: ClueScrollTier,
    private val location: EmoteClueLocation,
) : ClueStep {
    override val hint: String = location.hints.joinToString(" ")

    fun matches(
        player: Player,
        emoteComponent: Int,
    ): Boolean {
        if (emoteComponent != location.emote.component) return false
        if (player.tile != location.tile) return false
        return location.requiredEquipment.all { (slot, itemId) -> player.equipment[slot.id]?.id == itemId }
    }

    /**
     * Not used directly - emote clues are checked via [matches] from the emote-tab hook, since
     * [ClueStep.onAttempt] has no way to receive which emote was just performed. Present only to
     * satisfy the [ClueStep] contract.
     */
    override fun onAttempt(player: Player): Boolean = false
}

/**
 * The 24 real map-clue (dig) locations found in Novite's `scrolls/impl` package. Tile coordinates
 * and interface ids are the donor's own values - every one independently verified against this
 * project's real cache this batch (see class doc above).
 */
val MAP_CLUE_LOCATIONS: List<MapClueLocation> =
    listOf(
        MapClueLocation(Tile(2970, 3421, 0), 337), // Falador statue
        MapClueLocation(Tile(3022, 3912, 0), 338), // Wilderness altar
        MapClueLocation(Tile(2732, 3337, 0), 339), // Legends' Guild
        MapClueLocation(Tile(2535, 3866, 0), 340), // Miscellania
        MapClueLocation(Tile(3433, 3265, 0), 341), // Mort'ton
        MapClueLocation(Tile(2455, 3230, 0), 342), // Chaos altar
        MapClueLocation(Tile(2578, 3598, 0), 343), // Lighthouse island
        MapClueLocation(Tile(2666, 3562, 0), 344), // Sinclair path
        MapClueLocation(Tile(3166, 3360, 0), 346), // Champions' Guild
        MapClueLocation(Tile(3289, 3372, 0), 347), // Varrock mine
        MapClueLocation(Tile(3092, 3227, 0), 348), // Draynor fishing spot
        MapClueLocation(Tile(2702, 3429, 0), 349), // Ranging Guild
        MapClueLocation(Tile(3309, 3504, 0), 350), // Lumber Yard
        MapClueLocation(Tile(3043, 3398, 0), 351), // North of Falador tree
        MapClueLocation(Tile(2906, 3294, 0), 352), // Crafting Guild peninsula
        MapClueLocation(Tile(2616, 3077, 0), 353), // Yanille smithing shop
        MapClueLocation(Tile(2612, 3482, 0), 354), // Sir Galahad
        MapClueLocation(Tile(2658, 3487, 0), 355), // McGrubor's Woods
        MapClueLocation(Tile(2488, 3308, 0), 357), // West Ardougne
        MapClueLocation(Tile(2457, 3183, 0), 358), // Novite's "NorthCWScroll" (exact location name not confirmed)
        MapClueLocation(Tile(3026, 3629, 0), 359), // Wilderness fortress
        MapClueLocation(Tile(2650, 3231, 0), 360), // Tower of Life
        MapClueLocation(Tile(2565, 3249, 0), 361), // Clocktower
        MapClueLocation(Tile(2923, 3209, 0), 362), // Rimmington
    )

/**
 * The 17 real emote-clue locations found in Novite's `scrolls/impl` package. Animation/component
 * ids matched exactly against this project's own [Emote] enum (independently confirmed the same
 * numbers this batch, e.g. Novite's `HEADBANG=17`/`SHRUG=8`/`CHEER=9` line up 1:1 with
 * `Emote.HEADBANG`/`Emote.SHRUG`/`Emote.CHEER`'s `component` values). Equipment item ids verified
 * present in this project's own `Items.kt`.
 */
val EMOTE_CLUE_LOCATIONS: List<EmoteClueLocation> =
    listOf(
        EmoteClueLocation(
            Tile(3298, 3287, 0),
            Emote.HEADBANG,
            listOf("Headbang in the mine north of Al Kharid.", "Equip a black d'hide body, leather gloves, and leather boots."),
            mapOf(
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.BLACK_DHIDE_BODY,
                EquipmentType.GLOVES to gg.rsmod.plugins.api.cfg.Items.LEATHER_GLOVES,
                EquipmentType.BOOTS to gg.rsmod.plugins.api.cfg.Items.LEATHER_BOOTS,
            ),
        ),
        EmoteClueLocation(
            Tile(2917, 3166, 0),
            Emote.SALUTE,
            listOf("Salute in the banana plantation. Beware of double agents!", "Equip a diamond ring, amulet of power, and nothing on your chest and legs."),
            mapOf(
                EquipmentType.RING to gg.rsmod.plugins.api.cfg.Items.DIAMOND_RING,
                EquipmentType.AMULET to gg.rsmod.plugins.api.cfg.Items.AMULET_OF_POWER,
            ),
        ),
        EmoteClueLocation(
            Tile(2547, 3554, 0),
            Emote.CHEER,
            listOf("Cheer in the Barbarian Agility Arena.", "Equip a steel plate body, maple shortbow, and bronze boots."),
            mapOf(
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.STEEL_PLATEBODY,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.MAPLE_SHORTBOW,
                EquipmentType.BOOTS to gg.rsmod.plugins.api.cfg.Items.BRONZE_BOOTS,
            ),
        ),
        EmoteClueLocation(
            Tile(2757, 3445, 0),
            Emote.JUMP_FOR_JOY,
            listOf("Jump for joy at the beehives.", "Equip iron boots, an unholy symbol, and a steel hatchet."),
            mapOf(
                EquipmentType.BOOTS to gg.rsmod.plugins.api.cfg.Items.IRON_BOOTS,
                EquipmentType.AMULET to gg.rsmod.plugins.api.cfg.Items.UNHOLY_SYMBOL,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.STEEL_HATCHET,
            ),
        ),
        EmoteClueLocation(
            Tile(3111, 3296, 0),
            Emote.DANCE,
            listOf("Dance at the crossroads north of Draynor.", "Equip an iron chain body, a sapphire ring, and a shortbow."),
            mapOf(
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.IRON_CHAINBODY,
                EquipmentType.RING to gg.rsmod.plugins.api.cfg.Items.SAPPHIRE_RING,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.SHORTBOW,
            ),
        ),
        EmoteClueLocation(
            Tile(2923, 3484, 0),
            Emote.CHEER,
            listOf("Cheer at the Druid's Circle.", "Equip an air tiara, bronze two-handed sword, and gold amulet."),
            mapOf(
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.AIR_TIARA,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.BRONZE_2H_SWORD,
                EquipmentType.AMULET to gg.rsmod.plugins.api.cfg.Items.GOLD_AMULET_1692,
            ),
        ),
        EmoteClueLocation(
            Tile(3314, 3241, 0),
            Emote.BOW,
            listOf("Bow or curtsy in the ticket office of the Duel Arena.", "Equip an iron chain body, leather chaps and a coif."),
            mapOf(
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.LEATHER_CHAPS,
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.COIF,
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.IRON_CHAINBODY,
            ),
        ),
        EmoteClueLocation(
            Tile(2613, 3386, 0),
            Emote.JIG,
            listOf("Dance a jig by the entrance to the fishing guild.", "Equip an emerald ring, a sapphire amulet, and a bronze chain body."),
            mapOf(
                EquipmentType.RING to gg.rsmod.plugins.api.cfg.Items.EMERALD_RING,
                EquipmentType.AMULET to gg.rsmod.plugins.api.cfg.Items.SAPPHIRE_AMULET_1694,
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.BRONZE_CHAINBODY,
            ),
        ),
        EmoteClueLocation(
            Tile(2586, 3422, 0),
            Emote.RASPBERRY,
            listOf("Blow a raspberry in the Fishing Guild bank.", "Beware of double agents!", "Equip an elemental shield, blue dragonhide chaps, and rune warhammer."),
            mapOf(
                EquipmentType.SHIELD to gg.rsmod.plugins.api.cfg.Items.ELEMENTAL_SHIELD,
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.BLUE_DHIDE_CHAPS,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.RUNE_WARHAMMER,
            ),
        ),
        EmoteClueLocation(
            Tile(3203, 3169, 0),
            Emote.DANCE,
            listOf("Dance in the shack in Lumbridge Swamp.", "Equip a bronze dagger, iron full helmet, and a gold ring"),
            mapOf(
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.BRONZE_DAGGER,
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.IRON_FULL_HELM,
                EquipmentType.RING to gg.rsmod.plugins.api.cfg.Items.GOLD_RING,
            ),
        ),
        EmoteClueLocation(
            Tile(3045, 3376, 0),
            Emote.DANCE,
            listOf("Dance in the Party Room.", "Equip a steel full helmet, steel platebody and an iron plateskirt."),
            mapOf(
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.STEEL_FULL_HELM,
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.STEEL_PLATEBODY,
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.IRON_PLATESKIRT,
            ),
        ),
        EmoteClueLocation(
            Tile(3048, 3234, 0),
            Emote.CHEER,
            listOf("Cheer for the monks at Port Sarim.", "Equip a coif, steel plateskirt, and a sapphire necklace."),
            mapOf(
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.COIF,
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.STEEL_PLATESKIRT,
                EquipmentType.AMULET to gg.rsmod.plugins.api.cfg.Items.SAPPHIRE_NECKLACE,
            ),
        ),
        EmoteClueLocation(
            Tile(3294, 3933, 0),
            Emote.SHRUG,
            listOf("Shrug in the Rogue's Castle found deep in the North Eastern Wilderness. Beware of double agents!", "Equip iron platelegs, dragon scimitar, and climbing boots."),
            mapOf(
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.IRON_PLATELEGS,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.DRAGON_SCIMITAR,
                EquipmentType.BOOTS to gg.rsmod.plugins.api.cfg.Items.ROCK_CLIMBING_BOOTS,
            ),
        ),
        EmoteClueLocation(
            Tile(3025, 3701, 0),
            Emote.YAWN,
            listOf("Yawn in the rogues' general store. Beware of double agents!", "Equip an iron kiteshield, blue dragon vambraces and an iron pickaxe."),
            mapOf(
                EquipmentType.SHIELD to gg.rsmod.plugins.api.cfg.Items.IRON_KITESHIELD,
                EquipmentType.GLOVES to gg.rsmod.plugins.api.cfg.Items.BLUE_DHIDE_VAMBRACES,
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.IRON_PICKAXE,
            ),
        ),
        EmoteClueLocation(
            Tile(3113, 3180, 0),
            Emote.CLAP,
            listOf("Clap on the causeway to the Wizard's Tower.", "Equip an iron medium helmet, an emerald ring, and leather gloves."),
            mapOf(
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.IRON_MED_HELM,
                EquipmentType.RING to gg.rsmod.plugins.api.cfg.Items.EMERALD_RING,
                EquipmentType.GLOVES to gg.rsmod.plugins.api.cfg.Items.LEATHER_GLOVES,
            ),
        ),
        EmoteClueLocation(
            Tile(2611, 3091, 0),
            Emote.JUMP_FOR_JOY,
            listOf("Jump for joy in Yanille bank.", "Equip a iron crossbow, adamant medium helmet, and snakeskin chaps."),
            mapOf(
                EquipmentType.WEAPON to gg.rsmod.plugins.api.cfg.Items.IRON_CROSSBOW,
                EquipmentType.HEAD to gg.rsmod.plugins.api.cfg.Items.ADAMANT_MED_HELM,
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.SNAKESKIN_CHAPS,
            ),
        ),
        EmoteClueLocation(
            Tile(3240, 3609, 0),
            Emote.SHRUG,
            listOf("Shrug in the Zamorak Temple found in the Eastern Wilderness. Beware of double agents!", "Equip bronze platelegs, an iron plate body, and blue dragonhide vambraces."),
            mapOf(
                EquipmentType.LEGS to gg.rsmod.plugins.api.cfg.Items.BRONZE_PLATELEGS,
                EquipmentType.CHEST to gg.rsmod.plugins.api.cfg.Items.IRON_PLATEBODY,
                EquipmentType.GLOVES to gg.rsmod.plugins.api.cfg.Items.BLUE_DHIDE_VAMBRACES,
            ),
        ),
    )
