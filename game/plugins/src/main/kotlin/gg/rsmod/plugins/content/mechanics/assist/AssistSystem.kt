package gg.rsmod.plugins.content.mechanics.assist

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.content.mechanics.aid.AcceptAid
import gg.rsmod.util.Misc
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.ZoneOffset

/**
 * The 2011 Assist System (owner 2026-10-01: build it completely).
 *
 * Rules - RuneScape Wiki "Assist System":
 *  - "The assisted player borrows the assister's skill levels to perform actions themselves." "The assister receives all skill
 *    experience, which is their payment for assisting." "If the player requesting assistance already has the requisite skill
 *    level to do something, they gain experience rather than the target player." Temporary boosts of the assister carry over
 *    (the assister's CURRENT level is lent).
 *  - Nine skills: Construction, Cooking, Crafting, Farming, Fletching, Herblore, Magic, Runecrafting, Smithing.
 *  - "Only 30,000 experience can be gained in a single day through assisting others", reset at 00:00 GMT; after it "assistance
 *    continues without XP rewards".
 *  - "The player being lent a skill must remain within 20 squares of the player they are being assisted by. However, if the
 *    assisted player teleports away, then they shall still be assisted." Measured from the assister; after a real teleport
 *    (Pawn.lastTeleportCycle - forced movement and shortcuts are not teleports) from the landing tile (Void's assist_point).
 *  - Request: right-click "Req Assist"; "The target player receives a message in the chat box which opens up a window when
 *    clicked"; "The assisting player cannot move or act except chatting" ([LockState.ASSISTING]).
 *  - Accept Aid must be on (to receive assistance - the requester); the Assist chat tab On / Friends / Off filters requests and
 *    "XP Earned/Time" shows the experience gained and the time left.
 * Cache (decoded 2026-10-01):
 *  - "Req Assist" is player option 7: clientscript 86 sends OPPLAYER7 for the chat op "Give assistance" (clientscript 84 gives
 *    that op to message type 102); clientscript 90 shows type 102 per the Assist filter (varp 1055: 0 On, 1 Friends, 2+ Off) and
 *    type 104 (assist lines) while the filter is below 2.
 *  - Interface 301 "Assist System XP Display": skill buttons 74..82 <-> varbits 4090, 4091, 4093, 4095, 4096, 4098, 4100, 4101,
 *    4102 (clientscript 530: non-zero = ON); total experience varbit 4103 in tenths (clientscript 535 divides by 10; 19 bits).
 *  - The assist icon is 745:2 (clientscript 1364 gives it sprite 1073; nothing in the cache shows it, the server does).
 * Donor Void (content/social/assist): the request / response flow, the 16-tick request delay and its two messages, the
 *  refusal / cap / "XP Earned/Time" lines, the 301 texts, anim 7299 + gfx 1247 - ADAPTED where the wiki has no wording.
 * Deadman (abuse audit): a borrowed level must never fight. The assistance ends the moment either player attacks or is attacked
 *  ([endForCombat], called before the attack resolves), so a combat spell is always cast with the caster's own Magic level.
 */
object AssistSystem {
    data class AssistSkill(val skill: Int, val name: String, val component: Int, val varbit: Int)

    val SKILLS =
        listOf(
            AssistSkill(Skills.RUNECRAFTING, "Runecrafting", 74, 4090),
            AssistSkill(Skills.CRAFTING, "Crafting", 75, 4091),
            AssistSkill(Skills.FLETCHING, "Fletching", 76, 4093),
            AssistSkill(Skills.CONSTRUCTION, "Construction", 77, 4095),
            AssistSkill(Skills.FARMING, "Farming", 78, 4096),
            AssistSkill(Skills.MAGIC, "Magic", 79, 4098),
            AssistSkill(Skills.SMITHING, "Smithing", 80, 4100),
            AssistSkill(Skills.COOKING, "Cooking", 81, 4101),
            AssistSkill(Skills.HERBLORE, "Herblore", 82, 4102),
        )
    private val BY_SKILL = SKILLS.associateBy { it.skill }

    const val OPTION = "Req Assist"
    const val OPTION_SLOT = 7
    const val INTERFACE = 301
    const val TITLE_COMPONENT = 73
    const val DESCRIPTION_COMPONENT = 10
    const val EARNED_VARBIT = 4103
    const val FILTER_VARP = 1055
    const val FILTER_FRIENDS = 1
    const val FILTER_OFF = 2
    const val ICON_INTERFACE = 745
    const val ICON_COMPONENT = 2
    const val MAX_XP = 30_000.0
    const val RANGE = 20
    const val REQUEST_DELAY_TICKS = 16
    const val REQUEST_TIMEOUT_TICKS = 100

    /**
     * How long a borrowed requirement check stays "the action in progress": the experience of the action that borrowed (and of the
     * next items of the same production loop, each refreshing it) goes to the assister; an unrelated gain later (a harvest minutes
     * after a borrowed planting, a fish gutted after a borrowed cook) is the player's own. 10 ticks covers the longest gap between a
     * requirement check and its experience in the nine skills (production loops check every item).
     */
    const val BORROW_WINDOW_TICKS = 10
    const val ANIM = 7299
    const val GFX = 1247

    const val REQUEST_TEXT = "is requesting your assistance."
    const val NEED_ACCEPT_AID = "You need to have Accept Aid turned on to be assisted."
    const val BUSY_ASSISTING = "You can't do that while you are assisting someone."
    const val LOWER_SKILL = "You can only assist skills which are higher than whom you are helping."

    /** On the assisted player: who assists him. */
    val ASSISTANT = AttributeKey<WeakReference<Player>>()

    /** On the assister: whom he assists. */
    val ASSISTED = AttributeKey<WeakReference<Player>>()

    /** On the assisted player: the landing tile of his last teleport in this session (the range is measured from it, else from the
     * assister) and the teleport cycle already seen. */
    val POINT = AttributeKey<Tile>()
    val TELEPORT_SEEN = AttributeKey<Int>()

    /** On a requester: the player he asked and the cycle he asked at. */
    val REQUESTED = AttributeKey<Pair<WeakReference<Player>, Int>>()

    /** On the assisted player: skill -> the cycle its last requirement check needed the lent level ([BORROW_WINDOW_TICKS]). */
    val BORROWED = AttributeKey<MutableMap<Int, Int>>()

    /**
     * Spells a lent Magic level never casts besides the combat spells (Deadman: a borrowed level never fights). A session ends at the
     * first attack, but these are cast before or beside a fight and carry into it: Vengeance kept primed, heals, energy, a god-spell
     * charge, cures and Disruption Shield for oneself or a fighting team mate.
     */
    val NO_LENDING_SPELLS =
        setOf(
            "Vengeance", "Vengeance Other", "Vengeance Group", "Heal Other", "Heal Group", "Energy Transfer", "Disruption Shield",
            "Cure Me", "Cure Other", "Cure Group", "Charge",
        )

    /** Ticks on the assisted player while assisted ([checkRange]). */
    val RANGE_TIMER = TimerKey()

    /** Experience earned by assisting today (persisted, in tenths like varbit 4103) and the UTC day it belongs to. */
    val EARNED = AttributeKey<Int>(persistenceKey = "assist_xp_earned_tenths")
    val EARNED_DAY = AttributeKey<Int>(persistenceKey = "assist_xp_day")

    fun isAssistSkill(skill: Int): Boolean = skill in BY_SKILL

    fun assistantOf(player: Player): Player? {
        val other = player.attr[ASSISTANT]?.get() ?: return null
        return other.takeIf { it.isOnline && it.attr[ASSISTED]?.get() === player }
    }

    fun assistedBy(assister: Player): Player? {
        val other = assister.attr[ASSISTED]?.get() ?: return null
        return other.takeIf { it.isOnline && it.attr[ASSISTANT]?.get() === assister }
    }

    fun inSession(player: Player): Boolean = assistantOf(player) != null || assistedBy(player) != null

    fun skillOn(
        assister: Player,
        skill: Int,
    ): Boolean = BY_SKILL[skill]?.let { assister.getVarbit(it.varbit) != 0 } ?: false

    /** The level [player] borrows for [skill] right now, or null when nobody lends it. */
    fun lentLevel(
        player: Player,
        skill: Int,
    ): Int? {
        if (!isAssistSkill(skill)) return null
        val assister = assistantOf(player) ?: return null
        if (!skillOn(assister, skill)) return null
        return assister.skills.getCurrentLevel(skill)
    }

    /** The highest level [player] could act with right now - his own or the lent one. For what he may choose (lists, selections). */
    fun availableLevel(
        player: Player,
        skill: Int,
        /** False: real (base) levels on both sides, for what boosts cannot reach. */
        boostable: Boolean = true,
    ): Int {
        if (boostable) return maxOf(player.skills.getCurrentLevel(skill), lentLevel(player, skill) ?: 0)
        val lent = lentLevel(player, skill)?.let { assistantOf(player)?.skills?.getMaxLevel(skill) } ?: 0
        return maxOf(player.skills.getMaxLevel(skill), lent)
    }

    /**
     * The level an action's chance or yield uses (burn chance, runes per essence, harvest): the lent level only when this action
     * borrowed it ([meets] marked the skill) - "If the player requesting assistance already has the requisite skill level ... they
     * gain experience rather than the target player": he acts with his own level then, and gets nothing of the assister's for free.
     * Call it after the action's [meets].
     */
    fun effectiveLevel(
        player: Player,
        skill: Int,
    ): Int = if (borrowedNow(player, skill)) availableLevel(player, skill) else player.skills.getCurrentLevel(skill)

    /** True while [skill]'s last borrowed requirement check is the action in progress ([BORROW_WINDOW_TICKS]). */
    fun borrowedNow(
        player: Player,
        skill: Int,
    ): Boolean {
        val at = player.attr[BORROWED]?.get(skill) ?: return false
        if (player.world.currentCycle - at <= BORROW_WINDOW_TICKS) return true
        player.attr[BORROWED]?.remove(skill)
        return false
    }

    /**
     * The one requirement check of an action in an assist skill: true when [player] has [required] himself (his own experience)
     * or borrows it (the experience of this action then goes to the assister, see [redirectXp]).
     */
    fun meets(
        player: Player,
        skill: Int,
        required: Int,
        /** False for a requirement boosts cannot meet: both sides then count their real (base) level. */
        boostable: Boolean = true,
    ): Boolean {
        val own = if (boostable) player.skills.getCurrentLevel(skill) else player.skills.getMaxLevel(skill)
        if (own >= required) {
            player.attr[BORROWED]?.remove(skill)
            return true
        }
        val lent =
            if (boostable) lentLevel(player, skill)
            else lentLevel(player, skill)?.let { assistantOf(player)?.skills?.getMaxLevel(skill) }
        if (lent == null) return false
        if (lent < required) return false
        (player.attr[BORROWED] ?: mutableMapOf<Int, Int>().also { player.attr[BORROWED] = it })[skill] = player.world.currentCycle
        return true
    }

    /** [gg.rsmod.game.model.skill.XpRedirect] handler: experience of a borrowed action goes to the assister (up to the daily cap). */
    fun redirectXp(
        player: Player,
        skill: Int,
        xp: Double,
        modifiers: Boolean,
        disableBonusExperience: Boolean,
    ): Boolean {
        if (!borrowedNow(player, skill)) return false
        val assister = assistantOf(player)
        if (assister == null || !skillOn(assister, skill)) {
            player.attr[BORROWED]?.remove(skill)
            return false
        }
        // A production loop's next items keep the window open.
        player.attr[BORROWED]?.set(skill, player.world.currentCycle)
        // The cap counts what the assister really gains (his level curve, xp rate and bonus apply as on any gain of his).
        val earned = earnedToday(assister)
        val multiplier = assister.xpMultiplier(skill, modifiers, disableBonusExperience).coerceAtLeast(1e-9)
        val gained = minOf(xp * multiplier, MAX_XP - earned)
        if (gained > 0.0) {
            assister.addXp(skill, gained / multiplier, modifiers, disableBonusExperience)
            setEarned(assister, earned + gained)
            if (earned + gained >= MAX_XP) {
                assister.setComponentText(INTERFACE, DESCRIPTION_COMPONENT, "You've earned the maximum XP from the Assist System within a 24-hour period. You can assist again in ${hoursLeft()} ${hours(hoursLeft())}.")
            }
        }
        return true
    }

    fun today(): Int = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().toEpochDay().toInt()

    /** Whole hours until the daily reset at 00:00 GMT (at least 1). */
    fun hoursLeft(): Int {
        val now = Instant.now().atZone(ZoneOffset.UTC)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        val seconds = midnight.toEpochSecond() - now.toEpochSecond()
        return maxOf(1, ((seconds + 3599) / 3600).toInt())
    }

    private fun hours(n: Int) = if (n == 1) "hour" else "hours"

    fun earnedToday(player: Player): Double {
        if (player.attr[EARNED_DAY] != today()) return 0.0
        return (player.attr[EARNED] ?: 0) / 10.0
    }

    fun setEarned(
        player: Player,
        xp: Double,
    ) {
        val tenths = Math.round(minOf(MAX_XP, xp) * 10).toInt()
        player.attr[EARNED] = tenths
        player.attr[EARNED_DAY] = today()
        player.setVarbit(EARNED_VARBIT, tenths)
    }

    fun capped(player: Player): Boolean = earnedToday(player) >= MAX_XP

    /** The Assist tab's "XP Earned/Time". */
    fun showEarned(player: Player) {
        if (capped(player)) {
            player.message("You've earned the maximum XP (30,000 Xp) from the Assist System within a 24-hour period.", ChatMessageType.ASSIST)
            player.message("You can assist again in ${hoursLeft()} ${hours(hoursLeft())}.", ChatMessageType.ASSIST)
        } else {
            player.message("You have earned ${earnedToday(player).toInt()} Xp. The Assist system is available to you.", ChatMessageType.ASSIST)
        }
    }

    private fun isFriend(
        owner: Player,
        other: Player,
    ): Boolean = owner.friends.any { Misc.formatForDisplay(it).equals(Misc.formatForDisplay(other.username), ignoreCase = true) }

    /** Whether [target]'s Assist filter lets [requester]'s requests through. */
    fun filterAllows(
        target: Player,
        requester: Player,
    ): Boolean =
        when (target.getVarp(FILTER_VARP)) {
            0 -> true
            FILTER_FRIENDS -> isFriend(target, requester)
            else -> false
        }

    /**
     * "Req Assist" by [player] on [other]: either [other] asked [player] before (then [player] gives assistance) or a new request
     * from [player] to [other].
     */
    fun option(
        player: Player,
        other: Player,
    ) {
        val asked = other.attr[REQUESTED]
        if (asked != null && asked.first.get() === player && player.world.currentCycle - asked.second <= REQUEST_TIMEOUT_TICKS) {
            respond(assister = player, assisted = other)
            return
        }
        request(player, other)
    }

    private fun request(
        requester: Player,
        target: Player,
    ) {
        if (inSession(requester) || inSession(target)) {
            requester.message("Other player is busy at the moment.")
            refused(requester, target, "already in an assistance")
            return
        }
        if (!AcceptAid.accepts(requester)) {
            requester.message(NEED_ACCEPT_AID)
            refused(requester, target, "requester has Accept Aid off")
            return
        }
        if (!filterAllows(target, requester)) {
            refused(requester, target, "the target's Assist filter (varp 1055 = ${target.getVarp(FILTER_VARP)})")
            return
        }
        val last = requester.attr[REQUESTED]?.second
        if (last != null && requester.world.currentCycle - last < REQUEST_DELAY_TICKS) {
            val seconds = maxOf(1, ((REQUEST_DELAY_TICKS - (requester.world.currentCycle - last)) * 600 + 999) / 1000)
            requester.message("You have only just made an assistance request", ChatMessageType.ASSIST)
            requester.message("You have to wait $seconds ${if (seconds == 1) "second" else "seconds"} before making a new request.", ChatMessageType.ASSIST)
            return
        }
        requester.attr[REQUESTED] = WeakReference(target) to requester.world.currentCycle
        if (capped(target)) {
            refused(requester, target, "the target reached the daily 30,000 xp")
            requester.message("${target.username} is unable to assist at the moment.", ChatMessageType.ASSIST)
            target.message("An assist request has been refused. You can assist again in ${hoursLeft()} ${hours(hoursLeft())}.", ChatMessageType.ASSIST)
            return
        }
        requester.message("Sending assistance request.", ChatMessageType.ASSIST)
        target.message(REQUEST_TEXT, ChatMessageType.ASSIST_REQ, Misc.formatForDisplay(requester.username))
    }

    /** Debug toolkit (AGENTS.md section 3): every refused request is a PROBLEM line the `bug` report and Show-Problems name. */
    private fun refused(
        requester: Player,
        target: Player,
        why: String,
    ) = gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INTERACT) {
        "assist request ${requester.username} -> ${target.username} refused: $why"
    }

    private fun respond(
        assister: Player,
        assisted: Player,
    ) {
        assisted.attr.remove(REQUESTED)
        if (inSession(assister) || inSession(assisted) || !assisted.isOnline) {
            assister.message("Other player is busy at the moment.")
            return
        }
        if (!AcceptAid.accepts(assisted)) {
            assister.message(AcceptAid.REFUSED)
            return
        }
        if (capped(assister)) {
            assister.message("You can assist again in ${hoursLeft()} ${hours(hoursLeft())}.", ChatMessageType.ASSIST)
            return
        }
        assister.message("Sending assistance response.", ChatMessageType.ASSIST)
        start(assisted, assister)
    }

    fun canAssist(
        assister: Player,
        assisted: Player,
        skill: Int,
    ): Boolean = assister.skills.getMaxLevel(skill) >= assisted.skills.getMaxLevel(skill)

    fun start(
        assisted: Player,
        assister: Player,
    ) {
        assisted.attr[ASSISTANT] = WeakReference(assister)
        assisted.attr.remove(POINT)
        assisted.attr[TELEPORT_SEEN] = assisted.lastTeleportCycle
        assisted.attr.remove(BORROWED)
        assisted.timers[RANGE_TIMER] = 1
        assister.attr[ASSISTED] = WeakReference(assisted)

        assisted.message("You are being assisted by ${assister.username}.", ChatMessageType.ASSIST)
        assisted.animate(ANIM)
        assisted.facePawn(assister)
        assisted.setComponentHidden(ICON_INTERFACE, ICON_COMPONENT, false)

        assister.message("You are assisting ${assisted.username}.", ChatMessageType.ASSIST)
        assister.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        assister.setComponentText(INTERFACE, DESCRIPTION_COMPONENT, "The Assist System is available for you to use.")
        assister.setComponentText(INTERFACE, TITLE_COMPONENT, "Assist System XP Display - You are assisting ${assisted.username}")
        var skipped = false
        for (s in SKILLS) {
            val on = canAssist(assister, assisted, s.skill)
            assister.setVarbit(s.varbit, if (on) 1 else 0)
            if (!on) skipped = true
        }
        if (skipped) assister.message(LOWER_SKILL)
        setEarned(assister, earnedToday(assister))
        assister.animate(ANIM)
        assister.graphic(GFX)
        assister.facePawn(assisted)
        assister.setComponentHidden(ICON_INTERFACE, ICON_COMPONENT, false)
        assister.lock = LockState.ASSISTING
    }

    /** A click on an Assist XP display skill: switch it, refused for a skill the assister is not higher in. */
    fun toggle(
        assister: Player,
        component: Int,
    ) {
        val s = SKILLS.firstOrNull { it.component == component } ?: return
        val assisted = assistedBy(assister)
        if (assisted == null) {
            end(assister, "nobody to assist")
            return
        }
        if (!canAssist(assister, assisted, s.skill)) {
            assister.setVarbit(s.varbit, 0)
            assister.message(LOWER_SKILL)
            return
        }
        assister.setVarbit(s.varbit, if (assister.getVarbit(s.varbit) == 0) 1 else 0)
        if (assister.getVarbit(s.varbit) == 0) assisted.attr[BORROWED]?.remove(s.skill)
    }

    private val ENDING = AttributeKey<Boolean>()

    /** Ends the session [player] is in (either side), telling both; [reason] goes to the trace. */
    fun end(
        player: Player,
        reason: String = "ended",
    ) {
        val assister = if (player.attr.has(ASSISTED)) player else player.attr[ASSISTANT]?.get()
        val assisted = if (player.attr.has(ASSISTANT)) player else player.attr[ASSISTED]?.get()
        if (assister != null || assisted != null) {
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INTERACT) {
                "assist ended ($reason): assister=${assister?.username} assisted=${assisted?.username}"
            }
        }
        if (assister != null) {
            if (assister.attr[ENDING] == true) return
            assister.attr[ENDING] = true
            try {
                assister.attr.remove(ASSISTED)
                if (assister.lock == LockState.ASSISTING) assister.lock = LockState.NONE
                if (assister.interfaces.isVisible(INTERFACE)) assister.closeInterface(INTERFACE)
                assister.setComponentHidden(ICON_INTERFACE, ICON_COMPONENT, true)
                assister.message("You have stopped assisting ${assisted?.username ?: "them"}.", ChatMessageType.ASSIST)
            } finally {
                assister.attr.remove(ENDING)
            }
        }
        if (assisted != null) {
            assisted.attr.remove(ASSISTANT)
            assisted.attr.remove(POINT)
            assisted.attr.remove(TELEPORT_SEEN)
            assisted.attr.remove(BORROWED)
            assisted.timers.remove(RANGE_TIMER)
            assisted.setComponentHidden(ICON_INTERFACE, ICON_COMPONENT, true)
            assisted.message("${assister?.username ?: "Your assistant"} has stopped assisting you.", ChatMessageType.ASSIST)
        }
    }

    /** The player's Assist tab filter changed: a session the new filter no longer allows ends (Off: any; Friends: a non-friend). */
    fun filterChanged(player: Player) {
        val other = assistantOf(player) ?: assistedBy(player) ?: return
        if (!filterAllows(player, other)) end(player, "assist filter")
    }

    /** Every tick for an assisted player: a teleport moves the 20-square spot along; walking out of it ends the assistance. */
    fun checkRange(assisted: Player) {
        if (!assisted.attr.has(ASSISTANT)) return
        val assister = assistantOf(assisted)
        if (assister == null) {
            end(assisted, "assister gone")
            return
        }
        // A queue task that owned a lock releases it to NONE when it ends (QueueTaskSet.releaseLockIfUnowned): the assister stays held.
        if (assister.lock == LockState.NONE) assister.lock = LockState.ASSISTING
        if (assisted.lastTeleportCycle > (assisted.attr[TELEPORT_SEEN] ?: -1)) {
            assisted.attr[TELEPORT_SEEN] = assisted.lastTeleportCycle
            assisted.attr[POINT] = assisted.tile
        }
        val anchor = assisted.attr[POINT] ?: assister.tile
        if (squares(anchor, assisted.tile) > RANGE) end(assisted, "more than $RANGE squares away")
    }

    /** Squares between two tiles (the larger axis distance; floors are ignored - a staircase does not end it). */
    fun squares(
        a: Tile,
        b: Tile,
    ): Int = maxOf(Math.abs(a.x - b.x), Math.abs(a.z - b.z))

    /** Called before any attack resolves (combat.plugin.kts): a borrowed level never fights - both sessions end first. */
    fun endForCombat(
        attacker: Pawn,
        target: Pawn,
    ) {
        (attacker as? Player)?.takeIf { it.attr.has(ASSISTANT) || it.attr.has(ASSISTED) }?.let { end(it, "attack") }
        (target as? Player)?.takeIf { it.attr.has(ASSISTANT) || it.attr.has(ASSISTED) }?.let { end(it, "attacked") }
    }
}
