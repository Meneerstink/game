package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * Owner 2026-09-29 (Leech Ranged / Leech Magic now carry Rigour's / Augury's offence, AncientCurses.leechMultiplier): the
 * prayer-tab hover text of those two curses must say so. The text is a string param of config structs 899 (Leech Ranged)
 * and 900 (Leech Magic) in config group 26; only that one sentence is replaced, byte for byte, every other param stays.
 * One [CacheTransaction] over both production caches (preflight, journal, verify); running it again is a no-op.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.CurseTooltipCacheTool plan|apply`
 */
object CurseTooltipCacheTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val CONFIG_INDEX = 2
    private const val STRUCT_GROUP = 26

    /** [olds]: every earlier wording of the sentence (the 667 original first); the one present is replaced by [new]. */
    class Tooltip(val struct: Int, val olds: List<String>, val new: String)

    // Owner 2026-09-29, second round: Leech Ranged damage +23 % -> +18 % and Leech Magic damage +4 % -> 0 % ("one hit lower",
    // accuracy unchanged); Leech Defence a flat +15 %.
    // Owner A8 2026-10-11 (night run): "the curse tooltips are wrong" - every one of the 20 was compared with what
    // AncientCurses really does; these 13 said something else and now state the real effect in the 2011 wording. The other 7
    // (Protect Item, the three Saps, Berserker, Soul Split, Turmoil) already matched and stay.
    private const val LEECH_DRAIN = "while draining enemy %s by 10%%, increasing to 25%% over time."

    private fun leech(skill: String, pct: Int) =
        "Boosts $skill by $pct% and raises it up to 5 levels over time, " + LEECH_DRAIN.format(skill)

    /** The 667 Sap sentence (only the drain). */
    private fun sapOriginal(drained: String) = "Drains 10% of enemy $drained, increasing to 20% over time."

    private const val SAP_DRAIN = "while draining enemy %s by 10%%, increasing to 20%% over time."

    private fun deflect(attacks: String) =
        "Protects against $attacks (40% against players) and has a 15% chance to deflect 10% of the damage back to the attacker."

    private fun original(skill: String) =
        "Boosts $skill by 5%, increasing to 10% over time, while draining enemy $skill by 10%, increasing to 25% over time."

    val TOOLTIPS =
        listOf(
            // Owner curse table 2026-10-11 (FINAL, dossier 4a-2): a Sap boosts its caster while on (AncientCurses.sapMultiplier,
            // SAP_SELF_BOOST_PCT 10) - Sap Warrior Attack + Strength, Sap Ranger ranged accuracy + damage, Sap Mage magic accuracy.
            Tooltip(
                889,
                listOf(sapOriginal("Attack, Strength and Defence")),
                "Boosts Attack and Strength by 10%, " + SAP_DRAIN.format("Attack, Strength and Defence"),
            ),
            Tooltip(890, listOf(sapOriginal("Ranged and Defence")), "Boosts Ranged accuracy and damage by 10%, " + SAP_DRAIN.format("Ranged and Defence")),
            Tooltip(891, listOf(sapOriginal("Magic and Defence")), "Boosts Magic accuracy by 10%, " + SAP_DRAIN.format("Magic and Defence")),
            // AncientCurses SAP_SPIRIT: AttackTab energy - 10.
            Tooltip(892, listOf("Drains enemy special attack energy."), "Drains 10% of enemy special attack energy."),
            // FamiliarCombat.blockedBySummoningProtection: a familiar's attacks and special moves do not land; nothing is reflected.
            Tooltip(
                894,
                listOf(
                    "Reduces damage dealt from Summoning scrolls, prevents the use of a familiar's special attack, and can deflect some " +
                        "of the damage back to the attacker.",
                ),
                "Protects against the attacks and special moves of familiars.",
            ),
            // deflectDamageTaken: all of an npc's hit, 40 % of a player's; onIncomingHit: 15 % chance to reflect 10 %.
            Tooltip(895, listOf("Protects against magical attacks and can deflect some of the damage back to the attacker."), deflect("magical attacks")),
            Tooltip(896, listOf("Protects against ranged attacks and can deflect some of the damage back to the attacker."), deflect("ranged attacks")),
            Tooltip(897, listOf("Protects against melee attacks and can deflect some of the damage back to the attacker."), deflect("melee attacks")),
            // leechMultiplier while active (owner 2026-10-11: LEECH_ATTACK_BOOST_PCT 15, was 5) + escalateBoost +1 level per proc,
            // at most LEECH_LEVEL_CAP 5. The A8 wording with 5 % is the one now in the cache.
            Tooltip(898, listOf(original("Attack"), leech("Attack", 5)), leech("Attack", 15)),
            // Earlier wordings first (667 original, 2026-09-29 Rigour, 2026-09-29 second round); now x1.20 accuracy, x1.13 damage.
            Tooltip(
                899,
                listOf(
                    original("Ranged"),
                    "Boosts Ranged accuracy by 20% and Ranged damage by 23%, while draining enemy Ranged by 10%, increasing to 25% over time.",
                    "Boosts Ranged accuracy by 20% and Ranged damage by 18%, while draining enemy Ranged by 10%, increasing to 25% over time.",
                ),
                "Boosts Ranged accuracy by 20% and Ranged damage by 13%, and raises Ranged up to 5 levels over time, " +
                    LEECH_DRAIN.format("Ranged"),
            ),
            // Owner 2026-10-09 (final): x1.25 magic accuracy, +4 % magic damage, +1 Magic level per proc.
            Tooltip(
                900,
                listOf(
                    original("Magic"),
                    "Boosts Magic accuracy by 25% and Magic damage by 4%, while draining enemy Magic by 10%, increasing to 25% over time.",
                    "Boosts Magic accuracy by 25%, while draining enemy Magic by 10%, increasing to 25% over time.",
                ),
                "Boosts Magic accuracy by 25% and Magic damage by 4%, and raises Magic up to 5 levels over time, " +
                    LEECH_DRAIN.format("Magic"),
            ),
            // Owner 2026-10-11: Leech Defence +10 % (LEECH_DEFENCE_BOOST_PCT), Leech Strength +18 % (LEECH_STRENGTH_BOOST_PCT).
            Tooltip(
                901,
                listOf(
                    original("Defence"),
                    "Boosts Defence by 15%, while draining enemy Defence by 10%, increasing to 25% over time.",
                    leech("Defence", 5),
                ),
                leech("Defence", 10),
            ),
            Tooltip(902, listOf(original("Strength"), leech("Strength", 5)), leech("Strength", 18)),
            // LEECH_ENERGY: +10 / -10 run energy; LEECH_SPECIAL_ATTACK: LEECH_SPECIAL_PCT 5.
            Tooltip(903, listOf("Drains enemy run energy, while increasing your own"), "Drains 10% of enemy run energy and gives it to you."),
            Tooltip(
                904,
                listOf("Drains enemy special attack energy, while increasing your own"),
                "Drains 5% of enemy special attack energy and gives it to you.",
            ),
            // wrathMaxDamage: up to 30 % of the Prayer level, to every target within two tiles (multi) or the killer (single).
            Tooltip(905, listOf("Inflicts damage to nearby<br>targets if you die"), "Inflicts up to 30% of your Prayer level as damage<br>to nearby targets if you die"),
            // N45 (owner 10-09 Q1 "ALL curse and prayer tooltips"): the normal prayers Augury (struct 1006) and Rigour (1029) state
            // what Prayers/MagicCombatFormula/RangedCombatFormula do - Augury magic accuracy x1.25, magic damage +4 %, Defence
            // +25 %; Rigour Ranged accuracy +20 %, Ranged damage +23 %, Defence +25 % - and their unlock is N98's scroll + 70
            // Defence, not Daemonheim (DGN: Dungeoneering is removed).
            Tooltip(
                1006,
                // The prayer tooltip wraps by itself: no <br> (it broke the line after "by 25%," - checked in the client).
                listOf("Increases magical accuracy by 20% and Defence by 25%.", "Increases magical accuracy by 25%,<br>magic damage by 4% and Defence by 25%."),
                "Increases magical accuracy by 25%, magic damage by 4% and Defence by 25%.",
            ),
            Tooltip(
                1006,
                listOf("You can buy this prayer from Daemonheim. You also need a Prayer level of 77."),
                "Read an Arcane prayer scroll first. You also need Prayer 77 and Defence 70.",
            ),
            Tooltip(
                1029,
                listOf("Increases your Ranged by 20% and Defence by 25%.", "Increases Ranged accuracy by 20%,<br>Ranged damage by 23% and Defence by 25%."),
                "Increases Ranged accuracy by 20%, Ranged damage by 23% and Defence by 25%.",
            ),
            Tooltip(
                1029,
                listOf("You can buy this prayer from Daemonheim. You also need a Prayer level of 74."),
                "Read a Dexterous prayer scroll first. You also need Prayer 74 and Defence 70.",
            ),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val mutations = plan(GAME_CACHE)
        if (mutations.isEmpty()) {
            println("CURSE_TOOLTIPS already applied")
            return
        }
        val transaction = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = transaction.preflight()
        println(preflight.joinToString("\n"))
        val errors = transaction.blockingErrors(preflight)
        check(errors.isEmpty()) { errors.joinToString("\n") }
        if (!apply) return
        val result = transaction.apply(preflight)
        val problems = transaction.verify()
        check(problems.isEmpty()) { problems.joinToString("\n") }
        println("CURSE_TOOLTIPS transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }

    /** The mutations still needed against [cache]; empty once both texts are in place. */
    fun plan(cache: String): List<CacheMutation> {
        val library = CacheLibrary(cache)
        try {
            // N45: a struct may carry two texts (Augury / Rigour: effect and unlock) - every replacement of one struct goes into ONE
            // mutation, folded over the same bytes.
            return TOOLTIPS.groupBy { it.struct }.mapNotNull { (struct, tooltips) ->
                val current = requireNotNull(library.data(CONFIG_INDEX, STRUCT_GROUP, struct)) { "struct $struct missing" }
                val patched = tooltips.fold(current) { data, tooltip -> replaceText(data, tooltip.olds, tooltip.new) ?: data }
                if (patched.contentEquals(current)) return@mapNotNull null
                CacheMutation(
                    CONFIG_INDEX, STRUCT_GROUP, struct, patched, "struct $struct: prayer tooltip",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current),
                )
            }
        } finally {
            library.close()
        }
    }

    /** [data] with whichever of [olds] it holds replaced by [new] (exactly one occurrence); null when [new] is already there. */
    fun replaceText(
        data: ByteArray,
        olds: List<String>,
        new: String,
    ): ByteArray? {
        val text = String(data, Charsets.ISO_8859_1)
        if (text.contains(new)) return null
        val old = olds.firstOrNull { text.contains(it) } ?: error("none of the known texts found: $olds")
        val at = text.indexOf(old)
        check(text.indexOf(old, at + 1) < 0) { "expected exactly one '$old'" }
        return (text.substring(0, at) + new + text.substring(at + old.length)).toByteArray(Charsets.ISO_8859_1)
    }
}