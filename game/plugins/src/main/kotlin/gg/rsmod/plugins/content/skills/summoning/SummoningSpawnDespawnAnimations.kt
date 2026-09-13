package gg.rsmod.plugins.content.skills.summoning

/**
 * Per-familiar summon (spawn) and dismiss (despawn) animations.
 *
 * ## Provenance
 *
 * The spawn column is sourced directly from Void's canonical 77-row
 * `data/skill/summoning/summoning_spawn.anims.toml`; it matches every mapped row here. Void and
 * Novite contain no despawn table, so the permitted fallback is Darkan (`jojo162/world-server`,
 * commit `2b63cd96`, `Pouch.java`), which plays `despawnAnim` in `dismiss()` and removes the npc
 * three ticks later. The extracted fallback is saved at
 * `C:\RSPS\summoning_refs\darkan_spawn_despawn_anims.txt`; this table keeps only the canonical 78
 * Summoning familiars (that donor also carries unrelated custom npcs -
 * Meerkats, Ghast, the numbered Bloodrager/Stormbringer/Hoardstalker/Skinweaver/Worldbearer/
 * Deathslinger/Clay pouch rows - which are not part of this roster and are excluded).
 *
 * Every id below was cross-checked two ways before being trusted: the donor's `npc=` column was
 * matched against this project's own `Npcs` constants (e.g. `VOID_SPINNER npc=7333` against
 * `Npcs.VOID_SPINNER = 7333`), and every distinct spawn/despawn id was then verified to actually
 * decode as a `SeqType` in the real 667 production cache via
 * `./gradlew :game:runSeqSoundProbeTool --args="C:/RSPS/game/game/data/cache <ids>"`. All 116
 * distinct ids besides Albino rat's decoded cleanly; Phoenix's pair (11095/11096) turned out to
 * carry real attached sounds (`sound5776`/`sound5753`) and is the one spawn/despawn pair in the
 * whole roster with sourced audio - every other familiar's spawn/despawn animation is silent.
 *
 * ## Albino rat - SOURCE_BLOCKED
 *
 * Darkan's table gives Albino rat spawn=16080/despawn=16081, but the probe above reports both
 * `ABSENT` from this 667 production cache - they simply do not decode as sequences here, matching
 * the standing suspicion recorded in the Q-033-b checkpoint before this table was ported. No other
 * revision-667 source for Albino rat's own spawn/despawn animation has been found. Recorded as
 * `-1`/`-1` (no animation played) rather than guessing a substitute.
 */
object SummoningSpawnDespawnAnimations {
    /** Familiar -> (spawn animation, despawn animation). -1 means no sourced animation exists. */
    private val ANIMATIONS: Map<SummoningPouchData, Pair<Int, Int>> =
        mapOf(
            SummoningPouchData.SPIRIT_WOLF to (8298 to 8532),
            SummoningPouchData.DREADFOWL to (7807 to 8555),
            SummoningPouchData.SPIRIT_SPIDER to (8163 to 8544),
            SummoningPouchData.THORNY_SNAIL to (8141 to 8561),
            SummoningPouchData.GRANITE_CRAB to (8108 to 8541),
            SummoningPouchData.SPIRIT_MOSQUITO to (8037 to 8915),
            SummoningPouchData.DESERT_WYRM to (7794 to 8537),
            SummoningPouchData.SPIRIT_SCORPION to (8127 to 8546),
            SummoningPouchData.SPIRIT_TZ_KIH to (8260 to 8927),
            SummoningPouchData.ALBINO_RAT to (-1 to -1), // SOURCE_BLOCKED: darkan 16080/16081 ABSENT from 667 cache (runSeqSoundProbeTool)
            SummoningPouchData.SPIRIT_KALPHITE to (8516 to 8531),
            SummoningPouchData.COMPOST_MOUND to (7773 to 8540),
            SummoningPouchData.GIANT_CHINCHOMPA to (7754 to 8922),
            SummoningPouchData.VAMPYRE_BAT to (8279 to 8563),
            SummoningPouchData.HONEY_BADGER to (7929 to 8568),
            SummoningPouchData.BEAVER to (7721 to 8535),
            SummoningPouchData.VOID_RAVAGER to (8091 to 8929),
            SummoningPouchData.VOID_SHIFTER to (8134 to 8919),
            SummoningPouchData.VOID_SPINNER to (8174 to 8920),
            SummoningPouchData.VOID_TORCHER to (8238 to 8921),
            SummoningPouchData.BRONZE_MINOTAUR to (8029 to 8549),
            SummoningPouchData.BULL_ANT to (7894 to 8554),
            SummoningPouchData.MACAW to (8005 to 8553),
            SummoningPouchData.EVIL_TURNIP to (8252 to 8250),
            SummoningPouchData.SPIRIT_COCKATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_GUTHATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_SARATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_ZAMATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_PENGATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_CORAXATRICE to (7765 to 8551),
            SummoningPouchData.SPIRIT_VULATRICE to (7765 to 8551),
            SummoningPouchData.IRON_MINOTAUR to (8029 to 8549),
            SummoningPouchData.PYRELORD to (8081 to 8930),
            SummoningPouchData.MAGPIE to (8005 to 8553),
            SummoningPouchData.BLOATED_LEECH to (7711 to 8567),
            SummoningPouchData.SPIRIT_TERRORBIRD to (8231 to 8557),
            SummoningPouchData.ABYSSAL_PARASITE to (7669 to 8556),
            SummoningPouchData.SPIRIT_JELLY to (8513 to 7075),
            SummoningPouchData.IBIS to (8202 to 8552),
            SummoningPouchData.STEEL_MINOTAUR to (8029 to 8549),
            SummoningPouchData.SPIRIT_GRAAHK to (7909 to 8917),
            SummoningPouchData.SPIRIT_KYATT to (7909 to 8917),
            SummoningPouchData.SPIRIT_LARUPIA to (7909 to 8917),
            SummoningPouchData.KARAMTHULHU_OVERLORD to (7969 to 8543),
            SummoningPouchData.SMOKE_DEVIL to (7819 to 8530),
            SummoningPouchData.ABYSSAL_LURKER to (7683 to 8564),
            SummoningPouchData.SPIRIT_COBRA to (8157 to 8550),
            SummoningPouchData.STRANGER_PLANT to (8216 to 8533),
            SummoningPouchData.BARKER_TOAD to (7702 to 8539),
            SummoningPouchData.MITHRIL_MINOTAUR to (8029 to 8549),
            SummoningPouchData.WAR_TORTOISE to (8282 to 8542),
            SummoningPouchData.BUNYIP to (7736 to 8547),
            SummoningPouchData.FRUIT_BAT to (8279 to 8563),
            SummoningPouchData.RAVENOUS_LOCUST to (7997 to 8931),
            SummoningPouchData.ARCTIC_BEAR to (8522 to 8566),
            SummoningPouchData.PHOENIX to (11095 to 11096),
            SummoningPouchData.OBSIDIAN_GOLEM to (8049 to 8924),
            SummoningPouchData.GRANITE_LOBSTER to (8122 to 8548),
            SummoningPouchData.PRAYING_MANTIS to (8075 to 8558),
            SummoningPouchData.FORGE_REGENT to (7870 to 8934),
            SummoningPouchData.ADAMANT_MINOTAUR to (8029 to 8549),
            SummoningPouchData.TALON_BEAST to (8045 to 8933),
            SummoningPouchData.GIANT_ENT to (7850 to 8559),
            SummoningPouchData.FIRE_TITAN to (7829 to 8925),
            SummoningPouchData.ICE_TITAN to (8188 to 8926),
            SummoningPouchData.MOSS_TITAN to (8188 to 8926),
            SummoningPouchData.HYDRA to (7940 to 8538),
            SummoningPouchData.SPIRIT_DAGANNOTH to (7783 to 8560),
            SummoningPouchData.LAVA_TITAN to (7987 to 8928),
            SummoningPouchData.SWAMP_TITAN to (8225 to 8932),
            SummoningPouchData.RUNE_MINOTAUR to (8029 to 8549),
            SummoningPouchData.UNICORN_STALLION to (8266 to 8565),
            SummoningPouchData.GEYSER_TITAN to (7881 to 7880),
            SummoningPouchData.WOLPERTINGER to (8309 to 8534),
            SummoningPouchData.ABYSSAL_TITAN to (8188 to 8926),
            SummoningPouchData.IRON_TITAN to (8188 to 8926),
            SummoningPouchData.PACK_YAK to (8058 to 8536),
            SummoningPouchData.STEEL_TITAN to (8188 to 8926),
        )

    /** The animation to play when [pouch]'s familiar is summoned, or -1 when none is sourced. */
    fun spawnAnim(pouch: SummoningPouchData): Int = ANIMATIONS.getValue(pouch).first

    /** The animation to play when [pouch]'s familiar despawns, or -1 when none is sourced. */
    fun despawnAnim(pouch: SummoningPouchData): Int = ANIMATIONS.getValue(pouch).second
}
