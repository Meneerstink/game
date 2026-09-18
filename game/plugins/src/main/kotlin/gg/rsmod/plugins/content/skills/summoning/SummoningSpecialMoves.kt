package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openJewelleryCraftingInterface
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.api.ext.sendRunEnergy
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarcString
import gg.rsmod.plugins.api.ext.stun
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.poison
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.skills.cooking.CookingData
import kotlin.math.ceil

enum class FamiliarSpecialTarget { INSTANT, NPC, PLAYER, INVENTORY_ITEM, OBJECT }
enum class FamiliarSpecialTrigger { IMMEDIATE, TARGETED, NEXT_ATTACK, UTILITY }

private fun FamiliarSpecialTarget.defaultTrigger(): FamiliarSpecialTrigger = when (this) {
    FamiliarSpecialTarget.INSTANT -> FamiliarSpecialTrigger.IMMEDIATE
    FamiliarSpecialTarget.INVENTORY_ITEM -> FamiliarSpecialTrigger.UTILITY
    FamiliarSpecialTarget.NPC, FamiliarSpecialTarget.PLAYER, FamiliarSpecialTarget.OBJECT -> FamiliarSpecialTrigger.TARGETED
}

data class FamiliarSpecialBinding(
    val scroll: SummoningScrollData,
    val target: FamiliarSpecialTarget,
    val alternativeScrolls: List<SummoningScrollData> = emptyList(),
    val trigger: FamiliarSpecialTrigger = target.defaultTrigger(),
)

val FamiliarSpecialBinding.scrolls: List<SummoningScrollData>
    get() = listOf(scroll) + alternativeScrolls

private data class DirectFamiliarSpecial(
    val maxHit: Double,
    val animation: Int,
    val sourceGraphic: Int = -1,
    val projectile: Int = -1,
    val targetGraphic: Int = -1,
    val hitType: HitType = HitType.MAGIC,
)

/**
 * Revision-667 dispatcher for familiar special moves.
 *
 * R08 correction: a prior session's `bindings` entries each carried a `detailsComponent`/
 * `orbComponent` pair (e.g. 662:77, 747:161) that this session proved fabricated - decoding
 * interface 662/747 with a decoder ported directly from this revision's real client source
 * (`2011scape-client`'s `Component.decode()`, not RuneLite's incompatible generic loader) shows
 * 662 only has components 0-75 and 747 only 0-26, so every one of those ids was out of range and
 * every "bound" special move was dead - wired to a button that doesn't exist in the cache. The
 * only real special-move trigger this cache has is interface 747 component 25 ("Spell, Cast",
 * resize-mode only - opBase="Spell", ops=["Cast"]); 662 has no equivalent. So bindings no longer
 * carry a component id at all - which scroll a cast means is resolved from the player's currently
 * summoned familiar (see [resolveBinding]), and the single shared component 25 is wired once in
 * `familiar.plugin.kts`.
 */
/** Last `events` value pushed to 747:25, so the per-cycle refresh only writes on a real change. */
private val ORB_EVENTS_ATTR = gg.rsmod.game.model.attr.AttributeKey<Int>()

/** Last special-move name pushed to varcstr 204; guards the panel refresh, which has no dirty check. */
private val PANEL_TEXT_ATTR = gg.rsmod.game.model.attr.AttributeKey<String>()

object SummoningSpecialMoves {
    /** Summoning orb overlay, and its permanently-separate "Spell, Cast" special-move button. */
    private const val ORB_INTERFACE = 747
    private const val SPECIAL_MOVE_COMPONENT = 25

    /**
     * Scroll-spam guard and successful-cast presentation from Void
     * `Summoning.castFamiliarSpecial`: a 3-tick `familiar_special_delay`, then player animation
     * 7660, graphic 1316 and `summoning_special_cast` sound 4161.
     */
    internal const val SPECIAL_MOVE_DELAY_TICKS = 3
    internal const val SPECIAL_CAST_ANIMATION = 7660
    internal const val SPECIAL_CAST_GRAPHIC = 1316
    internal const val SPECIAL_CAST_SOUND = 4161

    /**
     * Steel-specific special audio from `RSPS_SUMMONING_SOUNDS_2009_2012_MASTER_QC3`.
     * The resource commit, familiar action and first projectile impact are distinct events.
     */
    internal const val STEEL_OF_LEGENDS_SCROLL_SOUND = 4680
    internal const val STEEL_TITAN_SPECIAL_ATTACK_SOUND = 4611
    internal const val STEEL_TITAN_SPECIAL_IMPACT_SOUND = 4653

    /**
     * QC3's dedicated familiar-special cues. `action` is the familiar's cast/launch/effect
     * event; `impact` is emitted only when the damaging effect reaches its target. Entries with
     * no exact or strong familiar-specific source are intentionally omitted.
     *
     * Abyssal Drain and Explode are also intentionally omitted: their revision-667 graphics or
     * sequences already carry the client-side sounds, so sending another synth would double-play.
     */
    internal data class SpecialSoundCue(
        val action: Int,
        val impact: Int? = null,
        val secondaryAction: Int? = null,
        val secondaryImpact: Int? = null,
    )

    internal val SPECIAL_SOUND_CUES: Map<SummoningScrollData, SpecialSoundCue> = mapOf(
        SummoningScrollData.HOWL_SCROLL to SpecialSoundCue(4145, secondaryAction = 4265),
        SummoningScrollData.DREADFOWL_STRIKE_SCROLL to SpecialSoundCue(4174, impact = 4222),
        SummoningScrollData.EGG_SPAWN_SCROLL to SpecialSoundCue(4132),
        SummoningScrollData.SLIME_SPRAY_SCROLL to SpecialSoundCue(4373, impact = 4384),
        SummoningScrollData.STONY_SHELL_SCROLL to SpecialSoundCue(4392),
        SummoningScrollData.THIEVING_FINGERS_SCROLL to SpecialSoundCue(4390),
        SummoningScrollData.PESTER_SCROLL to SpecialSoundCue(4721),
        SummoningScrollData.ELECTRIC_LASH_SCROLL to SpecialSoundCue(4137, impact = 4261),
        SummoningScrollData.SANDSTORM_SCROLL to SpecialSoundCue(4389),
        SummoningScrollData.GENERATE_COMPOST_SCROLL to SpecialSoundCue(4403, impact = 4406),
        SummoningScrollData.VAMPIRE_TOUCH_SCROLL to SpecialSoundCue(4136),
        SummoningScrollData.INSANE_FEROCITY_SCROLL to SpecialSoundCue(4395),
        SummoningScrollData.MULTICHOP_SCROLL to SpecialSoundCue(4387),
        SummoningScrollData.PETRIFYING_GAZE_SCROLL to SpecialSoundCue(4385),
        SummoningScrollData.TIRELESS_RUN_SCROLL to SpecialSoundCue(4386),
        SummoningScrollData.EVIL_FLAMES_SCROLL to SpecialSoundCue(4290),
        SummoningScrollData.DISSOLVE_SCROLL to SpecialSoundCue(4154, impact = 4133),
        SummoningScrollData.RENDING_SCROLL to SpecialSoundCue(4661),
        SummoningScrollData.DOOMSPHERE_SCROLL to SpecialSoundCue(4193, impact = 4248),
        SummoningScrollData.TESTUDO_SCROLL to SpecialSoundCue(4394),
        SummoningScrollData.ARCTIC_BLAST_SCROLL to SpecialSoundCue(4399),
        SummoningScrollData.CRUSHING_CLAW_SCROLL to SpecialSoundCue(4374),
        SummoningScrollData.MANTIS_STRIKE_SCROLL to SpecialSoundCue(4363),
        SummoningScrollData.INFERNO_SCROLL to SpecialSoundCue(4700, impact = 4712),
        SummoningScrollData.VOLCANIC_STRENGTH_SCROLL to SpecialSoundCue(4619, secondaryAction = 4608, secondaryImpact = 4379),
        SummoningScrollData.TITANS_CONSTITUTION_SCROLL to SpecialSoundCue(4678),
        SummoningScrollData.HEALING_AURA_SCROLL to SpecialSoundCue(4372),
        SummoningScrollData.MAGIC_FOCUS_SCROLL to SpecialSoundCue(4217),
        SummoningScrollData.SPIKE_SHOT_SCROLL to SpecialSoundCue(4402, impact = 4405, secondaryAction = 4300),
        SummoningScrollData.BRONZE_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.IRON_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.STEEL_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.RUNE_BULL_RUSH_SCROLL to SpecialSoundCue(4401, impact = 4400, secondaryAction = 4337),
        SummoningScrollData.POISONOUS_BLAST_SCROLL to SpecialSoundCue(4371, impact = 4380),
        SummoningScrollData.SWAMP_PLAGUE_SCROLL to SpecialSoundCue(4598, impact = 4693),
        SummoningScrollData.BOIL_SCROLL to SpecialSoundCue(4383, impact = 4364),
        SummoningScrollData.DEADLY_CLAW_SCROLL to SpecialSoundCue(4618, impact = 4657),
        SummoningScrollData.EBON_THUNDER_SCROLL to SpecialSoundCue(4639, secondaryAction = 4640),
        SummoningScrollData.WINTER_STORAGE_SCROLL to SpecialSoundCue(4172),
        SummoningScrollData.GOAD_SCROLL to SpecialSoundCue(4645, secondaryAction = 4622),
        SummoningScrollData.AMBUSH_SCROLL to SpecialSoundCue(4617),
        SummoningScrollData.FAMINE_SCROLL to SpecialSoundCue(4701),
        SummoningScrollData.TOAD_BARK_SCROLL to SpecialSoundCue(4368, secondaryAction = 4310),
        SummoningScrollData.FISH_RAIN_SCROLL to SpecialSoundCue(4397),
        SummoningScrollData.DUST_CLOUD_SCROLL to SpecialSoundCue(4367, impact = 4382),
        SummoningScrollData.FRUITFALL_SCROLL to SpecialSoundCue(4396),
        SummoningScrollData.ESSENCE_SHIPMENT_SCROLL to SpecialSoundCue(4621),
        SummoningScrollData.OPHIDIAN_INCUBATION_SCROLL to SpecialSoundCue(4319),
        SummoningScrollData.UNBURDEN_SCROLL to SpecialSoundCue(4388),
        SummoningScrollData.HERBCALL_SCROLL to SpecialSoundCue(4404),
        SummoningScrollData.IMMENSE_HEAT_SCROLL to SpecialSoundCue(4603),
        SummoningScrollData.IRON_WITHIN_SCROLL to SpecialSoundCue(4707),
    )

    private fun playSpecialAction(player: Player, scroll: SummoningScrollData) {
        SPECIAL_SOUND_CUES[scroll]?.let {
            FamiliarAudio.play(player, it.action)
            it.secondaryAction?.let { sound -> FamiliarAudio.play(player, sound) }
        }
    }

    private fun playSpecialImpact(player: Player, scroll: SummoningScrollData) {
        SPECIAL_SOUND_CUES[scroll]?.let {
            it.impact?.let { sound -> FamiliarAudio.play(player, sound) }
            it.secondaryImpact?.let { sound -> FamiliarAudio.play(player, sound) }
        }
    }

    /**
     * Void `FamiliarCombatSpecials`: Abyssal Drain plays `abyssal_drain` anim 7672, gfx 1422 and
     * projectile 1423; Explode plays `chinchompa_explode` anim 7758 and gfx 1364. Their audio is
     * attached in the revision-667 cache itself (openrs2 #1473): seq 7672 frames 0/3 carry sounds
     * 6538(/6537) and 6540, gfx 1364 -> seq 7757 frame 1 carries 7164. The client plays those, so
     * no server-side sound is sent for either special.
     */
    internal const val ABYSSAL_DRAIN_ANIMATION = 7672
    internal const val ABYSSAL_DRAIN_GRAPHIC = 1422
    internal const val ABYSSAL_DRAIN_PROJECTILE = 1423

    /** Pre-6-Nov-2017 Abyssal Drain: "restores up to 5 prayer points" (1:1 prayer-point unit). */
    internal const val ABYSSAL_DRAIN_PRAYER_RESTORE = 5

    /** Titan's Constitution heal and above-maximum allowance: 80 life points (x10 scale) = 8 HP. */
    internal const val TITANS_CONSTITUTION_HEAL = 8
    internal const val EXPLODE_ANIMATION = 7758
    internal const val EXPLODE_GRAPHIC = 1364
    internal val SPECIAL_MOVE_DELAY_TIMER = TimerKey()

    /** 747:25's own baked `events`: op1 ("Cast") and op10. Preserved when a target mask is added. */
    private const val BAKED_EVENTS = 0x402

    /** The child index clientscript 606 allocates for the dynamic special-move buttons. */
    private const val DYNAMIC_CHILD_SLOT = 0

    /** Call to Arms' destination - beside the novice Pest Control lander; see the scroll's case. */
    private const val VOID_OUTPOST_X = 2657
    private const val VOID_OUTPOST_Z = 2639

    /*
     * com.jagex.game.runetek6.config.iftype.TargetMask, from this revision's own client source:
     *
     *   TGT_OBJ 0x01   TGT_NPC 0x02   TGT_LOC 0x04   TGT_PLAYER 0x08
     *   TGT_SELF 0x10  TGT_BUTTON 0x20  TGT_GROUND 0x40
     */
    private const val TARGET_NPC = 0x02
    private const val TARGET_LOC = 0x04
    private const val TARGET_PLAYER = 0x08

    /**
     * Selecting an **inventory item** is `TGT_BUTTON`, not `TGT_OBJ`.
     *
     * This is the fix for the owner's requirement H10 ("Pack yak Winter Storage must enter
     * inventory-item targeting"). An inventory item is a *component slot*, so the entry that turns
     * a click on it into the interface-target packet is added by
     * `InterfaceManager.addMiniMenuOptions`, which requires `targetMask & TGT_BUTTON`. `TGT_OBJ`
     * is the **ground**-item mask, tested in `MiniMenu`'s ground-item scan instead - so with 0x01
     * the button entered target mode and then refused every inventory slot, which is exactly the
     * reported symptom.
     *
     * Confirmed against the cache's own working precedent rather than reasoned about alone: High
     * Alchemy (192:38), Low Alchemy (192:59) and Enchant Jewellery (192:29) are all item-targeted
     * spells in this revision and every one of them bakes `events=0x010000`, i.e. `targetMask=32`.
     */
    private const val TARGET_BUTTON = 0x20

    private const val TARGET_MASK_SHIFT = 11

    /** The follower panel's special-move line; see [refreshPanelText] and [SummoningSpecialMoveText]. */
    private const val SPECIAL_NAME_VARCSTR = 204
    private const val SPECIAL_DESCRIPTION_VARCSTR = 205
    private const val SPECIAL_COST_VARBIT = 4288

    /**
     * varbit 4288 is varp 1175 bits 23..27 - five bits, 32 states. A special-move cost above 31
     * would silently wrap and the follower panel would advertise the wrong price, so
     * [SummoningLedger] fails the boot instead.
     */
    const val MAX_PANEL_COST = 31

    val bindings = listOf(
        FamiliarSpecialBinding(SummoningScrollData.DREADFOWL_STRIKE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.SLIME_SPRAY_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.ELECTRIC_LASH_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.STONY_SHELL_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.INSANE_FEROCITY_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.THIEVING_FINGERS_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.UNBURDEN_SCROLL, FamiliarSpecialTarget.INSTANT),
        // The four Void familiars and the seven "-atrice" familiars, neither of which had any
        // special-move binding at all until 2026-09-07 - eleven familiars whose Special Move
        // button, orb "Cast" entry and own right-click ability option were all dead.
        FamiliarSpecialBinding(SummoningScrollData.CALL_TO_ARMS_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.PETRIFYING_GAZE_SCROLL, FamiliarSpecialTarget.NPC),
        // Owner P0 remainder 2026-09-18: Spirit wolf Howl and Spirit scorpion Venom Shot had no binding (Void donor effects).
        FamiliarSpecialBinding(SummoningScrollData.HOWL_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.VENOM_SHOT_SCROLL, FamiliarSpecialTarget.INSTANT),
        // Object-targeted specials (spell-on-object route, OpLocTHandler): Compost mound, Beaver, Hydra (Void donor effects).
        FamiliarSpecialBinding(SummoningScrollData.GENERATE_COMPOST_SCROLL, FamiliarSpecialTarget.OBJECT),
        FamiliarSpecialBinding(SummoningScrollData.MULTICHOP_SCROLL, FamiliarSpecialTarget.OBJECT),
        FamiliarSpecialBinding(SummoningScrollData.REGROWTH_SCROLL, FamiliarSpecialTarget.OBJECT),
        FamiliarSpecialBinding(SummoningScrollData.RISH_FROM_THE_ASHES_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM),
        FamiliarSpecialBinding(SummoningScrollData.TIRELESS_RUN_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.EVIL_FLAMES_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.DISSOLVE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.RENDING_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.DOOMSPHERE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.ABYSSAL_STEALTH_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.TESTUDO_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.ARCTIC_BLAST_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.CRUSHING_CLAW_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.MANTIS_STRIKE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.INFERNO_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.VOLCANIC_STRENGTH_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.TITANS_CONSTITUTION_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.HEALING_AURA_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.MAGIC_FOCUS_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.SPIKE_SHOT_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(
            SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL,
            FamiliarSpecialTarget.NPC,
            listOf(
                SummoningScrollData.BRONZE_BULL_RUSH_SCROLL,
                SummoningScrollData.IRON_BULL_RUSH_SCROLL,
                SummoningScrollData.STEEL_BULL_RUSH_SCROLL,
                SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL,
                SummoningScrollData.RUNE_BULL_RUSH_SCROLL,
            ),
        ),
        FamiliarSpecialBinding(SummoningScrollData.POISONOUS_BLAST_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.SWAMP_PLAGUE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.BOIL_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.DEADLY_CLAW_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.ACORN_MISSILE_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(
            SummoningScrollData.IRON_WITHIN_SCROLL,
            FamiliarSpecialTarget.NPC,
            trigger = FamiliarSpecialTrigger.NEXT_ATTACK,
        ),
        FamiliarSpecialBinding(SummoningScrollData.SANDSTORM_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.FIREBALL_ASSAULT_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.EBON_THUNDER_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.WINTER_STORAGE_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM),
        FamiliarSpecialBinding(
            SummoningScrollData.STEEL_OF_LEGENDS_SCROLL,
            FamiliarSpecialTarget.NPC,
            trigger = FamiliarSpecialTrigger.NEXT_ATTACK,
        ),
        FamiliarSpecialBinding(SummoningScrollData.PESTER_SCROLL, FamiliarSpecialTarget.NPC),
        /*
         * Goad and Ambush had no binding at all, which left the Spirit graahk and the Spirit kyatt
         * with no special move on either surface despite both carrying fully sourced scroll data.
         *
         * Neither needs an invented damage figure, because the Knowledge Base text describes them
         * as the same thing Pester already is - "Sends your spirit graahk to attack an enemy" and
         * "Calls the kyatt into combat for an instant hit with potential high damage". That is
         * precisely what this dispatcher's own `familiar.attack(target)` does for Pester: the
         * familiar attacks the given target whether or not it is the player's active one, and the
         * damage comes from that familiar's own sourced combat definition rather than from a
         * number chosen here. The Spirit larupia's Rending, the third of the three hunter
         * familiars, was already bound and is unaffected.
         */
        FamiliarSpecialBinding(SummoningScrollData.GOAD_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.AMBUSH_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.FAMINE_SCROLL, FamiliarSpecialTarget.PLAYER),
        FamiliarSpecialBinding(SummoningScrollData.TOAD_BARK_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.ABYSSAL_DRAIN_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.EGG_SPAWN_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.BLOOD_DRAIN_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.FISH_RAIN_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.DUST_CLOUD_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.FRUITFALL_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.ESSENCE_SHIPMENT_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.CHEESE_FEAST_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.VAMPIRE_TOUCH_SCROLL, FamiliarSpecialTarget.NPC),
        FamiliarSpecialBinding(SummoningScrollData.HERBCALL_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.OPHIDIAN_INCUBATION_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM),
        FamiliarSpecialBinding(SummoningScrollData.EXPLODE_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.IMMENSE_HEAT_SCROLL, FamiliarSpecialTarget.INSTANT),
        FamiliarSpecialBinding(SummoningScrollData.SWALLOW_WHOLE_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM),
    )

    /** Animation, source graphic and projectile of a direct combat special, for provenance tests. */
    internal fun directSpecialVisuals(scroll: SummoningScrollData): Triple<Int, Int, Int>? =
        directCombat[scroll]?.let { Triple(it.animation, it.sourceGraphic, it.projectile) }

    private val directCombat = mapOf(
        SummoningScrollData.DREADFOWL_STRIKE_SCROLL to DirectFamiliarSpecial(30.0, 5387, 1523, 1318),
        SummoningScrollData.SLIME_SPRAY_SCROLL to DirectFamiliarSpecial(80.0, 8148, 1385, 1386, 1387, HitType.RANGE),
        SummoningScrollData.ELECTRIC_LASH_SCROLL to DirectFamiliarSpecial(50.0, 7795, 1410, 1411),
        SummoningScrollData.EVIL_FLAMES_SCROLL to DirectFamiliarSpecial(100.0, 8251, 1328, 1330, 1329),
        SummoningScrollData.DISSOLVE_SCROLL to DirectFamiliarSpecial(120.0, 8575, 1361, 1360, 1360),
        SummoningScrollData.RENDING_SCROLL to DirectFamiliarSpecial(120.0, 5229, 1370, 1371, 1372, HitType.RANGE),
        SummoningScrollData.DOOMSPHERE_SCROLL to DirectFamiliarSpecial(78.0, 7974, 1478, 1479, 1480),
        SummoningScrollData.ARCTIC_BLAST_SCROLL to DirectFamiliarSpecial(130.0, 4926, 1405, 1406, 1407),
        SummoningScrollData.CRUSHING_CLAW_SCROLL to DirectFamiliarSpecial(96.0, 8118, 1351, 1352, hitType = HitType.RANGE),
        SummoningScrollData.MANTIS_STRIKE_SCROLL to DirectFamiliarSpecial(100.0, 8071, 1379, 1380, 1381, HitType.RANGE),
        SummoningScrollData.INFERNO_SCROLL to DirectFamiliarSpecial(85.0, 7871, 1394, targetGraphic = 1393),
        SummoningScrollData.SPIKE_SHOT_SCROLL to DirectFamiliarSpecial(170.0, 7787, projectile = 1426, targetGraphic = 1428, hitType = HitType.RANGE),
        SummoningScrollData.POISONOUS_BLAST_SCROLL to DirectFamiliarSpecial(120.0, 8211, projectile = 1508, targetGraphic = 1511),
        SummoningScrollData.SWAMP_PLAGUE_SCROLL to DirectFamiliarSpecial(110.0, 8223, projectile = 1462),
        SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL to DirectFamiliarSpecial(200.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.BRONZE_BULL_RUSH_SCROLL to DirectFamiliarSpecial(80.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.IRON_BULL_RUSH_SCROLL to DirectFamiliarSpecial(100.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.STEEL_BULL_RUSH_SCROLL to DirectFamiliarSpecial(120.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL to DirectFamiliarSpecial(160.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.RUNE_BULL_RUSH_SCROLL to DirectFamiliarSpecial(240.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.EBON_THUNDER_SCROLL to DirectFamiliarSpecial(140.0, 7986, 1492, 1493, 1494),
        // Every max hit in this table is on the x10 ledger unit (FamiliarCombat.dealLedgerHit).
        // RCV-010: Toad Bark 300, Abyssal Drain 95 and Vampyre Touch 120 are Void 2011
        // `FamiliarCombatSpecials` figures; the previous 1152/70/40 were a modern-wiki figure and two
        // reused normal-attack max hits. Toad Bark keeps Barker Toad's verified attack animation 7260;
        // Abyssal Drain's presentation is Void's `abyssal_drain` (see ABYSSAL_DRAIN_ANIMATION);
        // Vampyre Touch keeps Vampyre Bat's verified melee attack animation 4915.
        SummoningScrollData.TOAD_BARK_SCROLL to DirectFamiliarSpecial(300.0, 7260, hitType = HitType.RANGE),
        SummoningScrollData.ABYSSAL_DRAIN_SCROLL to
            DirectFamiliarSpecial(95.0, ABYSSAL_DRAIN_ANIMATION, ABYSSAL_DRAIN_GRAPHIC, ABYSSAL_DRAIN_PROJECTILE),
        SummoningScrollData.VAMPIRE_TOUCH_SCROLL to DirectFamiliarSpecial(120.0, 4915, hitType = HitType.MELEE),
    )

    fun validate() {
        bindings.forEach { binding ->
            binding.scrolls.forEach { scroll ->
                check(scroll.familiars.isNotEmpty())
                check(scroll.specialPoints in 1..Familiar.MAX_SPECIAL_POINTS)
            }
        }
        // every familiar must map to at most one special-move binding or resolveBinding is ambiguous
        val allFamiliars = bindings.flatMap { b -> b.scrolls.flatMap { it.familiars.toList() } }
        check(allFamiliars.distinct().size == allFamiliars.size) {
            "multiple SummoningSpecialMoves.bindings entries claim the same familiar npc id"
        }
    }

    /** The one real special-move trigger (747:25) has no per-scroll id, so resolve by active familiar. */
    fun resolveBinding(player: Player): FamiliarSpecialBinding? {
        val familiar = Familiar.current(player) ?: return null
        return bindings.firstOrNull { b -> b.scrolls.any { familiar.id in it.familiars } }
    }

    /**
     * 2026-09-06: the "Spell, Cast" button (747:25) is baked with `events=0x402`, i.e. op1 and op10
     * and **no target mask at all**. The `on_spell_on_npc`/`on_spell_on_player`/`on_spell_on_item`
     * handlers bound to it could therefore never fire: the client only turns a component into a
     * target selector when its events carry a target mask, and nothing was enabling one. Clicking
     * the button always produced a plain `IF_BUTTON1`, so every targeted special silently behaved
     * like an instant one.
     *
     * The mask lives in `events` bits 11..17 - `ServerActiveProperties.targetMaskFrom(events) =
     * (events >> 11) & 0x7F` in this revision's own client, with `TargetMask.TGT_OBJ = 0x01`,
     * `TGT_NPC = 0x02`, `TGT_PLAYER = 0x08`. Interface 662's own attack component (662:65,
     * `events=0x5000`) decodes to mask 10 = NPC|PLAYER, which is what confirms the encoding.
     *
     * Called whenever the active familiar changes, so the button offers exactly the targets the
     * summoned familiar's own special can actually use, and reverts to the baked value when no
     * familiar is out.
     *
     * ## 2026-09-07: this is also why the follower panel's Special Move button did nothing
     *
     * 747:25 is only the *baked* twin. The special-move buttons the player actually sees on both
     * surfaces are dynamic components created at runtime by the cache's own clientscript 606
     * (`CC_CREATE` under **662:74** and **747:17**, child index 0) and dressed by 608, which gives
     * them `opBase = "<col=00ff00>" + varcstr 204` and a "Cast" target verb.
     *
     * A `CC_CREATE`d component is born with `ServerActiveProperties.DEFAULT` - `events = 0`. In
     * this revision's client, `isOpEnabled(op) = (events >> (op + 1)) & 1` decides whether a click
     * sends anything at all, and `getComponentTargetVerb` returns the verb only when
     * `(events >> 11) & 0x7F` is non-zero. With `events = 0` the panel's button therefore had no
     * clickable op *and* no target verb: pressing it produced no packet whatsoever, which is
     * exactly the reported "Special Move from the main Follower Details GUI does NOTHING".
     *
     * `IF_SETEVENTS` is keyed by (parent hash, child index), so arming the dynamic children means
     * writing the range 0..0 on 662:74 and 747:17 - not -1..-1, which addresses static components.
     */
    /**
     * The `TargetMask` bits the special-move button needs so the client will accept the kind of
     * target this special uses. An INSTANT special takes no target and so needs no mask - it fires
     * from its op1 instead, gated by varc 1436.
     *
     * An NPC-targeted special also accepts a player, because every familiar attack special in the
     * roster is usable in PvP; that is one mask, not two behaviours, and the dispatch resolves
     * which handler runs from the packet the client sends.
     */
    fun targetMaskFor(target: FamiliarSpecialTarget?): Int =
        when (target) {
            FamiliarSpecialTarget.NPC -> TARGET_NPC or TARGET_PLAYER
            FamiliarSpecialTarget.PLAYER -> TARGET_PLAYER
            FamiliarSpecialTarget.INVENTORY_ITEM -> TARGET_BUTTON
            // Compost bins, trees and stumps (castOnObject via the spell-on-object route).
            FamiliarSpecialTarget.OBJECT -> TARGET_LOC
            FamiliarSpecialTarget.INSTANT, null -> 0
        }

    fun refreshOrbButton(
        player: Player,
        force: Boolean = false,
    ) {
        val events = BAKED_EVENTS or (targetMaskFor(resolveBinding(player)?.target) shl TARGET_MASK_SHIFT)
        // Guarded: [Familiar.updateHud] calls this once per cycle, and IfSetEvents has no
        // "unchanged" short-circuit of its own.
        //
        // [force] defeats the guard. The events mask is live client state that a component rebuild
        // silently reverts to its baked value, and the server is never told, so an unchanged mask
        // does not mean the client still has it - see SummoningUi.RESEND_INTERVAL_CYCLES.
        if (!force && player.attr[ORB_EVENTS_ATTR] == events) {
            return
        }
        player.attr[ORB_EVENTS_ATTR] = events
        player.setEvents(
            interfaceId = ORB_INTERFACE,
            component = SPECIAL_MOVE_COMPONENT,
            from = -1,
            to = -1,
            setting = events,
        )
        // The two dynamic buttons. Same events value, addressed at the child index script 606
        // allocates for them.
        player.setEvents(
            interfaceId = SummoningUi.PANEL,
            component = SummoningUi.PANEL_SPECIAL,
            from = DYNAMIC_CHILD_SLOT,
            to = DYNAMIC_CHILD_SLOT,
            setting = events,
        )
        player.setEvents(
            interfaceId = ORB_INTERFACE,
            component = SummoningUi.ORB_SPECIAL,
            from = DYNAMIC_CHILD_SLOT,
            to = DYNAMIC_CHILD_SLOT,
            setting = events,
        )
    }

    /**
     * Fills in the follower panel's special-move line - "Level 52: Tireless Run (8 Special Move
     * points)" plus the description underneath.
     *
     * The client builds that line itself (clientscript 606 -> 659 -> 661) but, for every familiar
     * outside the seven tiered scroll families, it takes the name from **varcstr 204**, the
     * description from **varcstr 205** and the cost from **varbit 4288**; see
     * [SummoningSpecialMoveText] for the full decode. Nothing was writing any of the three, so the
     * panel rendered the literal string "null" for both, and a cost of 0.
     *
     * The pouch's own `param 394` supplies the "Level <n>" prefix client-side, so there is
     * deliberately nothing to send for it.
     */
    fun refreshPanelText(
        player: Player,
        force: Boolean = false,
    ) {
        val binding = resolveBinding(player)
        val familiar = Familiar.current(player)
        val scroll =
            binding?.scrolls?.firstOrNull { data -> familiar != null && familiar.id in data.familiars }
                ?: binding?.scroll
        val text = scroll?.let { SummoningSpecialMoveText[it] }
        val move = text?.move ?: ""
        // 662:74 lists varcstrTriggers=[205], so 205 has to be written last for the redraw to see
        // the new name and cost.
        //
        // [force] defeats the guard for the same reason it does on [refreshOrbButton]: a varc is
        // client state, so an unchanged value on the server is no evidence the client still holds
        // it after a rebuild.
        if (!force && player.attr[PANEL_TEXT_ATTR] == move) {
            return
        }
        player.attr[PANEL_TEXT_ATTR] = move
        player.setVarbit(SPECIAL_COST_VARBIT, scroll?.specialPoints ?: 0)
        player.setVarcString(SPECIAL_NAME_VARCSTR, move)
        player.setVarcString(SPECIAL_DESCRIPTION_VARCSTR, text?.description ?: "")
    }

    /**
     * The familiar npc's own named ability option ("Cure", "Drain", "Ash-blast", ...). These are
     * the same special move the follower panel and the orb cast, invoked from the familiar
     * itself, so they route to the same code rather than to a parallel implementation.
     *
     * The only thing this adds is target resolution: the option carries no target of its own, so
     * a targeted special acts on whatever the owner is already fighting. When there is nothing to
     * act on the player is told so - the one behaviour the owner explicitly rejected was silence.
     */
    fun castFromFamiliarOption(player: Player): Boolean {
        val binding = resolveBinding(player)
        if (binding == null) {
            player.message("Your familiar doesn't have a special move.")
            return false
        }
        return when (binding.target) {
            FamiliarSpecialTarget.INSTANT -> castInstant(player)
            FamiliarSpecialTarget.OBJECT -> {
                player.message("Use the special move on the object itself.")
                false
            }
            FamiliarSpecialTarget.NPC -> {
                val target = player.getCombatTarget()
                when (target) {
                    is Npc -> castOnNpc(player, target)
                    is Player -> castOnPlayer(player, target)
                    else -> {
                        player.message("Your familiar needs a target for that.")
                        false
                    }
                }
            }
            FamiliarSpecialTarget.PLAYER -> {
                val target = player.getCombatTarget() as? Player
                if (target == null) {
                    player.message("Your familiar needs a target for that.")
                    false
                } else {
                    castOnPlayer(player, target)
                }
            }
            FamiliarSpecialTarget.INVENTORY_ITEM -> {
                player.message("Use your familiar's special move on the item you want to use it on.")
                false
            }
        }
    }

    fun castInstant(player: Player): Boolean {
        if (Familiar.current(player) == null) {
            player.message("You need a familiar summoned to use its special move.")
            return false
        }
        val binding = resolveBinding(player)
        if (binding == null) {
            player.message("Your familiar doesn't have a special move.")
            return false
        }
        if (binding.target != FamiliarSpecialTarget.INSTANT) {
            player.message("Your familiar's special move needs a target.")
            return false
        }
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        val changed = when (scroll) {
            SummoningScrollData.VENOM_SHOT_SCROLL -> chargeVenomShot(player, familiar)
            SummoningScrollData.STONY_SHELL_SCROLL ->
                boost(player, Skills.DEFENCE, 4).also { if (it) animateSelf(player, familiar, 8109, 1326) }
            SummoningScrollData.THIEVING_FINGERS_SCROLL ->
                boost(player, Skills.THIEVING, 2).also { if (it) animateSelf(player, familiar, 8020, 1336, 1300) }
            /*
             * The four Void familiars share one scroll and had no binding at all, so the special
             * move button, the orb's "Cast Call to Arms" and the Void torcher's own "Strike"
             * cache option were all dead on them. The effect is the 2011 Knowledge Base's own
             * one-liner, carried verbatim in SummoningSpecialMoveText: "Teleports you to Pest
             * Control landers".
             *
             * The destination is the novice lander's own square: this server already spawns
             * Squire (Novice) at 2657,2637 in `spawns_10537.plugin.kts`, so this lands the player
             * beside a live, populated part of the outpost rather than at a guessed coordinate.
             */
            SummoningScrollData.CALL_TO_ARMS_SCROLL -> {
                // No teleport animation or graphic id for this scroll could be sourced from this
                // cache, so none is played rather than a guessed one - same rule as the familiar
                // call/summon visual, which is still an open blocker.
                player.moveTo(Tile(VOID_OUTPOST_X, VOID_OUTPOST_Z, 0))
                Familiar.call(player)
                true
            }
            SummoningScrollData.UNBURDEN_SCROLL -> {
                val restored = restoreRunEnergy(player)
                if (restored) animateSelf(player, familiar, 7896, 1382)
                restored
            }
            SummoningScrollData.TIRELESS_RUN_SCROLL -> {
                val beforeEnergy = player.runEnergy
                val beforeAgility = player.skills.getCurrentLevel(Skills.AGILITY)
                val boostedAgility = (beforeAgility + 2).coerceAtMost(player.skills.getMaxLevel(Skills.AGILITY) + 2)
                val restoredEnergy = (beforeEnergy + boostedAgility / 2.0).coerceAtMost(100.0)
                if (boostedAgility == beforeAgility && restoredEnergy == beforeEnergy) {
                    player.message("Your Agility and run energy are already fully boosted.")
                    false
                } else {
                    if (boostedAgility != beforeAgility) player.skills.setCurrentLevel(Skills.AGILITY, boostedAgility)
                    player.runEnergy = restoredEnergy
                    player.sendRunEnergy(restoredEnergy.toInt())
                    familiar.animate(8229)
                    familiar.graphic(1521)
                    player.graphic(1300)
                    true
                }
            }
            SummoningScrollData.ABYSSAL_STEALTH_SCROLL -> {
                val agility = boost(player, Skills.AGILITY, 4)
                val thieving = boost(player, Skills.THIEVING, 4)
                (agility || thieving).also { if (it) animateSelf(player, familiar, 7682, 1339, 1302) }
            }
            SummoningScrollData.TESTUDO_SCROLL -> {
                val before = player.skills.getCurrentLevel(Skills.DEFENCE)
                val after = (before + 8).coerceAtMost(player.skills.getMaxLevel(Skills.DEFENCE) + 8)
                if (after == before) {
                    player.message("Your Defence is already fully boosted.")
                    false
                } else {
                    player.skills.setCurrentLevel(Skills.DEFENCE, after)
                    familiar.animate(8288)
                    familiar.graphic(1414)
                    player.graphic(1308)
                    true
                }
            }
            SummoningScrollData.VOLCANIC_STRENGTH_SCROLL ->
                boost(player, Skills.STRENGTH, 9).also { if (it) animateSelf(player, familiar, 8053, 1465) }
            SummoningScrollData.MAGIC_FOCUS_SCROLL ->
                boost(player, Skills.MAGIC, 7).also { if (it) animateSelf(player, familiar, 8308, 1464) }
            SummoningScrollData.HEALING_AURA_SCROLL -> {
                if (player.getCurrentLifepoints() >= player.getMaximumLifepoints()) {
                    player.message("You are already at full life points.")
                    false
                } else {
                    player.heal(ceil(player.getMaximumLifepoints() * 0.15).toInt())
                    animateSelf(player, familiar, 8267, 1356, 1300)
                    true
                }
            }
            SummoningScrollData.TITANS_CONSTITUTION_SCROLL -> {
                val defenceBoost = ceil(player.skills.getMaxLevel(Skills.DEFENCE) * 0.125).toInt()
                boost(player, Skills.DEFENCE, defenceBoost)
                // 80 life points on the historical x10 scale = 8 HP in the 1:1 unit; capValue is the
                // allowance above maximum, so it is scaled the same way (was 80/80 = 10x overheal).
                player.heal(TITANS_CONSTITUTION_HEAL, capValue = TITANS_CONSTITUTION_HEAL)
                when (familiar.id) {
                    SummoningPouchData.FIRE_TITAN.npc -> animateSelf(player, familiar, 7835, 1514, 1307)
                    SummoningPouchData.ICE_TITAN.npc -> animateSelf(player, familiar, 7837, 1512, 1306)
                    SummoningPouchData.MOSS_TITAN.npc -> animateSelf(player, familiar, 7837, 1513, 1308)
                }
                true
            }
            SummoningScrollData.INSANE_FEROCITY_SCROLL -> {
                val attack = 5 + ceil(player.skills.getMaxLevel(Skills.ATTACK) * 0.15).toInt()
                val strength = 5 + ceil(player.skills.getMaxLevel(Skills.STRENGTH) * 0.15).toInt()
                boost(player, Skills.ATTACK, attack)
                boost(player, Skills.STRENGTH, strength)
                drain(player, Skills.RANGED, 0.10)
                drain(player, Skills.MAGIC, 0.10)
                drain(player, Skills.DEFENCE, 0.10)
                animateSelf(player, familiar, 7928, 1397, 1399)
                true
            }
            SummoningScrollData.FIREBALL_ASSAULT_SCROLL ->
                executeAoe(player, familiar, maxTargets = 2, radius = 3, maxHit = 70.0, animation = 8257, targetGraphic = 1329)
            SummoningScrollData.SANDSTORM_SCROLL ->
                executeAoe(player, familiar, maxTargets = 6, radius = 6, maxHit = 200.0, animation = 8517, sourceGraphic = 1350, projectile = 1349)
            // Dust Cloud (instant cast): Void 2011 `FamiliarCombatSpecials` smoke_devil_familiar -
            // up to 6 targets within radius 1, max 80 (x10 ledger). RCV-010 SOURCE_CONFLICT: the
            // previous 791 came from a modern wiki page (post-2011 life-point figure), not 2011 data.
            // Animation/projectile remain Smoke Devil's own real normal-attack ids.
            SummoningScrollData.DUST_CLOUD_SCROLL ->
                executeAoe(
                    player,
                    familiar,
                    maxTargets = 6,
                    radius = 1,
                    maxHit = 80.0,
                    animation = 7816,
                    projectile = 1376,
                    onHit = { playSpecialImpact(player, SummoningScrollData.DUST_CLOUD_SCROLL) },
                )
            SummoningScrollData.EGG_SPAWN_SCROLL -> {
                // Exact per-cast distribution isn't published beyond "up to 8" - uniform 1..8.
                val count = player.world.random(1..8)
                repeat(count) { player.world.spawn(GroundItem(Item(Items.RED_SPIDERS_EGGS), player.tile, player)) }
                player.message("Your spirit spider spawns $count red spiders' eggs.")
                true
            }
            SummoningScrollData.FRUITFALL_SCROLL -> {
                // Papaya always drops; each other fruit type has an independent chance to also
                // drop. Exact per-fruit rates aren't published - 20% each approximates the wiki's
                // stated "average being two fruits" (1 guaranteed + 5 * 20% = ~2 expected).
                val drops = mutableListOf(Items.PAPAYA_FRUIT)
                listOf(Items.BANANA, Items.LEMON, Items.LIME, Items.ORANGE, Items.PINEAPPLE).forEach {
                    if (player.world.randomDouble() < 0.20) drops.add(it)
                }
                drops.forEach { player.world.spawn(GroundItem(Item(it), player.tile, player)) }
                player.message("Your fruit bat forages ${drops.size} pieces of fruit.")
                true
            }
            SummoningScrollData.FISH_RAIN_SCROLL -> {
                // Real mechanic rolls each of 8 surrounding tiles at 19%, first success always a
                // bass, later ones weighted by Fishing level (cod needs 23+) via an unpublished
                // stat_random formula - approximated here with flat weights after the guaranteed
                // bass rather than guessing that formula's exact denominator.
                val level = player.skills.getMaxLevel(Skills.FISHING)
                val drops = mutableListOf<Int>()
                repeat(8) {
                    if (player.world.randomDouble() >= 0.19) return@repeat
                    val fish = when {
                        drops.isEmpty() -> Items.RAW_BASS
                        level >= 23 && player.world.randomDouble() < 0.15 -> Items.RAW_COD
                        player.world.randomDouble() < 0.55 -> Items.RAW_MACKEREL
                        else -> Items.RAW_SHRIMPS
                    }
                    drops.add(fish)
                }
                if (drops.isEmpty()) {
                    player.message("Your ibis doesn't manage to catch anything this time.")
                    false
                } else {
                    drops.forEach { player.world.spawn(GroundItem(Item(it), player.tile, player)) }
                    player.message("Your ibis rains ${drops.size} fish around you.")
                    true
                }
            }
            SummoningScrollData.BLOOD_DRAIN_SCROLL -> {
                // Pre-6-Nov-2017 values (this cache predates that patch, which buffed the
                // threshold 6 -> 60 and the damage 1 -> 10).
                if (player.getCurrentLifepoints() < 6) {
                    player.message("Your leech needs you to have more life points to feed.")
                    false
                } else {
                    for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
                        if (skill == Skills.CONSTITUTION || skill == Skills.PRAYER) continue
                        val current = player.skills.getCurrentLevel(skill)
                        val max = player.skills.getMaxLevel(skill)
                        if (current < max) {
                            val restore = ceil((max - current) * 0.05).toInt().coerceAtLeast(1)
                            player.skills.setCurrentLevel(skill, (current + restore).coerceAtMost(max))
                        }
                    }
                    gg.rsmod.plugins.content.mechanics.poison.Poison.cure(player)
                    player.alterLifepoints(value = -1)
                    familiar.animate(7657)
                    true
                }
            }
            SummoningScrollData.ESSENCE_SHIPMENT_SCROLL -> {
                // Real move ships pure essence only (runecrafting-pouch essence excluded, and the
                // dedicated scroll page names only pure essence - not rune essence).
                val key = BeastOfBurden.ABYSSAL_TITAN_KEY
                val bobContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }
                val inventoryEssence = player.inventory.getItemCount(Items.PURE_ESSENCE)
                val bobEssence = (0 until bobContainer.capacity).sumOf { slot ->
                    bobContainer[slot]?.takeIf { it.id == Items.PURE_ESSENCE }?.amount ?: 0
                }
                val total = inventoryEssence + bobEssence
                if (total <= 0) {
                    player.message("Your titan has no pure essence to ship.")
                    false
                } else {
                    val banked = player.bank.add(Items.PURE_ESSENCE, total, assureFullInsertion = false).completed
                    if (banked <= 0) {
                        player.message("Your bank is too full to store any pure essence.")
                        false
                    } else {
                        var remaining = banked
                        if (inventoryEssence > 0) {
                            val fromInventory = minOf(remaining, inventoryEssence)
                            player.inventory.remove(Items.PURE_ESSENCE, fromInventory, assureFullRemoval = true)
                            remaining -= fromInventory
                        }
                        for (slot in 0 until bobContainer.capacity) {
                            if (remaining <= 0) break
                            val item = bobContainer[slot] ?: continue
                            if (item.id != Items.PURE_ESSENCE) continue
                            val take = minOf(remaining, item.amount)
                            bobContainer[slot] = if (take == item.amount) null else Item(item.id, item.amount - take)
                            remaining -= take
                        }
                        player.message("Your titan ships $banked pure essence to your bank.")
                        true
                    }
                }
            }
            SummoningScrollData.CHEESE_FEAST_SCROLL -> {
                val added = BeastOfBurden.grant(player, Item(Items.CHEESE, 4))
                if (added <= 0) {
                    player.message("Your rat's cheese pouch is already full.")
                    false
                } else {
                    player.message("Your albino rat produces $added cheese for you to retrieve.")
                    true
                }
            }
            SummoningScrollData.HERBCALL_SCROLL -> {
                // Wiki's published table is real Jagex-sourced weights out of 128, unchanged
                // since 2011. Delivery mechanism (inventory vs ground) isn't specified, so this
                // reuses this file's own established "forage special drops on the ground"
                // convention (Fruitfall/Fish Rain) rather than guessing a bank-note mechanic.
                val roll = player.world.random(0..127)
                val herb = when {
                    roll < 26 -> Items.GRIMY_GUAM_NOTED
                    roll < 45 -> Items.GRIMY_MARRENTILL_NOTED
                    roll < 61 -> Items.GRIMY_AVANTOE_NOTED
                    roll < 75 -> Items.GRIMY_TARROMIN_NOTED
                    roll < 87 -> Items.GRIMY_HARRALANDER_NOTED
                    roll < 98 -> Items.GRIMY_RANARR_NOTED
                    roll < 109 -> Items.GRIMY_IRIT_NOTED
                    roll < 115 -> Items.GRIMY_KWUARM_NOTED
                    roll < 120 -> Items.GRIMY_CADANTINE_NOTED
                    roll < 124 -> Items.GRIMY_LANTADYME_NOTED
                    else -> Items.GRIMY_DWARF_WEED
                }
                player.world.spawn(GroundItem(Item(herb), player.tile, player))
                player.message("Your macaw returns with a herb.")
                true
            }
            SummoningScrollData.EXPLODE_SCROLL -> {
                // Void 2011 `FamiliarCombatSpecials` chinchompa_explode: up to 9 targets, radius 6,
                // max 120 (x10 ledger; wiki: higher than the chinchompa's normal max hit of 38).
                // The familiar destroys itself in the process, per the wiki.
                val exploded = executeAoe(player, familiar, maxTargets = 9, radius = 6, maxHit = 120.0, animation = EXPLODE_ANIMATION, sourceGraphic = EXPLODE_GRAPHIC)
                if (exploded) Familiar.dismiss(player)
                exploded
            }
            SummoningScrollData.IMMENSE_HEAT_SCROLL -> {
                // "Acts as a portable furnace" - opens the same real, already-verified jewellery
                // crafting interface (446) furnaces.plugin.kts opens for a gold bar used on a
                // furnace, with that same flow's own precondition (it doesn't check for a mould
                // either - the interface itself hides components for moulds you don't carry).
                if (!player.inventory.contains(Items.GOLD_BAR)) {
                    player.message("You need a gold bar to use this.")
                    false
                } else {
                    player.openJewelleryCraftingInterface()
                    true
                }
            }
            else -> false
        }
        if (!changed) return false
        if (!commitResources(player, scroll)) return false
        playSpecialAction(player, scroll)
        return true
    }

    fun castOnNpc(player: Player, target: Npc): Boolean {
        if (Familiar.current(player) == null) {
            player.message("You need a familiar summoned to use its special move.")
            return false
        }
        val binding = resolveBinding(player)
        if (binding == null) {
            player.message("Your familiar doesn't have a special move.")
            return false
        }
        if (binding.target != FamiliarSpecialTarget.NPC) {
            player.message("Your familiar's special move can't be used on a target like that.")
            return false
        }
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        if (!target.isAlive() || target === familiar || target.tile.height != familiar.tile.height || familiar.tile.getDistance(target.tile) > 16) {
            player.message("Your familiar cannot use that special move on this target.")
            return false
        }
        if (!player.tile.isMulti(player.world) || !target.tile.isMulti(player.world) || !player.world.plugins.canAttack(player, target)) {
            player.message("Your familiar cannot attack that target here.")
            return false
        }
        if (binding.trigger == FamiliarSpecialTrigger.NEXT_ATTACK && FamiliarCombat.hasQueuedNextAttack(familiar)) {
            player.message("Your familiar already has a special attack queued.")
            return false
        }
        val direct = directCombat[scroll]
        val supported =
            binding.trigger == FamiliarSpecialTrigger.NEXT_ATTACK ||
                direct != null ||
                scroll in setOf(
                    SummoningScrollData.BOIL_SCROLL,
                    SummoningScrollData.DEADLY_CLAW_SCROLL,
                    SummoningScrollData.ACORN_MISSILE_SCROLL,
                    SummoningScrollData.PESTER_SCROLL,
                    SummoningScrollData.GOAD_SCROLL,
                    SummoningScrollData.AMBUSH_SCROLL,
                    SummoningScrollData.PETRIFYING_GAZE_SCROLL,
                    SummoningScrollData.HOWL_SCROLL,
                )
        // Validate dispatch coverage before consuming anything. This protects the resource
        // transaction when a new NPC-targeted binding is added before its effect branch exists.
        if (!supported) {
            player.message("Your familiar's special move is not available yet.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        familiar.facePawn(target)
        when {
            binding.trigger == FamiliarSpecialTrigger.NEXT_ATTACK -> {
                check(FamiliarCombat.queueNextAttack(familiar, scroll)) {
                    "next-attack special was not available after its resource commit"
                }
                // The target was explicitly selected for this special. Assign it here so the
                // normal combat loop reaches the attack boundary even when the familiar had no
                // previous combat target; ordinary specials below intentionally do not retarget.
                familiar.attack(target)
            }
            direct != null -> executeDirectCombat(player, familiar, target, scroll, direct)
            scroll == SummoningScrollData.BOIL_SCROLL -> executeBoil(player, familiar, target)
            scroll == SummoningScrollData.HOWL_SCROLL -> executeHowl(player, familiar, target)
            scroll == SummoningScrollData.DEADLY_CLAW_SCROLL -> {
                playSpecialAction(player, scroll)
                val attackAnimation = SummoningCombatDefinitions.getByNpc(familiar.id)?.attackAnimation ?: -1
                executeVolley(
                    familiar,
                    target,
                    3,
                    100.0,
                    HitType.MAGIC,
                    animation = attackAnimation,
                ) { if (it == 0) playSpecialImpact(player, scroll) }
            }
            scroll == SummoningScrollData.ACORN_MISSILE_SCROLL -> {
                executeDirectCombat(player, familiar, target, scroll, DirectFamiliarSpecial(100.0, 7858, projectile = 1362, targetGraphic = 1363))
                executeSplash(player, familiar, target, maxTargets = 9, radius = 1, maxHit = 100.0, projectile = 1362, targetGraphic = 1363)
            }
            // Pester's real effect is just an attack "even without being set as the player's
            // active target" - already exactly what this function's own target parameter (as
            // opposed to the player's normal combat target) plus the unconditional
            // familiar.attack(target) below provide, so there's nothing extra to do here.
            // Goad and Ambush are the same instruction as Pester - send the familiar at that
            // target - so they take the same branch, and for the same reason: the attack itself
            // is the effect, and its damage is the familiar's own.
            scroll == SummoningScrollData.PESTER_SCROLL ||
                scroll == SummoningScrollData.GOAD_SCROLL ||
                scroll == SummoningScrollData.AMBUSH_SCROLL -> {
                playSpecialAction(player, scroll)
                // These three specials are explicitly "send the familiar to attack" moves. That
                // intentional target assignment is kept local; ordinary damaging specials below
                // must not silently replace the familiar's current combat target.
                familiar.attack(target)
            }
            /*
             * Petrifying Gaze, shared by all seven "-atrice" familiars, had no binding at all,
             * which left the seven "Drain" options the cache really puts on those npcs doing
             * nothing. Knowledge Base text (carried verbatim in SummoningSpecialMoveText):
             * "Deals up to 100 damage against an opponent, as well as reducing a combat skill by
             * up to 3 (varies by type of cockatrice)".
             *
             * The damage and the size of the drain are sourced. WHICH combat skill each of the
             * seven drains is NOT: the article only says "varies by type" and publishes no table.
             * Rather than invent seven mappings, all seven drain Defence - the stat petrification
             * plausibly targets - and the choice is recorded here as unsourced so it can be
             * corrected the moment a real per-type table turns up. The animation is the
             * familiar's own real attack animation from SummoningCombatDefinitions, the same
             * convention Toad Bark, Abyssal Drain and Vampire Touch already use for specials with
             * no published animation of their own.
             */
            scroll == SummoningScrollData.PETRIFYING_GAZE_SCROLL -> {
                playSpecialAction(player, scroll)
                val attackAnimation = SummoningCombatDefinitions.getByNpc(familiar.id)?.attackAnimation ?: -1
                if (attackAnimation >= 0) familiar.animate(attackAnimation)
                FamiliarCombat.dealLedgerHit(familiar, target, 100.0, !FamiliarCombat.blockedBySummoningProtection(target), 1, HitType.MAGIC)
                target.stats.decrementCurrentLevel(NpcSkills.DEFENCE, player.world.random(1..3), capped = false)
            }
            else -> return false
        }
        return true
    }

    /**
     * Object-targeted familiar specials (Void donor FamiliarUtilitySpecials / Beaver / Hydra). Every check that can refuse runs
     * before the scroll and points are committed, so a refused cast costs nothing.
     *  - Generate Compost (Compost mound): only an empty compost bin; fills it ready-to-empty with 15 compost, 1-in-10
     *    supercompost. Anim 7775, gfx 1461, projectile 1462, bin gfx 1460.
     *  - Multichop (Beaver): a naturally growing tree; stores 1-3 logs (KB "up to 3") of the tree's tier or any lower tier
     *    (Void) in the beaver's pack. Anim 7722, gfx 1459.
     *  Quantities not given by the 2011 KB (supercompost 1-in-10, the log spread) follow Void or are ADAPTED, labelled so.
     *  - Regrowth (Hydra): the stump of a felled world tree regrows at once. Anim 7945, gfx 1487.
     */
    fun castOnObject(player: Player, obj: gg.rsmod.game.model.entity.GameObject): Boolean {
        if (Familiar.current(player) == null) {
            player.message("You need a familiar summoned to use its special move.")
            return false
        }
        val binding = resolveBinding(player)
        if (binding == null || binding.target != FamiliarSpecialTarget.OBJECT) {
            player.message("Your familiar's special move can't be used on that.")
            return false
        }
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        when (scroll) {
            SummoningScrollData.GENERATE_COMPOST_SCROLL -> {
                val bin = gg.rsmod.plugins.content.skills.farming.data.CompostBin.byCompostBinId(obj.id)
                val varbit = bin?.let { gg.rsmod.plugins.content.skills.farming.logic.VarbitUpdater(it.varbit, player) }
                if (varbit == null || gg.rsmod.plugins.content.skills.farming.data.CompostBinState.forVarbit(varbit.value) !=
                    gg.rsmod.plugins.content.skills.farming.data.CompostBinState.Empty
                ) {
                    player.message("This scroll can only be used on an empty compost bin.")
                    return false
                }
                if (!commitResources(player, scroll)) return false
                playSpecialAction(player, scroll)
                familiar.faceTile(obj.tile)
                familiar.animate(7775)
                familiar.graphic(1461)
                val projectile = familiar.createProjectile(obj.tile, 1462, ProjectileType.MAGIC)
                player.world.spawn(projectile)
                val superCompost = player.world.random(9) == 0
                player.world.queue {
                    wait(((projectile.lifespan + 29) / 30).coerceAtLeast(1))
                    player.world.spawn(gg.rsmod.game.model.TileGraphic(obj.tile, 1460, 0))
                    playSpecialImpact(player, scroll)
                    val full =
                        if (superCompost) {
                            gg.rsmod.plugins.content.skills.farming.data.CompostBinState.EmptyingSuperCompost
                        } else {
                            gg.rsmod.plugins.content.skills.farming.data.CompostBinState.EmptyingCompost
                        }
                    varbit.set(full.varbits.last)
                }
            }
            SummoningScrollData.MULTICHOP_SCROLL -> {
                val tree = gg.rsmod.plugins.content.skills.woodcutting.TreeType.values().firstOrNull { obj.id in it.objectIds }
                if (tree == null) {
                    player.message("Your beaver can only chop naturally growing trees.")
                    return false
                }
                if ((BeastOfBurden.activeContainer(player)?.freeSlotCount ?: 0) <= 0) {
                    player.message("Your beaver's pack is too full to store any more logs.")
                    return false
                }
                val tiers = MULTICHOP_LOGS.filter { (log, level) -> level <= tree.level }.map { it.first }
                val log = if (tiers.isEmpty()) tree.log else tiers[player.world.random(tiers.size - 1)]
                if (!commitResources(player, scroll)) return false
                playSpecialAction(player, scroll)
                familiar.faceTile(obj.tile)
                familiar.animate(7722)
                familiar.graphic(1459)
                // 2011 Knowledge Base: "Cuts up to 3 logs from a nearby tree" (Void stores one; the KB wins). The 1-3 spread is
                // ADAPTED (no source gives the distribution).
                val logs = 1 + player.world.random(2)
                player.world.queue {
                    wait(MULTICHOP_TICKS)
                    if (BeastOfBurden.grant(player, gg.rsmod.game.model.item.Item(log, logs)) > 0) {
                        val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, log).name.lowercase()
                        player.message("Your beaver chops the tree and stashes some $name in its pack.")
                    }
                }
            }
            SummoningScrollData.REGROWTH_SCROLL -> {
                if (gg.rsmod.plugins.content.skills.woodcutting.Woodcutting.felledStumps[obj.tile]?.stump?.id != obj.id) {
                    player.message("Your familiar can only regrow the stumps of felled trees.")
                    return false
                }
                if (!commitResources(player, scroll)) return false
                playSpecialAction(player, scroll)
                familiar.faceTile(obj.tile)
                familiar.animate(7945)
                familiar.graphic(1487)
                gg.rsmod.plugins.content.skills.woodcutting.Woodcutting.regrowStump(player.world, obj)
            }
            else -> {
                player.message("Your familiar's special move is not available yet.")
                return false
            }
        }
        return true
    }

    /** Multichop log tiers, lowest first (Void MULTICHOP_LOGS with the woodcutting levels of their trees). */
    private val MULTICHOP_LOGS =
        listOf(
            Items.LOGS to 1, Items.OAK_LOGS to 15, Items.WILLOW_LOGS to 30,
            Items.MAPLE_LOGS to 45, Items.YEW_LOGS to 60, Items.MAGIC_LOGS to 75,
        )

    /** Void Beaver: CHOP_TICKS = 10 before the log is stored. */
    private const val MULTICHOP_TICKS = 10

    fun castOnPlayer(player: Player, target: Player): Boolean {
        if (Familiar.current(player) == null) {
            player.message("You need a familiar summoned to use its special move.")
            return false
        }
        if (target === player || !target.isOnline) {
            player.message("That is not a valid target.")
            return false
        }
        val binding = resolveBinding(player)
        if (binding == null || binding.target != FamiliarSpecialTarget.PLAYER) {
            player.message("Your familiar's special move cannot target a player.")
            return false
        }
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        if (player.tile.height != target.tile.height || player.tile.getDistance(target.tile) > 16) {
            player.message("That player is too far away.")
            return false
        }
        if (!player.tile.isMulti(player.world) || !target.tile.isMulti(player.world) ||
            !player.world.plugins.canAttack(player, target)
        ) {
            player.message("You cannot attack that player here.")
            return false
        }

        var foodSlot = -1
        for (slot in 0 until target.inventory.capacity) {
            val item = target.inventory[slot] ?: continue
            if (Food.values().any { it.item == item.id }) {
                foodSlot = slot
                break
            }
        }
        if (foodSlot < 0) {
            player.message("Your familiar cannot find any food to consume.")
            return false
        }

        val consumed = target.inventory[foodSlot] ?: return false
        if (!target.inventory.remove(consumed.id, 1, assureFullRemoval = true, beginSlot = foodSlot).hasSucceeded()) {
            return false
        }
        if (!commitResources(player, scroll)) {
            target.inventory.add(consumed.id, 1, assureFullInsertion = true, beginSlot = foodSlot)
            return false
        }
        playSpecialAction(player, scroll)
        familiar.facePawn(target)
        player.message("Your familiar consumes one piece of your opponent's food.")
        return true
    }
    fun castOnInventoryItem(player: Player, slot: Int): Boolean {
        if (Familiar.current(player) == null) {
            player.message("You need a familiar summoned to use its special move.")
            return false
        }
        val binding = resolveBinding(player)
        if (binding == null) {
            player.message("Your familiar doesn't have a special move.")
            return false
        }
        if (binding.target != FamiliarSpecialTarget.INVENTORY_ITEM) {
            player.message("Your familiar's special move can't be used on an item like that.")
            return false
        }
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        val selected = player.inventory[slot] ?: return false
        if (selected.id == binding.scroll.scroll) {
            player.message("Your familiar refuses to bank the scroll powering its special move.")
            return false
        }
        return when (scroll) {
            SummoningScrollData.OPHIDIAN_INCUBATION_SCROLL -> executeOphidianIncubation(player, scroll, slot, selected)
            SummoningScrollData.SWALLOW_WHOLE_SCROLL -> executeSwallowWhole(player, scroll, slot, selected)
            SummoningScrollData.RISH_FROM_THE_ASHES_SCROLL -> executeRiseFromTheAshes(player, familiar, scroll, slot, selected)
            else -> executeWinterStorage(player, familiar, scroll, slot, selected)
        }
    }

    /**
     * Rise from the Ashes, the Phoenix's special - and the effect behind the "Ash-blast" option
     * the cache puts on npc 8575, which had no handler at all until 2026-09-07.
     *
     * Sourced effect (SummoningSpecialMoveText, from the 2011 Knowledge Base): "Target ashes on
     * the ground to cause phoenix to be reborn, healing all of its injuries and damaging adjacent
     * targets. Damage is greater if the phoenix's health was lower before casting."
     *
     * RECONSTRUCTION, disclosed: the article says "ashes on the ground" and this revision has no
     * ground-item target mode on a familiar special, so the ashes are taken from the inventory
     * instead - the player selects them the same way every other item-targeted familiar special
     * selects its item. Everything else follows the article: the heal is total, the damage scales
     * with how much health the phoenix had lost, and it lands on adjacent targets only. No
     * animation or graphic is played, because none is sourced.
     */
    private fun executeRiseFromTheAshes(
        player: Player,
        familiar: Npc,
        scroll: SummoningScrollData,
        slot: Int,
        selected: Item,
    ): Boolean {
        if (selected.id != Items.ASHES) {
            player.message("Your phoenix needs ashes to be reborn from.")
            return false
        }
        val maxLife = familiar.getMaximumLifepoints()
        val missing = (maxLife - familiar.getCurrentLifepoints()).coerceAtLeast(0)
        if (!commitResources(player, scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, scroll)
            return false
        }
        familiar.setCurrentLifepoints(maxLife)
        playSpecialAction(player, scroll)
        // Void 2011 `familiar/Phoenix.kt`: max hit = (max - current life) / 4 on its x10 unit. `missing` is
        // 1:1 real HP, so the ledger value is missing * 10 / 4 (a full-health phoenix can only splat 0).
        executeAoe(player, familiar, maxTargets = 9, radius = 1, maxHit = missing * FamiliarCombat.LEDGER_UNITS_PER_HITPOINT / 4.0, animation = -1)
        return true
    }

    private fun executeWinterStorage(player: Player, familiar: Npc, scroll: SummoningScrollData, slot: Int, selected: Item): Boolean {
        val bankProbe = ItemContainer(player.bank)
        if (!bankProbe.add(selected.id, 1, assureFullInsertion = true).hasSucceeded()) {
            player.message("Your bank is too full to store that item.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, scroll)
            return false
        }
        if (!player.bank.add(selected.id, 1, assureFullInsertion = true).hasSucceeded()) {
            player.inventory.add(selected.id, 1, assureFullInsertion = true, beginSlot = slot)
            refundResources(player, scroll)
            return false
        }
        familiar.graphic(1358)
        playSpecialAction(player, scroll)
        player.message("Your pack yak sends the item to your bank.")
        return true
    }

    // Egg-transform table for Ophidian Incubation. Raven Egg -> Coraxatrice Egg is deliberately
    // excluded: a 16 May 2022 patch note says this pairing was "now correctly" fixed, implying it
    // was wrong/broken before, but the wiki doesn't record what the pre-fix (revision-667-era)
    // mapping actually was - a genuine undocumented value, not guessed. The blue/red/green bird
    // egg mapping below is sourced from bird_nest.plugin.kts's own god-alignment comments
    // (Saradomin/Zamorak/Guthix) cross-referenced with the scroll's own god-themed egg names.
    private val ophidianEggs = mapOf(
        Items.EGG to Items.COCKATRICE_EGG,
        Items.BIRDS_EGG_5077 to Items.SARATRICE_EGG,
        Items.BIRDS_EGG to Items.ZAMATRICE_EGG,
        Items.BIRDS_EGG_5078 to Items.GUTHATRICE_EGG,
        Items.PENGUIN_EGG to Items.PENGATRICE_EGG,
        Items.VULTURE_EGG to Items.VULATRICE_EGG,
    )

    private fun executeOphidianIncubation(player: Player, scroll: SummoningScrollData, slot: Int, selected: Item): Boolean {
        val output = ophidianEggs[selected.id]
        if (output == null) {
            player.message("Your spirit cobra can't incubate that.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, scroll)
            return false
        }
        if (!player.inventory.add(output, 1, assureFullInsertion = true, beginSlot = slot).hasSucceeded()) {
            player.inventory.add(selected.id, 1, assureFullInsertion = true, beginSlot = slot)
            refundResources(player, scroll)
            return false
        }
        playSpecialAction(player, scroll)
        player.message("Your spirit cobra incubates the egg.")
        return true
    }

    // Swallow Whole: "eat a raw fish without having to cook it, gaining the correct number of
    // life points corresponding to the fish eaten, if they have the Cooking level to cook the
    // fish" - reuses this codebase's own real raw->cooked map (CookingData, for the level gate)
    // and its own real cooked-item heal table (Food) rather than a new/guessed table. A 4 Dec
    // 2012 patch note says Swallow Whole "now gives the correct amount of life points", implying
    // an earlier, undocumented wrong value pre-dating that fix - not recoverable from the wiki,
    // so this implements the presumably-intended correct healing rather than guessing the bug.
    private fun executeSwallowWhole(player: Player, scroll: SummoningScrollData, slot: Int, selected: Item): Boolean {
        val cooking = CookingData.values().firstOrNull { it.raw == selected.id }
        val healAmount = cooking?.let { data -> Food.values().firstOrNull { it.item == data.cooked }?.hitpoints }
        if (cooking == null || healAmount == null) {
            player.message("Your bunyip can't swallow that.")
            return false
        }
        if (player.skills.getCurrentLevel(Skills.COOKING) < cooking.levelRequirement) {
            player.message("You need a Cooking level of ${cooking.levelRequirement} to eat that raw.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, scroll)
            return false
        }
        player.heal(healAmount)
        player.message("Your bunyip swallows the fish whole.")
        return true
    }

    private data class ResolvedSpecial(val familiar: Npc, val scroll: SummoningScrollData)

    private fun validateResources(player: Player, binding: FamiliarSpecialBinding): ResolvedSpecial? {
        val familiar = Familiar.current(player) ?: return null
        // binding.scrolls can hold several tiers (e.g. bull rush bronze..rune) sharing one
        // familiar, so the real scroll to consume is whichever tier the player is carrying -
        // not resolvable by familiar ownership alone since every tier shares the same familiar.
        // RCV-010 A5: a worn charged helm supplies its stored scroll when the pack has none (Void cast gate).
        val wornScroll = EnchantedHeadgear.wornScroll(player)
        val scroll = binding.scrolls.firstOrNull { player.inventory.contains(it.scroll) }
            ?: binding.scrolls.firstOrNull { it.scroll == wornScroll }
        if (scroll == null) {
            player.message("You need the matching summoning scroll to use this special move.")
            return null
        }
        if (Familiar.currentSpecialPoints(player) < scroll.specialPoints) {
            player.message("You do not have enough familiar special-move energy.")
            return null
        }
        // The cooldown is armed only by commitResources after the effect-specific validation has
        // succeeded. A refused target/item cast must not leave a stale delay behind.
        if (player.timers.has(SPECIAL_MOVE_DELAY_TIMER)) return null
        return ResolvedSpecial(familiar, scroll)
    }

    private fun commitResources(player: Player, scroll: SummoningScrollData): Boolean {
        gg.rsmod.game.model.AvTrace.log {
            "summoning cast ${scroll.name} cost=${scroll.specialPoints} pointsBefore=${Familiar.currentSpecialPoints(player)} " +
                "scrollsBefore=${player.inventory.getItemCount(scroll.scroll)} summoningLevel=${player.skills.getCurrentLevel(Skills.SUMMONING)} cycle=${player.world.currentCycle}"
        }
        val fromHelm = !player.inventory.contains(scroll.scroll) && EnchantedHeadgear.wornScroll(player) == scroll.scroll
        if (fromHelm) {
            EnchantedHeadgear.spendWornScroll(player)
        } else if (!player.inventory.remove(scroll.scroll, 1, assureFullRemoval = true).hasSucceeded()) {
            return false
        }
        if (!Familiar.consumeSpecialPoints(player, scroll.specialPoints)) {
            player.inventory.add(scroll.scroll, 1, assureFullInsertion = true)
            return false
        }
        player.addXp(Skills.SUMMONING, scroll.useExperience)
        player.animate(SPECIAL_CAST_ANIMATION)
        player.graphic(SPECIAL_CAST_GRAPHIC)
        FamiliarAudio.play(player, SPECIAL_CAST_SOUND)
        if (scroll == SummoningScrollData.STEEL_OF_LEGENDS_SCROLL) {
            FamiliarAudio.play(player, STEEL_OF_LEGENDS_SCROLL_SOUND)
        }
        player.timers[SPECIAL_MOVE_DELAY_TIMER] = SPECIAL_MOVE_DELAY_TICKS
        return true
    }

    private fun refundResources(player: Player, scroll: SummoningScrollData) {
        player.inventory.add(scroll.scroll, 1, assureFullInsertion = true)
        Familiar.restoreSpecialPoints(player, scroll.specialPoints)
    }

    /**
     * Executes a special explicitly queued by a successful NEXT_ATTACK cast. The normal combat
     * loop calls this at the attack boundary and never selects a special as a normal fallback.
     * The queue is consumed by [FamiliarCombat] before this method is entered, so one activation
     * cannot repeat on later cycles.
     */
    internal fun executeNextAttack(player: Player, familiar: Npc, target: Npc, scroll: SummoningScrollData) {
        if (!target.isAlive()) return
        when (scroll) {
            SummoningScrollData.IRON_WITHIN_SCROLL -> {
                playSpecialAction(player, scroll)
                familiar.animate(7954)
                familiar.graphic(1450)
                executeVolley(familiar, target, 3, 230.0, HitType.MELEE) {
                    if (it == 0) playSpecialImpact(player, scroll)
                }
            }
            SummoningScrollData.STEEL_OF_LEGENDS_SCROLL -> {
                // QC3 and the 667 cache keep the launch/impact sounds separate. The four ranged
                // strikes are the next attack itself, not an extra attack after a normal cycle.
                FamiliarAudio.play(player, STEEL_TITAN_SPECIAL_ATTACK_SOUND)
                familiar.animate(8190)
                target.graphic(1449)
                repeat(4) { index ->
                    player.world.spawn(familiar.createProjectile(target, 1445, ProjectileType.ARROW))
                    FamiliarCombat.dealLedgerHit(
                        familiar,
                        target,
                        244.0,
                        !FamiliarCombat.blockedBySummoningProtection(target),
                        index + 1,
                        HitType.RANGE,
                    ) {
                        if (index == 0) FamiliarAudio.play(player, STEEL_TITAN_SPECIAL_IMPACT_SOUND)
                    }
                }
            }
            else -> error("${scroll.name} is not a NEXT_ATTACK special")
        }
    }

    private fun executeDirectCombat(
        player: Player,
        familiar: Npc,
        target: Npc,
        scroll: SummoningScrollData,
        effect: DirectFamiliarSpecial,
    ) {
        playSpecialAction(player, scroll)
        familiar.animate(effect.animation)
        if (effect.sourceGraphic >= 0) familiar.graphic(effect.sourceGraphic)
        if (effect.projectile >= 0) {
            val projectileType = if (effect.hitType == HitType.RANGE) ProjectileType.ARROW else ProjectileType.MAGIC
            player.world.spawn(familiar.createProjectile(target, effect.projectile, projectileType))
        }
        if (effect.targetGraphic >= 0) target.graphic(effect.targetGraphic)
        FamiliarCombat.dealLedgerHit(
            familiar,
            target,
            ledgerMaxHit = effect.maxHit,
            landHit = !FamiliarCombat.blockedBySummoningProtection(target),
            delay = if (effect.projectile >= 0) 2 else 1,
            onHit = { pawnHit ->
                playSpecialImpact(player, scroll)
                pawnHit.hit.addAction {
                    when (scroll) {
                        SummoningScrollData.ELECTRIC_LASH_SCROLL -> target.stun(5)
                        SummoningScrollData.ARCTIC_BLAST_SCROLL -> if (target.getSize() <= 1 && player.world.randomDouble() < 0.20) target.stun(3)
                        SummoningScrollData.MANTIS_STRIKE_SCROLL -> if (target.getSize() <= 1) target.stun(3)
                    SummoningScrollData.SPIKE_SHOT_SCROLL -> target.stun(5)
                    // Void `follower.poison(target, 20/80)` is on its x10 unit; Pawn.poison takes 1:1 damage.
                    SummoningScrollData.POISONOUS_BLAST_SCROLL -> if (player.world.randomDouble() < 0.50) target.poison(FamiliarCombat.ledgerToHitpoints(20.0).toInt())
                    SummoningScrollData.SWAMP_PLAGUE_SCROLL -> target.poison(FamiliarCombat.ledgerToHitpoints(80.0).toInt())
                    SummoningScrollData.BRONZE_BULL_RUSH_SCROLL,
                    SummoningScrollData.IRON_BULL_RUSH_SCROLL,
                    SummoningScrollData.STEEL_BULL_RUSH_SCROLL,
                    SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL,
                    SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL,
                    SummoningScrollData.RUNE_BULL_RUSH_SCROLL,
                    -> if (player.world.randomDouble() < (1.0 / 3.0)) target.stun(5)
                        SummoningScrollData.CRUSHING_CLAW_SCROLL -> drainNpc(target, NpcSkills.DEFENCE, 0.05)
                        SummoningScrollData.DISSOLVE_SCROLL -> drainNpc(target, NpcSkills.ATTACK, 0.10)
                        SummoningScrollData.RENDING_SCROLL -> drainNpc(target, NpcSkills.STRENGTH, 0.10)
                        SummoningScrollData.EVIL_FLAMES_SCROLL -> drainNpc(target, NpcSkills.MAGIC, amount = 1)
                        SummoningScrollData.DOOMSPHERE_SCROLL -> drainNpc(target, NpcSkills.MAGIC, 0.05)
                        // Wiki confirms the drain but not an exact percentage - reuses this file's
                        // established 0.05-0.10 in-house convention for undocumented NPC drains.
                        SummoningScrollData.TOAD_BARK_SCROLL -> drainNpc(target, NpcSkills.DEFENCE, 0.10)
                        SummoningScrollData.ABYSSAL_DRAIN_SCROLL -> {
                            drainNpc(target, NpcSkills.MAGIC, 0.05)
                            // Pre-6-Nov-2017 value (this cache predates that patch, which buffed
                            // 5 -> 50): restores up to 5 prayer points. Prayer points are 1:1, and
                            // restorePrayer's capValue is an allowance ABOVE the maximum, so none is
                            // passed: the old (50, capValue = max) raised Prayer to 2x max (owner saw 198).
                            player.restorePrayer(ABYSSAL_DRAIN_PRAYER_RESTORE)
                        }
                        // "Healing players for 50% of the damage dealt" - wiki's own wording.
                        SummoningScrollData.VAMPIRE_TOUCH_SCROLL -> {
                            val damage = pawnHit.hit.hitmarks.sumOf { it.damage }
                            if (damage > 0) player.heal(ceil(damage * 0.5).toInt())
                        }
                        else -> Unit
                    }
                }
            },
            hitType = effect.hitType,
        )
    }

    /**
     * Spirit wolf - Howl ("Causes NPC foes to flee"): no damage. Void donor FamiliarCombatSpecials: familiar anim 8293
     * (spirit_wolf_howl), source gfx 1334, projectile 1333 (delay 30), and the npc retreats from the familiar only once
     * the projectile reaches it. Here the retreat is: drop its fight and walk [HOWL_FLEE_TILES] tiles straight away from
     * the familiar (Void's Retreat mode flees the same way; the distance is ADAPTED).
     */
    private fun executeHowl(player: Player, familiar: Npc, target: Npc) {
        playSpecialAction(player, SummoningScrollData.HOWL_SCROLL)
        familiar.animate(8293)
        familiar.graphic(1334)
        val projectile = familiar.createProjectile(target, 1333, ProjectileType.MAGIC)
        player.world.spawn(projectile)
        val ticks = ((projectile.lifespan + 29) / 30).coerceAtLeast(1)
        player.world.queue {
            wait(ticks)
            if (!target.isAlive() || !familiar.isSpawned()) return@queue
            playSpecialImpact(player, SummoningScrollData.HOWL_SCROLL)
            val dx = Integer.signum(target.tile.x - familiar.tile.x).let { if (it == 0 && target.tile.z == familiar.tile.z) 1 else it }
            val dz = Integer.signum(target.tile.z - familiar.tile.z)
            gg.rsmod.plugins.content.combat.Combat.reset(target)
            target.resetFacePawn()
            target.walkTo(target.tile.transform(dx * HOWL_FLEE_TILES, dz * HOWL_FLEE_TILES))
        }
    }

    private const val HOWL_FLEE_TILES = 5

    /** Spirit scorpion Venom Shot charge (Void `familiar_venom_shot_charged`); spent by [consumeVenomShot]. */
    val VENOM_SHOT_CHARGED_ATTR = gg.rsmod.game.model.attr.AttributeKey<Boolean>()

    /** Void: poison(target, 60) in its x10 life-point units = 6 here. */
    const val VENOM_SHOT_POISON_DAMAGE = 6

    /**
     * Venom Shot (Void FamiliarBoostSpecials): refuses when already charged; anim 6261 + gfx 1354 on the scorpion, and once
     * the 3-tick wind-up ends a venom bolt 1355 flies to the owner. The owner's next damaging ranged hit then poisons.
     */
    private fun chargeVenomShot(player: Player, familiar: Npc): Boolean {
        if (player.attr[VENOM_SHOT_CHARGED_ATTR] == true) {
            player.message("Your familiar's venom shot is already charged.")
            return false
        }
        familiar.facePawn(player)
        familiar.animate(6261)
        familiar.graphic(1354)
        player.world.queue {
            wait(3)
            if (familiar.isSpawned()) player.world.spawn(familiar.createProjectile(player, 1355, ProjectileType.MAGIC))
        }
        player.attr[VENOM_SHOT_CHARGED_ATTR] = true
        return true
    }

    fun consumeVenomShot(attacker: Player, target: gg.rsmod.game.model.entity.Pawn) {
        if (attacker.attr[VENOM_SHOT_CHARGED_ATTR] != true) return
        attacker.attr.remove(VENOM_SHOT_CHARGED_ATTR)
        gg.rsmod.plugins.content.mechanics.poison.Poison.poison(target, VENOM_SHOT_POISON_DAMAGE)
    }

    private fun executeBoil(player: Player, familiar: Npc, target: Npc) {
        playSpecialAction(player, SummoningScrollData.BOIL_SCROLL)
        familiar.animate(7883)
        familiar.graphic(1373)
        val melee = familiar.tile.getDistance(target.tile) <= 1
        val hitType = if (melee) HitType.MELEE else if (player.world.randomDouble() < 0.50) HitType.RANGE else HitType.MAGIC
        if (!melee) {
            val projectileType = if (hitType == HitType.RANGE) ProjectileType.ARROW else ProjectileType.MAGIC
            player.world.spawn(familiar.createProjectile(target, 1376, projectileType))
            target.graphic(1377)
        }
        FamiliarCombat.dealLedgerHit(
            familiar,
            target,
            240.0,
            !FamiliarCombat.blockedBySummoningProtection(target),
            if (melee) 1 else 2,
            hitType,
        ) { playSpecialImpact(player, SummoningScrollData.BOIL_SCROLL) }
    }

    private fun executeVolley(
        familiar: Npc,
        target: Npc,
        hits: Int,
        maxHit: Double,
        hitType: HitType,
        animation: Int = -1,
        onHit: (Int) -> Unit = {},
    ) {
        if (animation >= 0) familiar.animate(animation)
        repeat(hits) { index ->
            FamiliarCombat.dealLedgerHit(
                familiar,
                target,
                maxHit,
                !FamiliarCombat.blockedBySummoningProtection(target),
                1 + index / 2,
                hitType,
            ) { onHit(index) }
        }
    }

    private fun executeAoe(
        player: Player,
        familiar: Npc,
        maxTargets: Int,
        radius: Int,
        maxHit: Double,
        animation: Int,
        sourceGraphic: Int = -1,
        projectile: Int = -1,
        targetGraphic: Int = -1,
        onHit: (Npc) -> Unit = {},
    ): Boolean {
        val targets = nearbyAttackableNpcs(player, familiar, familiar, radius, maxTargets)
        if (targets.isEmpty()) {
            player.message("There are no valid targets for your familiar's special move.")
            return false
        }
        familiar.animate(animation)
        if (sourceGraphic >= 0) familiar.graphic(sourceGraphic)
        targets.forEach { target ->
            if (projectile >= 0) player.world.spawn(familiar.createProjectile(target, projectile, ProjectileType.MAGIC))
            if (targetGraphic >= 0) target.graphic(targetGraphic)
            FamiliarCombat.dealLedgerHit(
                familiar,
                target,
                maxHit,
                true,
                if (projectile >= 0) 2 else 1,
                HitType.MAGIC,
            ) { onHit(target) }
        }
        return true
    }

    private fun executeSplash(
        player: Player,
        familiar: Npc,
        primary: Npc,
        maxTargets: Int,
        radius: Int,
        maxHit: Double,
        projectile: Int = -1,
        targetGraphic: Int = -1,
    ) {
        nearbyAttackableNpcs(player, familiar, primary, radius, maxTargets, excluded = primary).forEach { target ->
            if (projectile >= 0) player.world.spawn(familiar.createProjectile(target, projectile, ProjectileType.MAGIC))
            if (targetGraphic >= 0) target.graphic(targetGraphic)
            FamiliarCombat.dealLedgerHit(familiar, target, maxHit, true, if (projectile >= 0) 2 else 1, HitType.MAGIC)
        }
    }

    private fun nearbyAttackableNpcs(
        player: Player,
        familiar: Npc,
        center: Npc,
        radius: Int,
        maxTargets: Int,
        excluded: Npc? = null,
    ): List<Npc> {
        val targets = mutableListOf<Npc>()
        player.world.npcs.forEach { npc ->
            if (targets.size >= maxTargets) return@forEach
            if (npc === familiar || npc === excluded || !npc.isAlive() || npc.tile.height != center.tile.height) return@forEach
            if (!npc.tile.isWithinRadius(center.tile, radius)) return@forEach
            if (!player.tile.isMulti(player.world) || !npc.tile.isMulti(player.world)) return@forEach
            if (!player.world.plugins.canAttack(player, npc)) return@forEach
            targets.add(npc)
        }
        return targets
    }

    private fun drainNpc(target: Npc, skill: Int, multiplier: Double = 0.0, amount: Int = 0) {
        val current = target.stats.getCurrentLevel(skill)
        val drain = if (amount > 0) amount else ceil(target.stats.getMaxLevel(skill) * multiplier).toInt()
        target.stats.setCurrentLevel(skill, (current - drain).coerceAtLeast(1))
    }

    private fun boost(player: Player, skill: Int, amount: Int): Boolean {
        val before = player.skills.getCurrentLevel(skill)
        val after = (before + amount).coerceAtMost(player.skills.getMaxLevel(skill) + amount)
        if (after == before) return false
        player.skills.setCurrentLevel(skill, after)
        return true
    }

    private fun drain(player: Player, skill: Int, multiplier: Double) {
        val current = player.skills.getCurrentLevel(skill)
        val amount = ceil(player.skills.getMaxLevel(skill) * multiplier).toInt()
        player.skills.setCurrentLevel(skill, (current - amount).coerceAtLeast(1))
    }

    private fun restoreRunEnergy(player: Player): Boolean {
        if (player.runEnergy >= 100.0) {
            player.message("Your run energy is already full.")
            return false
        }
        player.runEnergy = (player.runEnergy + player.skills.getCurrentLevel(Skills.AGILITY) / 2.0).coerceAtMost(100.0)
        player.sendRunEnergy(player.runEnergy.toInt())
        return true
    }

    private fun animateSelf(player: Player, familiar: Npc, animation: Int, sourceGraphic: Int, ownerGraphic: Int = -1) {
        familiar.animate(animation)
        familiar.graphic(sourceGraphic)
        if (ownerGraphic >= 0) player.graphic(ownerGraphic)
    }
}
