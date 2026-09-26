package gg.rsmod.game.model.attr

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.container.ItemTransaction
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.shop.Shop
import java.lang.ref.WeakReference

/**
 * A decoupled file that holds AttributeKeys that require read-access from our
 * game module. Any attributes that can be stored on the plugin classes themselves,
 * should do so. When storing them in a class, remember the AttributeKey must be
 * a singleton, meaning it should only have a single state.
 *
 * @author Tom <rspsmods@gmail.com>
 */

/**
 * A flag which indicates if the player's account was just created/logged in for
 * the first time.
 */
val NEW_ACCOUNT_ATTR = AttributeKey<Boolean>()

/**
 * R14.25: permanently true once a beginner-protected player has explicitly confirmed they
 * want to initiate PvP. Separate from [gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER]
 * (the time budget) so forfeiting protection early doesn't need to fake-exhaust the timer, and
 * so the reason protection ended (ran out vs. chosen) stays distinguishable.
 */
val PROTECTION_FORFEITED_ATTR = AttributeKey<Boolean>(persistenceKey = "protection_forfeited")

/**
 * Owner answer Q12 (2026-09-14): a chosen respawn tile ([gg.rsmod.game.model.Tile.as30BitInteger]) that
 * [gg.rsmod.game.action.PlayerDeathAction] uses instead of the home arrival tile, e.g. Ferox's paid Enclave respawn.
 */
val RESPAWN_TILE_ATTR = AttributeKey<Int>(persistenceKey = "respawn_tile")

/**
 * R14.24: the [gg.rsmod.game.model.World.currentCycle] of the last real client-originated
 * packet this player sent - real RS clients send an explicit "mouse became idle" event
 * ([gg.rsmod.game.message.impl.EventMouseIdleMessage]) after a period of no input, which is
 * the authentic signal this uses rather than inventing a heuristic. Session-local only
 * (not persisted): AFK state resets on every login, which is correct - the concern is only
 * "is this session's play actually active right now", not something that should survive a
 * disconnect.
 */
val LAST_ACTIVE_CYCLE_ATTR = AttributeKey<Int>()

/**
 * Session-local interrupt installed by a server-owned action which must be cancelled by the
 * player's next deliberate input. Deadman escape countdowns use this shared packet-boundary hook
 * so eating, attacking, opening an interface, clicking an object, or any other action all cancel
 * the pending escape consistently instead of relying on a partial list of plugin handlers.
 */
val PLAYER_ACTION_INTERRUPT_ATTR = AttributeKey<() -> Unit>()

/**
 * A flag which indicates that the player will not take collision into account
 * when walking.
 */
val NO_CLIP_ATTR = AttributeKey<Boolean>()

/**
 * An npc that runs (two tiles a tick) when it paths to its target instead of walking, e.g. the Deadman breach monsters Durial321
 * and I DSCIM YOU (OSRS Wiki: "capable of running after his target, unlike most NPCs").
 */
val NPC_RUNS_ATTR = AttributeKey<Boolean>()

/** Session-local admin ID inspector switch used by the Crown of Helios diagnostics. */
val ID_INSPECTOR_ATTR = AttributeKey<Boolean>()

/**
 * A flag that indicates whether or not this player has protect-item
 * prayer active.
 */
val PROTECT_ITEM_ATTR = AttributeKey<Boolean>()

/**
 * The display mode that the player has submitted as a message.
 */
val DISPLAY_MODE_CHANGE_ATTR = AttributeKey<Int>()

/**
 * The [Pawn] which another pawn is facing.
 */
val FACING_PAWN_ATTR = AttributeKey<WeakReference<Pawn>>()

/**
 * Set on an npc that must keep facing its [FACING_PAWN_ATTR] outside combat even when that pawn is
 * not adjacent (the npc cycle otherwise drops a non-combat face-pawn once the target is more than
 * one tile away). Deadman guards use it to keep watching a skulled intruder they are not allowed to
 * attack (owner 2026-09-18: every 1337 guard faces a skulled player in its safe zone).
 */
val HOLD_FACING_ATTR = AttributeKey<Boolean>()

/**
 * World cycle until which a player frozen by a Deadman guard (Wizguard Ice Barrage) may not pick up
 * or telegrab ground items. OSRS Wiki "Deadman Mode" changelog: "A player frozen by guards cannot
 * pickup or telegrab items." Checked by `PluginRepository.canPickupGroundItem`.
 */
val GUARD_FROZEN_UNTIL_CYCLE_ATTR = AttributeKey<Int>()

/**
 * The wilderness agility stage
 */
val WILDERNESS_AGILITY_STAGE = AttributeKey<Int>()

/**
 * The barbarian agility stage
 */
val BARBARIAN_AGILITY_STAGE = AttributeKey<Int>()

/**
 * The advanced barbarian agility stage
 */
val ADVANCED_BARBARIAN_AGILITY_STAGE = AttributeKey<Int>()

/**
 * The gnome agility stage
 */
val GNOME_AGILITY_STAGE = AttributeKey<Int>()

/**
 * The advanced gnome agility stage
 */
val ADVANCED_GNOME_AGILITY_STAGE = AttributeKey<Int>()

/**
 * An [Npc] that has us as their [FACING_PAWN_ATTR].
 */
val NPC_FACING_US_ATTR = AttributeKey<WeakReference<Npc>>()

/**
 * The current viewed shop.
 */
val CURRENT_SHOP_ATTR = AttributeKey<Shop>()

/**
 * The [Pawn] which another pawn wants to initiate combat with, whether they meet
 * the criteria to attack or not (including being in attack range).
 */
val COMBAT_TARGET_FOCUS_ATTR = AttributeKey<WeakReference<Pawn>>()
val LAST_ENGAGED_COMBAT = AttributeKey<WeakReference<Pawn>>()

/**
 * The aggressor on the [Pawn]
 */
val AGGRESSOR = AttributeKey<WeakReference<Pawn>>()

/**
 * The [Player] that the owner of this attribute currently regards as their
 * PvP aggressor - i.e. the last player to attack them while the aggressor
 * window (see `PvpSkull`) is still open. Used to tell a legitimate
 * retaliation apart from a fresh, unprovoked attack when awarding the PK
 * skull: attacking back the player recorded here is retaliation, attacking
 * anyone else (or attacking after this has expired/cleared) is not.
 */
// The aggressor window is a per-life combat relationship. PlayerDeathAction removes the
// matching timer on death; reset the remembered player at the same boundary so a post-death
// attack cannot be mistaken for retaliation against a stale opponent.
val PVP_AGGRESSOR_ATTR = AttributeKey<WeakReference<Player>>(resetOnDeath = true)

/**
 * The [Pawn] that killed another pawn.
 */
val KILLER_ATTR = AttributeKey<WeakReference<Pawn>>()

/** Optional owner credited for damage dealt by a controlled pawn such as a familiar. */
val DAMAGE_CREDIT_ATTR = AttributeKey<WeakReference<Pawn>>()

/**
 * The last [Pawn] that the owner of this attribute has hit.
 */
val LAST_HIT_ATTR = AttributeKey<WeakReference<Pawn>>()

/**
 * The last [Pawn] who has hit the owner of this attribute.
 */
val LAST_HIT_BY_ATTR = AttributeKey<WeakReference<Pawn>>()

/**
 * The amount of ring of forging charges left.
 */
val RING_OF_FORGING_CHARGES = AttributeKey<Int>(persistenceKey = "ring_of_forging_charges")

/**
 * The amount of "poison ticks" left before the poison wears off.
 */
val POISON_TICKS_LEFT_ATTR = AttributeKey<Int>(persistenceKey = "poison_ticks_left")

/**
 * RCV-010 C2: while set, the pawn cannot walk and is told this reason (e.g. Duel Arena "No Movement").
 * Checked by [gg.rsmod.game.model.entity.Pawn.walkPath]/[gg.rsmod.game.model.entity.Pawn.walkTo]; teleports are unaffected.
 */
val MOVEMENT_RESTRICTION_ATTR = AttributeKey<String>()

/**
 * The number of venom ticks that have already elapsed since a pawn was envenomed. Counts
 * up (unlike poison's countdown) since venom damage escalates the longer it is active -
 * see [gg.rsmod.plugins.content.mechanics.poison.Venom.damageForTick].
 */
val VENOM_TICKS_ELAPSED_ATTR = AttributeKey<Int>(persistenceKey = "venom_ticks_elapsed")

/**
 * True while a pawn's own reflected/recoiled damage-response is being resolved for the hit
 * it just took - a defensive reentrancy guard for
 * [gg.rsmod.plugins.content.mechanics.combatresponse.DamageResponse], on top of the
 * structural guarantee that reflected hits use the raw [gg.rsmod.plugins.api.ext.hit]
 * primitive and therefore never re-enter the dispatcher on their own.
 */
val REFLECTING_DAMAGE_ATTR = AttributeKey<Boolean>()

/**
 * Whether Vengeance is currently primed on this pawn - set by casting the spell, consumed
 * (removed) the next time the pawn takes damage, at which point 75% of that damage is
 * reflected back onto whoever dealt it. Sourced from the OSRS Wiki "Vengeance" page - see
 * [gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance] and RSPS_DECISIONS.md.
 */
val VENGEANCE_ACTIVE_ATTR = AttributeKey<Boolean>(persistenceKey = "vengeance_active")

/**
 * Cumulative damage a ring of recoil (or ring of suffering, once sourced) has reflected for
 * this pawn since it last shattered. Tracked per-player rather than per physical ring -
 * sourced from the OSRS Wiki "Ring of recoil" page ("a newly-equipped ring will have the
 * same number of charges remaining as any other ring the player wears").
 */
val RING_OF_RECOIL_CHARGE_ATTR = AttributeKey<Int>(persistenceKey = "recoil_ring_charge")

/**
 * The amount of antifire potion charges left.
 */
val ANTIFIRE_POTION_CHARGES_ATTR = AttributeKey<Int>(persistenceKey = "antifire_potion_charges", resetOnDeath = true)

/**
 * If full dragonfire immunity is enabled.
 */
val DRAGONFIRE_IMMUNITY_ATTR = AttributeKey<Boolean>(persistenceKey = "dragonfire_immunity", resetOnDeath = true)

/**
 * The Stronhold of Security Floors Completed
 */
val STRONGHOLD_OF_SECURITY = AttributeKey<Int>(persistenceKey = "stronghold_of_security")

/**
 * The command that the player has submitted to the server using the '::' prefix.
 */
val COMMAND_ATTR = AttributeKey<String>()

/**
 * The arguments to the last command that was submitted by the player. This does
 * not include the command itself, if you want the command itself, use the
 * [COMMAND_ATTR] attribute.
 */
val COMMAND_ARGS_ATTR = AttributeKey<Array<String>>()

/**
 * The option that was last selected on any entity message.
 * For example: object action one will set this attribute to [1].
 */
val INTERACTING_OPT_ATTR = AttributeKey<Int>()

/**
 * The slot that was last selected on any entity message.
 */
val INTERACTING_SLOT_ATTR = AttributeKey<Int>()

/**
 * The [GroundItem] that was last clicked on.
 */
val INTERACTING_GROUNDITEM_ATTR = AttributeKey<WeakReference<GroundItem>>()

/**
 * The last [ItemTransaction] to occur when a ground item is picked up
 * from the ground.
 */
val GROUNDITEM_PICKUP_TRANSACTION = AttributeKey<WeakReference<ItemTransaction>>()

/**
 * The [GameObject] that was last clicked on.
 */
val INTERACTING_OBJ_ATTR = AttributeKey<WeakReference<out GameObject>>()

/**
 * The [Npc] that was last clicked on.
 */
val INTERACTING_NPC_ATTR = AttributeKey<WeakReference<Npc>>()

/**
 * The [Player] that was last clicked on.
 */
val INTERACTING_PLAYER_ATTR = AttributeKey<WeakReference<Player>>()

/**
 * The slot of the interacting item in its item container.
 */
val INTERACTING_ITEM_SLOT = AttributeKey<Int>()

/**
 * The id of the interacting item.
 */
val INTERACTING_ITEM_ID = AttributeKey<Int>()

/**
 * The id of the interacting button.
 */
val INTERACTING_BUTTON_ID = AttributeKey<Int>()

/**
 * The item pointer of the interacting item.
 */
val INTERACTING_ITEM = AttributeKey<WeakReference<Item>>()

/**
 * The slot index of any 'secondary' item being interacted with.
 */
val OTHER_ITEM_SLOT_ATTR = AttributeKey<Int>()

/**
 * The slot index of any 'secondary' item being interacted with.
 */
val SWAP_COMPONENT = AttributeKey<Int>()

/**
 * The slot index of any 'secondary' item being interacted with.
 */
val OTHER_SWAP_COMPONENT = AttributeKey<Int>()

/**
 * The item id of any 'secondary' item being interacted with.
 */
val OTHER_ITEM_ID_ATTR = AttributeKey<Int>()

/**
 * The item pointer of any 'secondary' item being interacted with.
 */
val OTHER_ITEM_ATTR = AttributeKey<WeakReference<Item>>()

/**
 * Interacting interface parent id.
 */
val INTERACTING_COMPONENT_HASH = AttributeKey<Int>()

/**
 * Interacting interface parent id.
 */
val INTERACTING_COMPONENT_PARENT = AttributeKey<Int>()

/**
 * Interacting interface child id.
 */
val INTERACTING_COMPONENT_CHILD = AttributeKey<Int>()

/**
 * The skill id of the latest level up.
 */
val LEVEL_UP_SKILL_ID = AttributeKey<Int>()

/**
 * The skill id of the latest experience up.
 */
val EXPERIENCE_UP_SKILL_ID = AttributeKey<Int>()

/**
 * The amount of levels that have incremented in a skill level up.
 */
val LEVEL_UP_INCREMENT = AttributeKey<Int>()

/**
 * The last combat level
 */
val LAST_COMBAT_LEVEL = AttributeKey<Int>()

/**
 * The last total level.
 */
val LAST_TOTAL_LEVEL = AttributeKey<Int>()

/**
 * The previous skill XP of the latest level up.
 */
val LEVEL_UP_OLD_XP = AttributeKey<Double>()

/**
 * The current skill menu opened
 */
val SKILL_MENU = AttributeKey<Int>()

/**
 * The opcode that was last sent on any interface message.
 * For example: opcode 64 was sent on an option in an if3 interface.
 */
val INTERACTING_OPCODE_ATTR = AttributeKey<Int>()

/**
 * The last viewed shop item slot
 * Required for the Item Information buy button
 */
val LAST_VIEWED_SHOP_ITEM_SLOT = AttributeKey<Int>()

/**
 * The last viewed shop item container (free/sold)
 * Required for the Item Information take button
 */
val LAST_VIEWED_SHOP_ITEM_FREE = AttributeKey<Boolean>()

/**
 * Whether the pawn was running or not
 */
val LAST_KNOWN_RUN_STATE = AttributeKey<Int>()

/**
 * The type of weapon the player is holding
 */
val LAST_KNOWN_WEAPON_TYPE = AttributeKey<Int>()

/**
 * The type of bar the player is smithing
 */
val BAR_TYPE = AttributeKey<Int>()

/**
 * Death flag
 */
val DEATH_FLAG = AttributeKey<Boolean>(persistenceKey = "death_flag")

/**
 * Attributes track if player has swapped their items for golden replicas in Priest in Peril Quest
 */
val SWAPPED_GOLDEN_POT = AttributeKey<Boolean>(persistenceKey = "swapped_golden_pot")

val SWAPPED_GOLDEN_CANDLE = AttributeKey<Boolean>(persistenceKey = "swapped_golden_candle")

val SWAPPED_GOLDEN_HAMMER = AttributeKey<Boolean>(persistenceKey = "swapped_golden_hammer")

val SWAPPED_GOLDEN_KEY = AttributeKey<Boolean>(persistenceKey = "swapped_golden_key")

val SWAPPED_GOLDEN_TINDERBOX = AttributeKey<Boolean>(persistenceKey = "swapped_golden_tinderbox")

val SWAPPED_GOLDEN_NEEDLE = AttributeKey<Boolean>(persistenceKey = "swapped_golden_needle")

val SWAPPED_GOLDEN_FEATHER = AttributeKey<Boolean>(persistenceKey = "swapped_golden_feather")

/**
 * Attribute for Rune Essence to purify the River Salve in Priest in Peril Quest
 */
val RUNE_ESSENCE_REMAINING = AttributeKey<Int>(persistenceKey = "rune_essence_remaining")

/**
 * If the player has activated Millie's extra-fine flour
 * operations
 */
val EXRTA_FINE_FLOUR = AttributeKey<Boolean>(persistenceKey = "extra_fine_flour")

/**
 * The last npc that the player interacted with
 * before teleporting to the essence mines
 */
val ESSENCE_MINE_INTERACTED_WITH = AttributeKey<Int>(persistenceKey = "last_loc_ess")

/**
 * The creation date for the account
 * saved in milliseonds
 */
val CREATION_DATE = AttributeKey<Long>(persistenceKey = "creation_date")

/**
 * The date the account last logged out
 * saved in milliseonds
 */
val LAST_LOGOUT_DATE = AttributeKey<Long>(persistenceKey = "last_logout_date")

/**
 * The last farming tick this character handled
 */
val LAST_WORLD_FARMING_TICK = AttributeKey<Int>(persistenceKey = "last_world_farming_tick")

/**
 * The last known action that the player
 * requested from the item box with space bar
 */
val LAST_KNOWN_SPACE_ACTION = AttributeKey<Int>(persistenceKey = "last_space_action")

/**
 * The last known initial item that the player
 * used to request an item box
 *
 * Note: this is used to "reset" the space bar
 * attribute should a "new" item box be produced
 */
val LAST_KNOWN_ITEMBOX_ITEM = AttributeKey<Int>(persistenceKey = "last_item_box")

/**
 * If the player has joined the monastery order or not
 */
val JOINED_MONASTERY = AttributeKey<Boolean>(persistenceKey = "join_monastery")

/**
 * Holiday Items
 */
val SCYTHE = AttributeKey<Boolean>(persistenceKey = "scythe")

/**
 * Attributes for Barbarian Skills
 */
val talkedToOtto = AttributeKey<Boolean>(persistenceKey = "talked_to_otto")
val learnedBarbarianHarpooning = AttributeKey<Boolean>(persistenceKey = "learned_barbarian_harpooning")
val learnedBarbarianFiremaking = AttributeKey<Boolean>(persistenceKey = "learned_barbarian_firemaking")
val learnedBarbarianRod = AttributeKey<Boolean>(persistenceKey = "learned_barbarian_rod")
val learnedBarbarianHerblore = AttributeKey<Boolean>(persistenceKey = "learned_barbarian_herblore")
val completedBarbarianTraining = AttributeKey<Boolean>(persistenceKey = "completed_barbarian_training")

/**
 * Attributes for Vampyre Slayer
 */
val gaveHarlowBeer = AttributeKey<Boolean>(persistenceKey = "gave_harlow_beer")
val killedCountDraynor = AttributeKey<Boolean>(persistenceKey = "killed_count_draynor")

/**
 * Attributes for the Mage Arena I miniquest.
 */
val COMPLETED_MAGE_ARENA = AttributeKey<Boolean>(persistenceKey = "completed_mage_arena")
val prayedAtStatue = AttributeKey<Boolean>(persistenceKey = "prayed_at_statue")
val receivedStaff = AttributeKey<Boolean>(persistenceKey = "received_staff")

/**
 * Attribute to disable lever warning message
 */
val DISABLE_LEVER_WARNING = AttributeKey<Boolean>(persistenceKey = "disable_lever_warning")

/**
 * Attribute for web fatigue
 */
val WEB_FATIGUE = AttributeKey<Int>(persistenceKey = "web_fatigue")

/**
 * Attribute for Drill Demon random event
 */
val CORRECT_EXERCISE = AttributeKey<Int>()
val LAST_KNOWN_POSITION = AttributeKey<Tile>()
val EXERCISE_SCORE = AttributeKey<Int>()

/**
 * Anti-cheat
 */
val BOTTING_SCORE = AttributeKey<Int>(persistenceKey = "botting_score")
val ANTI_CHEAT_EVENT_ACTIVE = AttributeKey<Boolean>(persistenceKey = "anti_cheat_event_active")

/**
 * Placeholder for attributes of type Long when saving and loading player data
 */
val LONG_ATTRIBUTES = AttributeKey<Map<String, Long>>(persistenceKey = "long_attributes")

/**
 * Placeholder for attributes of type Double when saving and loading player data
 */
val DOUBLE_ATTRIBUTES = AttributeKey<Map<String, Double>>(persistenceKey = "double_attributes")

/**
 * Stores the compost state of all patches
 * Format: Map<patch id, compost state id>
 */
val COMPOST_ON_PATCHES = AttributeKey<MutableMap<String, String>>(persistenceKey = "compost_on_patches")

/**
 * Stores the protected patches
 * Format: Map<patch id, compost state id>
 */
val PROTECTED_PATCHES = AttributeKey<MutableList<String>>(persistenceKey = "protected_patches")

/**
 * Stores the amount of lives left for all patches
 * Format: Map<patch id, amount of lives left>
 */
val PATCH_LIVES_LEFT = AttributeKey<MutableMap<String, String>>(persistenceKey = "patch_lives_left")

/**
 * The players current Slayer Master
 */
val SLAYER_MASTER = AttributeKey<Int>(persistenceKey = "slayer_master")

/**
 * The players current SlayerAssignment
 */
val SLAYER_ASSIGNMENT = AttributeKey<String>(persistenceKey = "slayer_assignment")

/**
 * The amount of Slayer monsters left to kill
 */
val SLAYER_AMOUNT = AttributeKey<Int>(persistenceKey = "slayer_amount")

/**
 * The amount of Slayer monsters left to kill
 */
val CONSECUTIVE_SLAYER_TASKS = AttributeKey<Int>(persistenceKey = "consecutive_slayer_tasks")

/**
 * The list of block monsters from slayer assignments
 */
val BLOCKED_TASKS = AttributeKey<MutableList<String>>("blocked_tasks")

/**
 * The amount of Slayer monsters left to kill
 */
val STARTED_SLAYER = AttributeKey<Boolean>(persistenceKey = "started_slayer")

/**
 * The canoe varbit.
 */
val CANOE_VARBIT = AttributeKey<Int>()

/**
 * The amount of loyalty points the player has
 */
val LOYALTY_POINTS = AttributeKey<Int>(persistenceKey = "loyalty_points")

/**
 * The amount of slayer points the player has
 */
val SLAYER_POINTS = AttributeKey<Int>(persistenceKey = "slayer_points")

/**
 * R07.2: the player's current Summoning points (pre-2018-rework mechanic - max pool equals
 * current Summoning level, 1:1, no x10 multiplier; sourced from 2011.rs's own worked example
 * and cross-validated against a same-era 2009scape RSPS implementation). Absent until the
 * player's first summon/login touches it, at which point it defaults to a full pool - see
 * [gg.rsmod.plugins.content.skills.summoning.Familiar].
 */
val SUMMONING_POINTS_ATTR = AttributeKey<Int>(persistenceKey = "summoning_points")

/**
 * R07.7: the npc id of the player's active familiar, persisted so it survives logout (real RS
 * mechanic - familiar's lifetime timer pauses offline and resumes on login, it does not
 * dismiss on logout). See [gg.rsmod.plugins.content.skills.summoning.Familiar.disconnect]/
 * [gg.rsmod.plugins.content.skills.summoning.Familiar.restoreOnLogin].
 */
val FAMILIAR_NPC_ID_ATTR = AttributeKey<Int>(persistenceKey = "familiar_npc_id")

/**
 * Slayer shop ability unlocks
 */
val BROAD_FLETCHING = AttributeKey<Boolean>(persistenceKey = "broad_fletching")
val SLAYER_HELM_CREATION = AttributeKey<Boolean>(persistenceKey = "slayer_helm_creation")
val CRAFT_ROS = AttributeKey<Boolean>(persistenceKey = "craft_ros")
val QUICK_BLOWS = AttributeKey<Boolean>(persistenceKey = "quick_blows")
val AQUANTIES = AttributeKey<Boolean>(persistenceKey = "aquanites")
val ICE_STRYKER_NO_CAPE = AttributeKey<Boolean>(persistenceKey = "ice_stryker_no_cape")

/**
 * The last time a map was built for the player
 */
val LAST_MAP_BUILD_TIME = AttributeKey<Int>(persistenceKey = "last_map_build")

/**
 * The last slot the player has selected for the random event gift interface
 */
val RANDOM_EVENT_GIFT_SLOT = AttributeKey<Int>()

/**
 * The lost city attribute
 */

val HAS_SPAWNED_TREE_SPIRIT = AttributeKey<Int>()

/**
 * A map containing number of times each NPC has been killed by a player
 * Since keys are always strings, we must have our key as a String here too
 */
val NPC_KILL_COUNTS = AttributeKey<MutableMap<String, Int>>(persistenceKey = "npc_kill_counts")

/**
 * An [AttributeKey] containing the name of the player that was added
 */
val ADDED_FRIEND = AttributeKey<String>()

/**
 * The item id most recently picked in the chatbox item search (client RESUME_P_OBJDIALOG).
 */
val OBJ_DIALOG_ITEM_ATTR = AttributeKey<Int>()

/**
 * An [AttributeKey] containing the name of the player that was deleted
 */
val DELETED_FRIEND = AttributeKey<String>()

/**
 * An [AttributeKey] containing the name of the player that was added to the ignore list
 */
val ADDED_IGNORE = AttributeKey<String>()

/**
 * An [AttributeKey] containing the name of the player that was deleted from the ignore list
 */
val DELETED_IGNORE = AttributeKey<String>()

/**
 * The ID of the last song that ended
 */
val LAST_SONG_END = AttributeKey<Int>()

/**
 * Whether the Spear Wall special is active on the player
 */
val SPEAR_WALL = AttributeKey<Boolean>(resetOnDeath = true)

/**
 * Whether the Hamstring special is active on the player
 */
val HAMSTRING = AttributeKey<Boolean>(resetOnDeath = true)

/**
 *  How much bleed damage left from Phantom Strike special
 */
val PHANTOM_STRIKE_BLEED = AttributeKey<Int>(resetOnDeath = true)

/**
 * A persisted mirror of [Player.skullIcon], kept in sync explicitly by
 * [gg.rsmod.game.service.serializer.json.JsonPlayerSerializer] on save/load.
 * The raw field remains the single source of truth during live gameplay (it is
 * what [gg.rsmod.game.model.entity.Player]'s update-block transmission and the
 * skull-icon helper extensions read/write); this attribute exists only so that
 * value survives logout/reconnect without changing the save-file schema.
 */
val SKULL_ICON_ATTR = AttributeKey<Int>(persistenceKey = "skull_icon")

/**
 * Legacy (pre-2026-09-26) Death's Domain deadline. Death's Office now keeps items without a time limit (OSRS Wiki "Death's
 * Office"); the key is only read once on login to migrate an old save and is then removed.
 */
val DEATH_RECOVERY_EXPIRY_ATTR = AttributeKey<Long>(persistenceKey = "death_recovery_expiry")

/**
 * Legacy (pre-2026-09-26) flat reclaim fee of the whole Death's Domain batch. Fees are now per item (OSRS). Read once on login
 * to migrate an old save (a batch that was free stays free) and then removed.
 */
val DEATH_RECOVERY_FEE_ATTR = AttributeKey<Int>(persistenceKey = "death_recovery_fee")

/** The tile of the player's gravestone (30-bit hash); absent when there is no gravestone. */
val GRAVESTONE_TILE_ATTR = AttributeKey<Int>(persistenceKey = "gravestone_tile")

/** Game ticks left before the gravestone collapses (OSRS: 15 minutes = 1500 ticks, counted only while active). */
val GRAVESTONE_TICKS_ATTR = AttributeKey<Int>(persistenceKey = "gravestone_ticks")

/** True once the gravestone's fee has been paid ("Unlock"); every item in it is then free to take. */
val GRAVESTONE_UNLOCKED_ATTR = AttributeKey<Boolean>(persistenceKey = "gravestone_unlocked")

/** True when the player bought the Angel of Death gravestone from Death (OSRS: 200,000 coins, cosmetic). */
val GRAVESTONE_ANGEL_ATTR = AttributeKey<Boolean>(persistenceKey = "gravestone_angel")

/** Owner 2026-09-26 (RS 2009 gravestones): the current gravestone was blessed / repaired - once each per gravestone, reset for a new one. */
val GRAVESTONE_BLESSED_ATTR = AttributeKey<Boolean>(persistenceKey = "gravestone_blessed")

val GRAVESTONE_REPAIRED_ATTR = AttributeKey<Boolean>(persistenceKey = "gravestone_repaired")

/** Coins in the player's Death's Coffer (OSRS: sacrificed items at 105% of their value; pays reclamation fees only). */
val DEATH_COFFER_ATTR = AttributeKey<Int>(persistenceKey = "death_coffer")

/** Death's first-death tutorial progress (bit flags, see the plugin's DeathTutorial). */
val DEATH_TUTORIAL_ATTR = AttributeKey<Int>(persistenceKey = "death_tutorial")

/** Where Death's Office's exit portal returns the player (30-bit tile hash): the entrance used, or the respawn point. */
val DEATHS_OFFICE_RETURN_ATTR = AttributeKey<Int>(persistenceKey = "deaths_office_return")

/**
 * Transient (non-persisted) guard set for the duration of death-loot
 * resolution. Defends against generating PvP ground loot or PvM recovery state
 * more than once for the same death, in addition to the death-flow's own
 * [DEATH_FLAG] re-entrancy guard. `resetOnDeath = true` means
 * [gg.rsmod.game.action.PlayerDeathAction] itself clears this flag near the
 * end of every `death()` call (via its existing `attr.removeIf { it.resetOnDeath }`
 * sweep), so it is armed only for the duration of the death it was set for and
 * is always clear again before the next death can begin.
 */
// Audit X-11: persisted, so a force-logout or crash-save during the death sequence can't make the
// replayed death on login resolve the victim's items a second time.
val DEATH_LOOT_RESOLVED_ATTR = AttributeKey<Boolean>(persistenceKey = "death_loot_resolved", resetOnDeath = true)

/**
 * Lunar Disruption Shield: while true, the next damaging hit from another player is nullified
 * and the flag is consumed. Not persisted (the spell wears off on logout in RS as well).
 */
val DISRUPTION_SHIELD_ATTR = AttributeKey<Boolean>()

/**
 * Lunar Magic Imbue window: while set, combination runecrafting needs no talisman.
 */
val MAGIC_IMBUE_ATTR = AttributeKey<Boolean>()

/**
 * Standard-book Charge spell: while true the three god spells hit up to 30 instead of 20 when the
 * matching god cape is worn. Cleared when [gg.rsmod.game.model.timer.GOD_SPELL_CHARGE_TIMER] ends.
 */
val GOD_SPELL_CHARGE_ATTR = AttributeKey<Boolean>()

/**
 * Set once this character's persisted lifepoints/prayer-points have been checked against the
 * legacy x10 storage unit and normalized to the server's 1:1 unit (see `on_login` in
 * `runetek5.plugin.kts`). Before this flag existed, that check ran on every single login and
 * inferred "this still looks like a x10 value" purely from magnitude (value greater than the
 * real max and divisible by ten) - a heuristic that can never fire again once a value is
 * genuinely 1:1 (current can never exceed max), so it is safe to gate behind a one-time flag
 * rather than re-run indefinitely for the character's entire lifetime.
 */
val LIFEPOINT_SCALE_MIGRATED_ATTR = AttributeKey<Boolean>(persistenceKey = "lifepoint_scale_migrated")
