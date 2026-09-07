package gg.rsmod.plugins.content.skills.summoning

/**
 * The combat level each familiar is displayed at, and why it has to be sent at all.
 *
 * ## The fault this exists to fix
 *
 * Every one of the 78 familiar npc definitions in this production cache carries
 * `combatLevel = 0` - verified across the whole roster by `runNpcDefProbeTool` and recorded in
 * `C:\RSPS\summoning_refs\familiar_npcdefs.txt`. The client takes the level it draws beside an
 * npc's name from its own cached `NPCType` (config opcode 95), and the server never sends npc
 * definitions, so the client's `MiniMenu` branch `if (npc.combatLevel != 0)` never fires for any
 * familiar. That is the owner's G9 - no familiar has ever shown a combat level, Steel titan in the
 * Wilderness included - and no amount of server-side combat data could have changed it.
 *
 * The revision does provide one lever: the `COMBAT_LEVEL` extended-info block
 * (`NpcExtendedInfoFlag.COMBAT_LEVEL = 0x80000`), which `NPCList` applies over the cached value.
 * See [gg.rsmod.game.model.entity.Npc.setCombatLevel]. Nothing here edits the cache.
 *
 * ## Provenance
 *
 * [ARTICLE_LEVELS] is quoted from the archived 2011 Jagex Knowledge Base article
 * `C:\RSPS\2011RS_SUMMONING_FAMILIARS.md`, which states each familiar's level inline in its
 * abilities cell and says so explicitly: *"the level in brackets indicates the familiar's Combat
 * level"*. Two shapes appear - `Fights (Level 230)` for Steel titan and `Fights (Level 25 -
 * Controlled)` for Spirit spider - and both are read.
 *
 * The article's naming differs from [SummoningPouchData] in three places, resolved by identity
 * rather than spelling: "Mosquito" is [SummoningPouchData.SPIRIT_MOSQUITO], "Phoenix essence" is
 * [SummoningPouchData.PHOENIX] and "Vampire bat" is [SummoningPouchData.VAMPYRE_BAT].
 *
 * ## The six familiars the article does not cover
 *
 * The article predates, or simply omits, the six alternative cockatrice hatchlings - Saratrice,
 * Guthatrice, Zamatrice, Pengatrice, Vulatrice and Coraxatrice. Rather than invent six numbers,
 * [levels] **derives** them: each is matched to the familiar whose sourced combat record in
 * [SummoningCombatDefinitions] it is identical to in every combat-relevant field, and takes that
 * familiar's article level. All six resolve to Spirit cockatrice, whose entries agree with theirs
 * field for field (hitpoints 1730, attack 39, strength 35, defence 35, ranged 39, magic 39, melee,
 * assist, range 1, speed 4, max hit 56, animations 7762/7761/7763).
 *
 * This is a derivation from data that is already sourced, not a guess, and it is computed rather
 * than hand-listed so a variant can never drift away from the familiar it copies. If a future
 * cockatrice variant is ever given combat data of its own, it drops out of the derivation and is
 * reported as uncovered instead of silently inheriting the wrong level.
 *
 * ## The five with no level, correctly
 *
 * Beaver, Fruit bat, Ibis, Macaw and Magpie are the pure foragers. The article gives them no
 * level because they do not fight, and they must show none - requirement G9's "non-combat
 * familiars -> no combat level". They are absent from [levels] and no block is sent for them, so
 * the client keeps the cache's 0 and draws nothing.
 */
object SummoningCombatLevels {
    /** Familiar -> the combat level the 2011 Knowledge Base states for it, verbatim. */
    private val ARTICLE_LEVELS: Map<SummoningPouchData, Int> =
        mapOf(
        SummoningPouchData.ABYSSAL_LURKER to 93,
        SummoningPouchData.ABYSSAL_PARASITE to 86,
        SummoningPouchData.ABYSSAL_TITAN to 215,
        SummoningPouchData.ADAMANT_MINOTAUR to 133,
        SummoningPouchData.ALBINO_RAT to 37,
        SummoningPouchData.ARCTIC_BEAR to 122,
        SummoningPouchData.BARKER_TOAD to 112,
        SummoningPouchData.BLOATED_LEECH to 76,
        SummoningPouchData.BRONZE_MINOTAUR to 50,
        SummoningPouchData.BULL_ANT to 58,
        SummoningPouchData.BUNYIP to 70,
        SummoningPouchData.COMPOST_MOUND to 37,
        SummoningPouchData.DESERT_WYRM to 31,
        SummoningPouchData.DREADFOWL to 26,
        SummoningPouchData.EVIL_TURNIP to 62,
        SummoningPouchData.FIRE_TITAN to 139,
        SummoningPouchData.FORGE_REGENT to 133,
        SummoningPouchData.GEYSER_TITAN to 200,
        SummoningPouchData.GIANT_CHINCHOMPA to 42,
        SummoningPouchData.GIANT_ENT to 137,
        SummoningPouchData.GRANITE_CRAB to 26,
        SummoningPouchData.GRANITE_LOBSTER to 129,
        SummoningPouchData.HONEY_BADGER to 45,
        SummoningPouchData.HYDRA to 141,
        SummoningPouchData.ICE_TITAN to 139,
        SummoningPouchData.IRON_MINOTAUR to 70,
        SummoningPouchData.IRON_TITAN to 220,
        SummoningPouchData.KARAMTHULHU_OVERLORD to 95,
        SummoningPouchData.LAVA_TITAN to 148,
        SummoningPouchData.MITHRIL_MINOTAUR to 112,
        SummoningPouchData.MOSS_TITAN to 139,
        SummoningPouchData.OBSIDIAN_GOLEM to 126,
        SummoningPouchData.PACK_YAK to 175,
        SummoningPouchData.PHOENIX to 124,
        SummoningPouchData.PRAYING_MANTIS to 131,
        SummoningPouchData.PYRELORD to 70,
        SummoningPouchData.RAVENOUS_LOCUST to 120,
        SummoningPouchData.RUNE_MINOTAUR to 154,
        SummoningPouchData.SMOKE_DEVIL to 101,
        SummoningPouchData.SPIRIT_COBRA to 105,
        SummoningPouchData.SPIRIT_COCKATRICE to 64,
        SummoningPouchData.SPIRIT_DAGANNOTH to 148,
        SummoningPouchData.SPIRIT_GRAAHK to 93,
        SummoningPouchData.SPIRIT_JELLY to 88,
        SummoningPouchData.SPIRIT_KALPHITE to 39,
        SummoningPouchData.SPIRIT_KYATT to 93,
        SummoningPouchData.SPIRIT_LARUPIA to 93,
        SummoningPouchData.SPIRIT_MOSQUITO to 32,
        SummoningPouchData.SPIRIT_SCORPION to 51,
        SummoningPouchData.SPIRIT_SPIDER to 25,
        SummoningPouchData.SPIRIT_TERRORBIRD to 62,
        SummoningPouchData.SPIRIT_TZ_KIH to 36,
        SummoningPouchData.SPIRIT_WOLF to 26,
        SummoningPouchData.STEEL_MINOTAUR to 90,
        SummoningPouchData.STEEL_TITAN to 230,
        SummoningPouchData.STRANGER_PLANT to 107,
        SummoningPouchData.SWAMP_TITAN to 152,
        SummoningPouchData.TALON_BEAST to 135,
        SummoningPouchData.THORNY_SNAIL to 26,
        SummoningPouchData.UNICORN_STALLION to 70,
        SummoningPouchData.VAMPYRE_BAT to 44,
        SummoningPouchData.VOID_RAVAGER to 46,
        SummoningPouchData.VOID_SHIFTER to 46,
        SummoningPouchData.VOID_SPINNER to 40,
        SummoningPouchData.VOID_TORCHER to 46,
        SummoningPouchData.WAR_TORTOISE to 86,
        SummoningPouchData.WOLPERTINGER to 210,
        )

    /**
     * The combat-relevant fields of a familiar's sourced combat record, ignoring the identity
     * fields (the pouch and its npc id) that necessarily differ between two familiars.
     *
     * Two familiars with the same signature are the same creature with a different name, which is
     * exactly what the six cockatrice variants are.
     */
    private fun signature(definition: SummoningCombatDefinition): List<Any> =
        listOf(
            definition.hitpoints,
            definition.attack,
            definition.strength,
            definition.defence,
            definition.ranged,
            definition.magic,
            definition.style,
            definition.assistMode,
            definition.attackRange,
            definition.attackSpeed,
            definition.maxHit,
            definition.attackAnimation,
            definition.blockAnimation,
            definition.deathAnimation,
            definition.attackGraphic,
            definition.projectile,
        )

    /**
     * Every familiar that must display a combat level, and the level it displays.
     *
     * The article's own values, plus the ones derived from a combat-identical twin. A familiar
     * that is in neither is absent, shows no level, and is meant to.
     */
    val levels: Map<SummoningPouchData, Int> =
        buildMap {
            putAll(ARTICLE_LEVELS)
            val bySignature =
                ARTICLE_LEVELS.keys
                    .mapNotNull { pouch ->
                        SummoningCombatDefinitions.getByNpc(pouch.npc)?.let { signature(it) to ARTICLE_LEVELS.getValue(pouch) }
                    }.toMap()
            SummoningPouchData.values().forEach { pouch ->
                if (pouch in ARTICLE_LEVELS) return@forEach
                val definition = SummoningCombatDefinitions.getByNpc(pouch.npc) ?: return@forEach
                if (!definition.canFight) return@forEach
                bySignature[signature(definition)]?.let { put(pouch, it) }
            }
        }

    /** The level to display for [npcId], or `null` when that familiar must show none. */
    fun forNpc(npcId: Int): Int? =
        SummoningPouchData.values().firstOrNull { it.npc == npcId }?.let { levels[it] }
}
