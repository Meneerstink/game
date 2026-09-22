package gg.rsmod.plugins.api.cfg

/**
 * Represents the facial expressions (the animations the entity does when
 * talking).
 * @author Emperor
 * @author Empathy
 */

/**
 * Dialogue options
 */
const val FIRST_OPTION = 1
const val SECOND_OPTION = 2
const val THIRD_OPTION = 3
const val FOURTH_OPTION = 4
const val FIFTH_OPTION = 5

/**
 * The head animation a dialogue portrait plays - one pose per rig, because this server has two kinds of head.
 *
 * Owner 2026-09-21/22: every npc's dialogue head was "small and incorrect not fitting" (Estate agent, gamblers, Max).
 * The portrait itself was never the animation's fault: the client clipped every model component to its own box, and
 * Jagex's chathead box (241-244 / 64-67 component 2) is 32 x 32 with the head drawn well past it, so every head was
 * cut down to the square around the nose (`InterfaceManager.clipModelComponents`, fixed in the client).
 *
 * On 2026-09-21 this enum had been switched from the 97xx/98xx poses to 588-617 on the belief that 98xx is a
 * full-body rig. Measured in this cache (read-only probe 2026-09-22), that was wrong for native heads:
 *  * every native 667 head model (Hans, Man/Banker, Estate agent, Gambler, Wise Old Man, Max) carries vertex labels
 *    65-199 - all in base **2165**, none in base 82 - so only the 97xx/98xx poses move them ([hd]);
 *  * OSRS-imported heads (Watson, the Deadman guards, Wizguard, Skully) carry labels 0-33, the classic head rig
 *    base **82** that 588-617 animate ([classic]).
 * Interfaces 241-244, bases 82/2165, the head models and the 98xx sequences are byte-identical to the pristine
 * openrs2 667 cache. `chatNpc` picks the rig per npc from its head models ([gg.rsmod.plugins.api.ext.chatheadAnimation]).
 *
 * HD ids and their meaning: the revision-667 donor Novite `ChatAnimation` (LISTENING 9804, PLAIN 9808, SNOBBY 9832,
 * UNSURE 9836, LISTEN_LAUGH 9840, SWAYING 9844, NORMAL 9847, LAUGHING 9851, SAD 9760, CRYING 9765, WHY 9776,
 * ANGRY 9788, FURIOUS 9792, THINKING 9827). Classic ids: ghreborn-667 `DialogueEmote` (HAPPY 588, DEFAULT 591, ...).
 * The `CHILD_*` entries are the separate child-head rig (7171-7179, base 1627) and are the same on both.
 */
enum class FacialExpression(
    /** The pose on the classic head rig (base 82, labels 0-50): OSRS-imported heads. */
    val classic: Int,
    /** The pose on the HD head rig (base 2165): every native revision-667 head, and the player's own. */
    val hd: Int,
) {
    /**
     * No facial animation at all - the portrait is drawn in its model's own resting pose.
     *
     * -1 is the client's own answer rather than an invented sentinel - `MainLogicManager`'s `IF_SETMODELANIM` branch
     * sets `component.animator = null` for exactly this value. It stays the right choice for a chathead that has no
     * dialogue-emote frames at all, such as a familiar or a beast.
     */
    NONE(classic = -1, hd = -1),

    // ---- the thirty revision-667 dialogue emotes, under the names this codebase already calls them by ----

    /** DEFAULT 591 - the plain talking pose. */
    NORMAL(classic = 591, hd = 9808),
    ANGRY(classic = 614, hd = 9788),
    GRUMPY(classic = 595, hd = 9788),
    ANNOYED(classic = 595, hd = 9788),
    SAD(classic = 599, hd = 9760),
    DISTRESSED(classic = 596, hd = 9765),
    HAPPY(classic = 588, hd = 9847),
    NEARLY_CRYING(classic = 598, hd = 9765),

    // The child chathead rig (frameset 1815, base 1627) - a different rig, and never part of the fault above.
    CHILD_QUESTIONABLE(classic = 7171, hd = 7171),
    CHILD_BACK_AND_FORTH(classic = 7172, hd = 7172),
    CHILD_NORMAL(classic = 7173, hd = 7173),
    CHILD_SLOW_NOD(classic = 7174, hd = 7174),
    CHILD_CRAZY_LAUGH(classic = 7175, hd = 7175),
    CHILD_THINKING(classic = 7176, hd = 7176),
    CHILD_SAD(classic = 7177, hd = 7177),
    CHILD_BIG_EYES(classic = 7178, hd = 7178),
    CHILD_LOOKING_OUT(classic = 7179, hd = 7179),

    SAD_2(classic = 599, hd = 9760),
    DWELL(classic = 589, hd = 9804),
    TEARY(classic = 598, hd = 9765),
    UPSET(classic = 610, hd = 9776),
    CRYING(classic = 611, hd = 9765),
    WAILING(classic = 611, hd = 9765),
    AFRAID(classic = 596, hd = 9776),
    SHOCK(classic = 596, hd = 9776),
    DISBELIEF(classic = 601, hd = 9836),
    SCARED(classic = 596, hd = 9776),
    ANGRY_2(classic = 615, hd = 9788),
    FURIOUS(classic = 616, hd = 9792),
    MAD(classic = 617, hd = 9792),
    TALKING(classic = 591, hd = 9808),
    DELAYED(classic = 590, hd = 9804),
    THINK(classic = 602, hd = 9827),
    SUSPICIOUS(classic = 600, hd = 9836),
    CHEERFUL(classic = 588, hd = 9847),
    LAUGH(classic = 605, hd = 9840),
    LAUGHING(classic = 605, hd = 9851),
    HYSTERICS(classic = 606, hd = 9851),
    AGREE(classic = 589, hd = 9847),
    DISAGREE(classic = 595, hd = 9836),
    UNCERTAIN(classic = 601, hd = 9836),
    DISREGARD(classic = 602, hd = 9832),
    DISDAIN(classic = 595, hd = 9832),
    SILENT(classic = 591, hd = 9804),
    EYES_CLOSED(classic = 603, hd = 9804),
    ASLEEP(classic = 603, hd = 9804),
    CONFLICTED(classic = 600, hd = 9836),
    CHICKEN(classic = 612, hd = 9844),

    /** The default of every `chatNpc` / `chatPlayer` call that does not name an expression: HAPPY 588. */
    HAPPY_TALKING(classic = 588, hd = 9847),

    GOOFY(classic = 608, hd = 9844),
    REALLY_SAD(classic = 611, hd = 9765),
    DEPRESSED(classic = 610, hd = 9760),
    WORRIED(classic = 597, hd = 9836),
    MEAN_FACE(classic = 592, hd = 9788),
    MEAN_HEAD_BANG(classic = 593, hd = 9792),
    EVIL(classic = 592, hd = 9788),
    WHAT_THE_CRAP(classic = 601, hd = 9776),
    CALM(classic = 589, hd = 9808),
    CALM_TALK(classic = 590, hd = 9808),
    TOUGH(classic = 614, hd = 9788),
    SNOBBY(classic = 602, hd = 9832),
    SNOBBY_HEAD_MOVE(classic = 602, hd = 9832),
    CONFUSED(classic = 600, hd = 9836),
    DRUNK_HAPPY_TIRED(classic = 603, hd = 9844),
    TALKING_ALOT(classic = 590, hd = 9844),
    BAD_ASS(classic = 592, hd = 9832),
    THINKING(classic = 602, hd = 9827),
    COOL_YES(classic = 589, hd = 9847),
    LAUGH_EXCITED(classic = 607, hd = 9851),
    SECRETLY_TALKING(classic = 594, hd = 9808),
    OLD_NORMAL(classic = 591, hd = 9808),
    ;

    /** The HD pose: native 667 npcs and the player. For an npc use [gg.rsmod.plugins.api.ext.chatheadAnimation]. */
    val animationId: Int get() = hd
}
