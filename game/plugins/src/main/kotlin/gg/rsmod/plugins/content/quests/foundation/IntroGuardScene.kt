package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.message.impl.MusicEffectMessage
import gg.rsmod.game.message.impl.OpLoc1Message
import gg.rsmod.game.message.impl.OpPlayer2Message
import gg.rsmod.game.message.impl.OpPlayerTMessage
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DEATH_TUTORIAL_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.cameraLookAt
import gg.rsmod.plugins.api.ext.closeFullscreenInterface
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.moveCameraTo
import gg.rsmod.plugins.api.ext.openFullscreenInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.playSong
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.setVarc
import gg.rsmod.plugins.api.ext.switchSpellbook
import gg.rsmod.plugins.content.cmd.conformance.Bots
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.items.osrs.StaffOfTheDead
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import gg.rsmod.plugins.content.mechanics.pvp.MirroredInstances
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

/**
 * The opening of the new-player introduction: a real Deadman pk escape (owner 2026-10-08, spec
 * agent-handoffs\2026-10-08-intro-guard-scene-SPEC.txt) - the game's only Deadman explanation, shown, not told.
 *
 * Two actor players fight through the REAL game: a skulled hybrid pker (Elder chaos robes (or), toxic staff of the dead,
 * two loot keys, Soul Split and the leeches, pack yak) and a rich PvMer (red risk skull, no keys, deflects, geyser titan),
 * in a private copy of the Wilderness north of Edgeville down to the Grand Exchange. Every action is a real client input
 * through the real handlers ([Bots]: spell on player, wield, attack, special bar, curse buttons, eat, object options), so
 * every animation, graphic, sound, leech, freeze (32 ticks + 5 immunity), food delay, familiar assist, guard and death is
 * the game's own:
 *  - Wilderness: Ice Barrage freezes him; Zaryte crossbow (one forced Armour Piercing proc, the rest real rolls), an
 *    Armadyl godsword (or) spec into Deflect Melee, back to mage and barrages; he eats and switches deflects (a tick or two
 *    late, as a player), his titan piles on the pker. Both have auto-retaliate off; the pker drinks super restores and
 *    super energies and leeches energy.
 *  - The freeze runs out; he runs over the Wilderness ditch (the real jump), the pker jumps after him and refreezes him
 *    with Ice Blitz; a second AGS spec catches him late on his deflect, he barrages back, bolts, a barrage that meets his
 *    freeze immunity, and he runs through the underwall tunnel into the Grand Exchange, the pker on his heels.
 *  - The pker follows him in: the real city guards and Wizguard react to his skull and kill him at once - a real Deadman
 *    guard death, his loot on the ground. The PvMer walks up to it and laughs, his titan beside him.
 * The new player watches (hidden, locked) with the scene camera; nothing is put on or taken from his account. The actors
 * are never saved ([Player.neverSave]) and leave with the copy. The copy is multi-combat (the titan assists only there).
 */
object IntroGuardScene {

    /** Set on the watching player while the scene runs. */
    val ACTIVE = AttributeKey<Boolean>()
    private val MAP = AttributeKey<InstancedMap>()
    private val CAST = AttributeKey<Cast>()

    private class Cast(val pker: Client, val escaper: Client)

    /** 16 x 16 map chunks (128 x 128 tiles) from (3072, 3456): the Wilderness north of Edgeville down to the Exchange. */
    private const val BASE_X = 3072
    private const val BASE_Z = 3456
    private const val CHUNKS = 16

    // ---- Real tiles (translated into the copy) ----------------------------------------------------------------------
    // All east of the river that runs from (3109, 3552) down to the ditch at x 3128-3133: west of it, or with it between
    // him and the ditch, his ditch click walked him north round the river's end first (owner 2026-10-08 "walks back").
    // Straight south from here is the ditch (segments x 3136-3156) and right below its landing the tunnel (3139, 3516).
    private val PKER_START = Tile(3141, 3555)
    private val ESCAPER_START = Tile(3140, 3549)
    /** The PvMer's first run south, where the barrage catches him (Wilderness level 3). */
    private val ESCAPE_ROUTE = listOf(Tile(3142, 3543))
    private val DITCH_Z = 3521
    private val TUNNEL_STAND = Tile(3138, 3516)
    private val TUNNEL_OUTSIDE = Tile(3139, 3516)
    /** Clear of the three trees by the tunnel's inner end (3147,3513 / 3154,3514 / 3143,3509, map square 12598). */
    private val ESCAPER_WATCH = Tile(3146, 3516)
    /** Where the world map opens afterwards: the border at the tunnel. */
    val BORDER = Tile(3139, 3512)
    /**
     * The INSIDE view's centre never lies north of this row: the camera stands 6 tiles north of it, so a centre on the tunnel's
     * inner end (3143,3514) put the camera outside the north wall and the guards killed the pker behind it (run 2026-10-09 00:47).
     */
    private const val INSIDE_MAX_Z = 3512

    // ---- Kits (owner's picks), slot -> item --------------------------------------------------------------------------
    private val PKER_WORN =
        mapOf(
            0 to Items.ELDER_CHAOS_HOOD_OR, 1 to Items.IMBUED_GUTHIX_CAPE, 2 to Items.AMULET_OF_FURY, 3 to Items.TOXIC_STAFF_OF_THE_DEAD,
            4 to Items.ELDER_CHAOS_TOP_OR, 5 to Items.MAGES_BOOK, 7 to Items.ELDER_CHAOS_ROBE_OR, 9 to Items.GLOVES_7458,
            10 to Items.WIZARD_BOOTS, 12 to Items.SEERS_RING_I,
        )
    private val PKER_AMMO = Item(Items.DIAMOND_DRAGON_BOLTS_E, 60)
    private val PKER_PACK =
        listOf(
            Item(Items.ZARYTE_CROSSBOW), Item(Items.DIZANAS_QUIVER), Item(Items.ARMADYL_CHAPS), Item(Items.ARMADYL_GODSWORD_OR),
            Item(Items.INFERNAL_CAPE), Item(Items.DEATH_RUNE, 500), Item(Items.BLOOD_RUNE, 500), Item(Items.WATER_RUNE, 1000),
            Item(Items.VARROCK_TELEPORT, 1), Item(Items.LOOT_KEY), Item(Items.LOOT_KEY_23697), Item(Items.PACK_YAK_POUCH),
            // Owner 2026-10-08: super restores for his prayer, a super energy for his run (Leech Energy too).
            Item(Items.SUPER_RESTORE_4), Item(Items.SUPER_RESTORE_4), Item(Items.SUPER_ENERGY_4),
            // Owner 2026-10-08: the Voidwaker spec after the second freeze; one Morrigan's javelin thrown in the chase.
            Item(Items.VOIDWAKER), Item(Items.MORRIGANS_JAVELIN, 5),
        ) + List(6) { Item(Items.SHARK) } + List(4) { Item(Items.COOKED_KARAMBWAN) }
    private val ESCAPER_WORN =
        mapOf(
            0 to Items.NEITIZNOT_FACEGUARD, 1 to Items.IMBUED_SARADOMIN_MAX_CAPE, 2 to Items.AMULET_OF_TORTURE, 3 to Items.ANCIENT_STAFF,
            4 to Items.KARILS_TOP, 5 to Items.ELIDINIS_WARD_F, 7 to Items.VERACS_PLATESKIRT, 9 to Items.FEROCIOUS_GLOVES,
            10 to Items.PRIMORDIAL_BOOTS,
        )
    private val ESCAPER_PACK =
        listOf(Item(Items.GEYSER_TITAN_POUCH), Item(Items.DEATH_RUNE, 100), Item(Items.BLOOD_RUNE, 100), Item(Items.WATER_RUNE, 200)) +
            List(12) { Item(Items.SHARK) } + List(8) { Item(Items.COOKED_KARAMBWAN) }

    // ---- Real inputs --------------------------------------------------------------------------------------------------
    private const val ANCIENT_BOOK = 193
    private const val ICE_BARRAGE = 23 // 193:23 (SpellbookData)
    private const val ICE_BLITZ = 21 // 193:21
    private const val PRAYER_BOOK = 271
    private const val PRAYER_COMPONENT = 8
    private const val SPECIAL_BAR_PARENT = 884
    private const val SPECIAL_BAR_COMPONENT = 4

    /** Curse slots on 271:8 (AncientCurse). */
    private const val DEFLECT_MAGIC = 7
    private const val DEFLECT_MISSILES = 8
    private const val DEFLECT_MELEE = 9
    private val PKER_CURSES = listOf(18, 10, 11, 12, 14, 15, 16) // Soul Split, Leech Attack / Ranged / Magic / Strength / Energy / Special Attack

    private const val SCENE_TRACK_INDEX = 12 // music "Attack 3" (owner 2026-10-08; enum 1351 / 1345, music_by_region.yaml attack_3)
    private const val MAP_TICKS = 17 // world map open ~10 s (owner)
    private const val LAUGH = 861 // Emote LAUGH
    private val DITCH_IDS = setOf(1440, 1441, 1442, 1443, 1444, 23271)
    private const val TUNNEL_OUT = 9311
    private const val TOXIC_STAFF_UNCHARGED = 22761
    private const val AUTO_RETALIATE_VARP = 172
    private const val AUTO_RETALIATE_COMPONENT = 15
    private const val RUN_VARP = 173
    /** Ticks the escaper eats and catches his breath after the ditch before he clicks the tunnel. */
    private const val LANDING_PAUSE = 5
    private const val AGS_ENERGY = 50
    private const val RUN_ORB = 750
    private const val RUN_ORB_COMPONENT = 1
    /** "Call familiar" on the follower details tab (familiar.plugin.kts on_button(662, 49)). */
    private const val FOLLOWER_TAB = 662
    private const val CALL_FAMILIAR = 49

    fun isActive(player: Player): Boolean = player.attr[ACTIVE] == true

    // ---- Set up and clean up ------------------------------------------------------------------------------------------

    private fun setUp(player: Player): Boolean {
        val world = player.world
        val chunks = InstancedChunkSet.Builder()
        // Every plane, as the Fight Caves copy does (bridge decks and wall walks are drawn from plane 1).
        for (x in 0 until CHUNKS) {
            for (z in 0 until CHUNKS) {
                for (height in 0..3) chunks.set(x, z, height, 0, Tile(BASE_X + x * 8, BASE_Z + z * 8, height))
            }
        }
        val config =
            InstancedMapConfiguration.Builder()
                .setOwner(player.uid)
                .setExitTile(TutorIntro.endTile())
                .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
                .build()
        val map = world.instanceAllocator.allocate(world, chunks.build(), config) ?: return false
        MirroredInstances.register(map)
        regionsOf(map).forEach { world.plugins.addMultiCombatRegion(it) }
        player.attr[MAP] = map
        player.attr[ACTIVE] = true
        val at = { real: Tile -> Tile(map.area.bottomLeftX + real.x - BASE_X, map.area.bottomLeftZ + real.z - BASE_Z, 0) }
        val cast =
            runCatching {
                val pker = actor(player, at(PKER_START), "Deadman Pker", PKER_WORN, PKER_PACK, ammo = PKER_AMMO)
                // A pker's staff carries Zulrah's scales: a bare one turned uncharged on his first cast and lost its magic
                // attack bonus (19:13 run, wpn 22763 -> 22761), so his Ice Blitz splashed more.
                pker.equipment[3] = StaffOfTheDead.withScales(Item(Items.TOXIC_STAFF_OF_THE_DEAD), StaffOfTheDead.MAX_SCALES)
                val escaper = actor(player, at(ESCAPER_START), "Rich PvMer", ESCAPER_WORN, ESCAPER_PACK)
                pker.switchSpellbook(Spellbook.ANCIENT)
                // Owner: a leech on every landed hit (scene-only; real players keep the 45 s cooldown and the roll).
                pker.attr[AncientCurses.SCENE_ALWAYS_PROC] = true
                // Owner 2026-10-10: no leech on the Voidwaker and godsword specs and the javelin throws - its animation hid them.
                pker.attr[AncientCurses.SCENE_NO_LEECH_WEAPONS] = setOf(Items.VOIDWAKER, Items.ARMADYL_GODSWORD_OR, Items.MORRIGANS_JAVELIN)
                // Owner 2026-10-08: the guards kill him fast in the Exchange - the real sword guard + Wizguard pick (the crossbow
                // guard's wiki Ranged 1 missed almost every shot: a 54 s last stand in the 19:52 run).
                pker.attr[CityGuards.SCENE_COMBINATION] = listOf(CityGuards.Kind.MELEE, CityGuards.Kind.MAGE)
                escaper.switchSpellbook(Spellbook.ANCIENT)
                Cast(pker, escaper)
            }.getOrNull() ?: return true.also { cleanUp(player) }
        player.attr[CAST] = cast
        // Owner: "hide it all" - only the scene, the Deadman box and the Tutor's lines (client RspsCinematic). The black
        // curtain stays up under the opening line (the fade interface alone let the scene show behind it, 19:41 run) and
        // is lifted at the fade-in.
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|black")
        return true
    }

    private fun regionsOf(map: InstancedMap): List<Int> {
        val out = ArrayList<Int>()
        for (rx in (map.area.bottomLeftX shr 6)..(map.area.topRightX shr 6)) {
            for (rz in (map.area.bottomLeftZ shr 6)..(map.area.topRightZ shr 6)) out += (rx shl 8) or rz
        }
        return out
    }

    /**
     * An actor: a socketless client ([Bots.create]) that is never saved ([Player.neverSave], set before his login), with
     * the intro and the Death's Office tutorial marked done, the Ancient Curses and Ancient Magicks unlocked, real worn
     * items, a real inventory and the curse book; his sounds are copied to the watching player.
     */
    private fun actor(
        watcher: Player,
        tile: Tile,
        name: String,
        worn: Map<Int, Int>,
        pack: List<Item>,
        ammo: Item? = null,
    ): Client {
        val bot =
            Bots.create(watcher.world, tile, name) { b ->
                b.neverSave = true
                b.attr[TutorIntro.SEEN] = true
                b.attr[DEATH_TUTORIAL_ATTR] = 64
                b.attr[AncientCurses.UNLOCKED_ATTR] = true
                b.attr[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] = true
            }
        bot.moveTo(tile)
        bot.timers.remove(gg.rsmod.game.model.timer.SAVE_TIMER)
        bot.inventory.removeAll()
        bot.equipment.removeAll()
        worn.forEach { (slot, item) -> bot.equipment[slot] = Item(item) }
        if (ammo != null) bot.equipment[13] = ammo
        pack.forEach { bot.inventory.add(it) }
        bot.addBlock(gg.rsmod.game.sync.block.UpdateBlockType.APPEARANCE)
        AncientCurses.switchBook(bot, AncientCurses.PrayerBook.ANCIENT)
        // Prayer points full: a fresh bot has the level but 0 points ("You don't have enough Prayer points left").
        bot.restorePrayer(10_000)
        // A full special bar, as a player who has waited (a fresh bot has 0%: the AGS spec answered "You don't have enough
        // power left." and swung a normal hit - owner 2026-10-08).
        gg.rsmod.plugins.content.inter.attack.AttackTab.setEnergy(bot, 100)
        forwardSounds(bot, watcher)
        return bot
    }

    /**
     * The actors' sounds reach the watcher: combat, curse and food sounds go to the fighting player only (playSound), so
     * every SynthSound / MusicEffect a bot is sent is copied to the watcher (once per tick per id - both bots get the
     * defend grunt). Area sounds already reach him (World.spawn(AreaSound), radius 10).
     */
    private fun forwardSounds(
        bot: Client,
        watcher: Player,
    ) {
        val heard = HashMap<Int, Int>()
        // At the tail: outbound messages pass tail -> head, and Bots.create's adapter (head) swallows every message.
        bot.channel.pipeline().addLast(
            object : ChannelOutboundHandlerAdapter() {
                override fun write(
                    ctx: ChannelHandlerContext,
                    msg: Any,
                    promise: ChannelPromise,
                ) {
                    if (isActive(watcher)) {
                        val cycle = watcher.world.currentCycle
                        when (msg) {
                            is SynthSoundMessage -> if (heard.put(msg.sound, cycle) != cycle) watcher.write(msg.copy())
                            is MusicEffectMessage -> watcher.write(msg.copy())
                        }
                    }
                    ctx.write(msg, promise)
                }
            },
        )
    }

    /**
     * Everything back as it was; safe to call twice (logout mid-scene goes through TutorIntro.restore). [lift] = false keeps
     * the client in scene mode (the end of the scene lifts it once the world map covers the view).
     */
    fun cleanUp(
        player: Player,
        lift: Boolean = true,
        keepMap: Boolean = false,
    ) {
        val world = player.world
        player.attr[CAST]?.let { cast ->
            listOf(cast.pker, cast.escaper).forEach { bot ->
                runCatching { Familiar.dismiss(bot) }
                // The normal logout (the bot logged in through bot.login()), so its logout plugins run: the pker's gravestone
                // from his guard death used to stay in the hall, ownerless, in front of the Quest Tutor (quests run 2026-10-10).
                runCatching { bot.handleLogout() }.onFailure { runCatching { world.unregister(bot) } }
            }
        }
        player.attr.remove(CAST)
        if (!keepMap) releaseMap(player)
        player.attr.remove(ACTIVE)
        if (lift) gg.rsmod.game.model.hud.ClientHud.send(player, "cine|0")
    }

    /** The copy's mirror and multi-combat regions go; called once the watcher has been moved out of it. Safe to call twice. */
    fun releaseMap(player: Player) {
        val world = player.world
        player.attr[MAP]?.let { map ->
            MirroredInstances.unregister(map)
            regionsOf(map).forEach { world.plugins.removeMultiCombatRegion(it) }
        }
        player.attr.remove(MAP)
    }

    // ---- The scene ----------------------------------------------------------------------------------------------------

    /**
     * Plays the scene inside the tour's queue (the watcher is locked, invisible and the minimap blocked by TutorIntro.begin).
     * Returns after the world map closed again, black.
     */
    suspend fun play(task: QueueTask) {
        val player = task.player
        val world = player.world
        player.openInterface(TutorIntro.FADE_TO_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
        task.wait(2)
        if (!setUp(player)) return
        val cast = player.attr[CAST] ?: return cleanUp(player)
        val map = player.attr[MAP]!!
        fun at(real: Tile) = Tile(map.area.bottomLeftX + real.x - BASE_X, map.area.bottomLeftZ + real.z - BASE_Z, 0)
        val pker = cast.pker
        val e = cast.escaper
        try {
            player.moveTo(Tile((PKER_START.x + ESCAPER_START.x) / 2, (PKER_START.z + ESCAPER_START.z) / 2).let { at(it) })
            task.wait(2)
            // The familiars, summoned from their pouches.
            useItem(pker, Items.PACK_YAK_POUCH, "Summon")
            useItem(e, Items.GEYSER_TITAN_POUCH, "Summon")
            // An escaping PvMer has auto-retaliate off (with it on he walked back to fight instead of crossing the ditch).
            // Both have it off (owner 2026-10-08: the pker walked off after the titan with it on).
            listOf(pker, e).forEach { if (it.getVarp(AUTO_RETALIATE_VARP) == 0) Bots.button(it, SPECIAL_BAR_PARENT, AUTO_RETALIATE_COMPONENT) }
            // Run on, by the run orb (750:1): with it off every click - the ditch, the tunnel, the chase - was a walk (owner).
            listOf(pker, e).forEach { if (it.getVarp(RUN_VARP) == 0) Bots.button(it, RUN_ORB, RUN_ORB_COMPONENT) }
            camera(player, pker, e, 0)
            val defs = world.definitions
            val song = defs.get(EnumDef::class.java, 1351).getInt(SCENE_TRACK_INDEX)
            if (song > 0) player.playSong(song, defs.get(EnumDef::class.java, 1345).getString(SCENE_TRACK_INDEX))
            task.wait(1)
            // Owner: the Tutor's line over black, then the picture opens on the chase already running.
            TutorIntro.say(task, arrayOf("Welcome to 78. Here, nothing is safe - not your", "items, not your loot, not even a city if you bring", "a skull into it. Watch."))
            // The curses go on after the line, not before: the line waits for the watcher's click, and the 19:13 run drained
            // both actors' prayer to 0 while it waited (the escaper never deflected again).
            PKER_CURSES.forEach { Bots.button(pker, PRAYER_BOOK, PRAYER_COMPONENT, 1, it) }
            Bots.button(e, PRAYER_BOOK, PRAYER_COMPONENT, 1, DEFLECT_MAGIC)
            Bots.walk(e, at(ESCAPE_ROUTE[0]), run = true) // south, toward the ditch
            Bots.walk(pker, e.tile, run = true)
            task.wait(2)
            gg.rsmod.game.model.hud.ClientHud.send(player, "cine|1")
            player.openInterface(TutorIntro.FADE_FROM_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)

            var tick = 0
            var phase = Phase.BARRAGE
            var phaseAt = 0
            var proc = false
            var escaperStep = 0
            var stepAt = 0
            var wasFrozen = false
            var escaperSafe = false
            var safeAt = -1
            var pkerInGe = false
            var dead = -1
            var lateDeflect = false
            var barragedBack = false
            var agsAgain = false
            var escaperDitch: GameObject? = null
            var styleLeechAt = 0
            var blitzAt = 0
            var ditchClickAt = 0
            var deflectAt = 0
            // A spec in flight: the energy before it (-1 none), the last click, the tick it swung (-1 not yet), the step.
            var specFrom = -1
            var specClickAt = 0
            var swungAt = -1
            var specStep = 0
            var backAt = 0
            var rebarrageAt = 0
            var javelinsAt = 0
            var lastFrozenAt = 0
            var javelinStop = false
            var lastView = View.EAST
            var cameraBase = player.lastKnownRegionBase
            var deathTile: Tile? = null
            val ditchRow = at(Tile(0, DITCH_Z)).z
            val insideMaxZ = at(Tile(0, INSIDE_MAX_Z)).z
            fun since() = tick - phaseAt
            // Owner 2026-10-08: specs are 1-ticked - the weapon switch, the special bar and the attack in one tick, from a tile
            // next to him (he is frozen); re-sent every 3 ticks until the energy drops (the swing), at most [limit] ticks.
            fun startSpec(vararg switchTo: Int) {
                specFrom = AttackTab.getEnergy(pker)
                swungAt = -1
                wield(pker, *switchTo)
                spec(pker, e)
                specClickAt = tick
            }
            // Melee reach: a tile straight next to him (no diagonal hit). Walks there with the current weapon.
            fun inMelee() = (kotlin.math.abs(pker.tile.x - e.tile.x) + kotlin.math.abs(pker.tile.z - e.tile.z)) == 1
            /**
             * Walks next to him for a melee spec; true once he stands there. Owner 2026-10-10: the escaper's geyser titan (size 2)
             * stood beside him and hid the specs - the pker takes the free side farthest from the titan, as a player picks a side.
             */
            fun approach(): Boolean {
                val titan = Familiar.current(e)
                // Twice the distance from a tile to the titan's centre (its footprint spans getSize() tiles).
                fun fromTitan(t: Tile): Int =
                    titan?.let { kotlin.math.abs(2 * t.x - (2 * it.tile.x + it.getSize() - 1)) + kotlin.math.abs(2 * t.z - (2 * it.tile.z + it.getSize() - 1)) } ?: 0
                fun underTitan(t: Tile): Boolean =
                    titan != null && t.x in titan.tile.x until titan.tile.x + titan.getSize() && t.z in titan.tile.z until titan.tile.z + titan.getSize()
                val sides =
                    listOf(e.tile.transform(1, 0), e.tile.transform(-1, 0), e.tile.transform(0, 1), e.tile.transform(0, -1)).filter { side ->
                        !underTitan(side) && !world.collision.isClipped(side) &&
                            !world.collision.isBlocked(e.tile, gg.rsmod.game.model.Direction.between(e.tile, side), false)
                    }
                val best =
                    sides.maxWithOrNull(compareBy<Tile> { fromTitan(it) }.thenBy { -(kotlin.math.abs(it.x - pker.tile.x) + kotlin.math.abs(it.z - pker.tile.z)) })
                if (best == null) {
                    // No free side: the old way, straight at him.
                    if (inMelee()) return true
                    if (!pker.hasMoveDestination()) {
                        val dx = Integer.signum(pker.tile.x - e.tile.x)
                        val dz = Integer.signum(pker.tile.z - e.tile.z)
                        val side = if (kotlin.math.abs(pker.tile.x - e.tile.x) >= kotlin.math.abs(pker.tile.z - e.tile.z)) Tile(e.tile.x + dx, e.tile.z, e.tile.height) else Tile(e.tile.x, e.tile.z + dz, e.tile.height)
                        Bots.walk(pker, side, run = true)
                    }
                    return false
                }
                // In melee on a side as good as the best one: there.
                if (inMelee() && !underTitan(pker.tile) && fromTitan(pker.tile) >= fromTitan(best)) return true
                if (pker.tile.sameAs(best)) return true
                if (!pker.hasMoveDestination() || pker.movementQueue.peekLast()?.let { !it.sameAs(best) } == true) Bots.walk(pker, best, run = true)
                return false
            }
            fun awaitSpec(limit: Int = 12): Boolean {
                if (swungAt >= 0) return true
                if (AttackTab.getEnergy(pker) < specFrom || tick - specClickAt > limit) {
                    swungAt = tick
                    specFrom = -1
                    return true
                }
                if ((tick - specClickAt) % 3 == 0) spec(pker, e)
                return false
            }
            fun next(p: Phase) {
                phase = p
                // The new phase's first tick is the next loop tick (since() == 0 there): with phaseAt = tick its tick-0 switches
                // never ran - no dragon dagger, infernal cape or staff switch, no specs (2026-10-08 log).
                phaseAt = tick + 1
            }
            while (tick++ < 480) {
                if (tick == 3) TutorIntro.closeFade(player)
                val frozen = e.timers.has(FROZEN_TIMER)
                val freezeLeft = e.timers.getTimers()[FROZEN_TIMER] ?: 0
                val escaperSouth = e.tile.z < ditchRow
                val pkerSouth = pker.tile.z < ditchRow
                // ---- The PvMer: deflects against the pker's weapon, eats like a player, runs when he can.
                if (!escaperSafe) {
                    val wanted =
                        when (pker.equipment[3]?.id) {
                            Items.ZARYTE_CROSSBOW, Items.MORRIGANS_JAVELIN -> DEFLECT_MISSILES
                            Items.ARMADYL_GODSWORD_OR, Items.VOIDWAKER -> DEFLECT_MELEE
                            else -> DEFLECT_MAGIC
                        }
                    val icon = when (wanted) { DEFLECT_MISSILES -> PrayerIcon.DEFLECT_MISSILES.id; DEFLECT_MELEE -> PrayerIcon.DEFLECT_MELEE.id; else -> PrayerIcon.DEFLECT_MAGIC.id }
                    // Owner: quick on normal attacks (a tick or two after a switch), but every spec - the AGS before the ditch,
                    // the Voidwaker (and AGS) after the Ice Blitz - catches him on the wrong curse.
                    if (e.prayerIcon != icon) {
                        if (deflectAt == 0) deflectAt = tick + 1 + world.random(1)
                        if (!lateDeflect && tick >= deflectAt) {
                            Bots.button(e, PRAYER_BOOK, PRAYER_COMPONENT, 1, wanted)
                            deflectAt = 0
                        }
                    } else {
                        deflectAt = 0
                    }
                }
                eatLikeAPlayer(e)
                if (frozen) {
                    wasFrozen = true
                    lastFrozenAt = tick
                }
                // Owner 2026-10-08: frozen, he keeps clicking away to escape - toward the ditch, south of it toward the tunnel.
                // The real handler answers every click "A magical force stops you from moving." (not while he barrages back).
                val barragingBack = phase == Phase.SPECS && specStep >= 4
                if (frozen && !escaperSafe && !barragingBack && tick % 3 == 0) {
                    val away = if (escaperSouth) at(TUNNEL_STAND) else Tile(e.tile.x + world.random(-2..2), ditchRow + 2, e.tile.height)
                    Bots.walk(e, away, run = true)
                }
                // Owner: the moment the freeze is over he goes for the ditch, the pker after him; south of it he is refrozen
                // (Ice Blitz), barrages back while frozen, and runs for the tunnel the moment that freeze is over too.
                if (!frozen && wasFrozen && !escaperSafe) {
                    // He escapes as a player does: he clicks the ditch, then the tunnel - the real handlers walk him there
                    // and cross. A click is repeated if he stalls.
                    when (escaperStep) {
                        0 -> {
                            ditchNear(world, e.tile, ditchRow)?.let { d ->
                                escaperDitch = d
                                Bots.inject(e, OpLoc1Message(0, d.tile.x, d.id, d.tile.z))
                            }
                            escaperStep = 1
                            stepAt = tick
                        }
                        1 -> if (escaperSouth) {
                            // Landed: he eats before he runs on, as a player does after a jump; the pker is over on his heels.
                            if (e.getCurrentLifepoints() < e.getMaximumLifepoints() * 90 / 100) useItem(e, Items.SHARK, "Eat")
                            escaperStep = 2
                            stepAt = tick
                        } else if (tick - stepAt >= 6 && !e.hasMoveDestination()) {
                            escaperStep = 0
                        }
                        // After a freeze he eats before he runs on (5 ticks), as after the jump: the pker's second javelin
                        // catches him on the way to the tunnel.
                        2 -> if (tick - stepAt >= LANDING_PAUSE && tick - lastFrozenAt >= 5) {
                            val hole = at(TUNNEL_OUTSIDE)
                            Bots.inject(e, OpLoc1Message(0, hole.x, TUNNEL_OUT, hole.z))
                            escaperStep = 3
                            stepAt = tick
                        }
                        3 -> if (GuardedZones.contains(e.zoneTile())) {
                            Bots.walk(e, at(ESCAPER_WATCH), run = false)
                            escaperSafe = true
                            safeAt = tick
                        } else if (tick - stepAt >= 6 && !e.hasMoveDestination()) {
                            escaperStep = 2
                        }
                    }
                }
                // Through the tunnel he calls his titan, as a player does after a shortcut: a familiar does not crawl after its
                // owner, and within 12 tiles it is never recalled by itself - it stood outside the wall for the rest of the scene
                // (run 2026-10-09 00:47, owner: "the escaper laughs with his geyser titan next to him").
                if (safeAt > 0 && tick - safeAt == 2) Bots.button(e, FOLLOWER_TAB, CALL_FAMILIAR)
                // Refrozen south of the ditch: the tunnel is clicked again once the freeze is over.
                if (frozen && escaperStep == 3) {
                    escaperStep = 2
                    stepAt = tick - LANDING_PAUSE
                }
                // ---- The pker: a real hybrid rotation, the chase over the ditch, the refreeze, his last stand in the Exchange.
                if (dead < 0) {
                    // No food while a spec is on its way: a shark delays the swing 3 ticks, and the AGS spec of the 19:52 run
                    // never swung at all (he ate as he reached him, the phase ran out, the staff took the queued attack). In
                    // the Exchange he eats twice at most (owner round 3; owner 2026-10-08 "die fast").
                    val specPending = (phase == Phase.AGS || phase == Phase.SPECS) && specFrom >= 0 && swungAt < 0
                    val low = pker.getCurrentLifepoints() < pker.getMaximumLifepoints() * 35 / 100
                    if (pkerInGe) {
                        // Owner 2026-10-08 ("Guards get the kill"): the PvMer's barrage-back ~40 ticks earlier still made him the
                        // pker's PvP aggressor, so he took the kill and the loot key - a key carrier is skulled and the guards
                        // killed him too (run 20:21). In the Exchange the record is cleared: a real guard death, public loot.
                        pker.timers.remove(gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER)
                        pker.attr.remove(gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR)
                        // His titan stays by him (owner: "the escaper laughs with his geyser titan next to him").
                        Familiar.current(e)?.let { titan ->
                            if (titan.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() === pker) gg.rsmod.plugins.content.combat.Combat.reset(titan)
                        }
                        // Owner 2026-10-08: the guards kill him at once - in the Exchange he no longer eats (the real 1337 guards'
                        // ramp, 20% of his hitpoints + 2 a hit every 2 ticks, and the Wizguard do the rest).
                    } else if (phase == Phase.CHASE || phase == Phase.CHASE_TUNNEL || phase == Phase.FOLLOW) {
                        // Chasing he only eats to save his life: three sharks in the 10-tick run to the ditch held his javelin
                        // back each time (run 21:39 - no throw).
                        // Chasing him into the tunnel he no longer stops to eat (only to save his life): he reaches the guards
                        // as he is, and they finish him in a few hits (owner 2026-10-08 "the pker dies instantly").
                        if (low) eatLikeAPlayer(pker)
                    } else if (!specPending || low) {
                        eatLikeAPlayer(pker)
                    }
                    if (phase != Phase.BLITZ && !pkerInGe) drinkLikeAPlayer(pker)
                    // A hybrid's style leech goes with his switch (Strength / Ranged / Magic are one group - owner 2026-09-18).
                    val styleLeech =
                        when (pker.equipment[3]?.id) {
                            Items.ZARYTE_CROSSBOW, Items.MORRIGANS_JAVELIN -> AncientCurse.LEECH_RANGED
                            Items.ARMADYL_GODSWORD_OR, Items.VOIDWAKER -> AncientCurse.LEECH_STRENGTH
                            Items.TOXIC_STAFF_OF_THE_DEAD, TOXIC_STAFF_UNCHARGED -> AncientCurse.LEECH_MAGIC
                            else -> null
                        }
                    if (styleLeech != null && !AncientCurses.isCurseActive(pker, styleLeech) && tick - styleLeechAt >= 2) {
                        Bots.button(pker, PRAYER_BOOK, PRAYER_COMPONENT, 1, styleLeech.slot)
                        styleLeechAt = tick
                    }
                    when (phase) {
                        Phase.BARRAGE -> {
                            // Owner 2026-10-08: he mass-clicks the escaper as a player does - the cast re-sent every 2 ticks;
                            // the game casts it when his staff is ready (a manual cast already queued is kept, Combat.postDamage).
                            if (since() >= 3 && since() % 2 == 1 && !frozen) castSpell(pker, e, ICE_BARRAGE)
                            if (frozen) next(Phase.CROSSBOW)
                        }
                        Phase.CROSSBOW -> {
                            // Off the staff with a click under his feet: a barrage still queued from the mass-clicks fired with the
                            // crossbow in his hands (run 22:17).
                            if (since() == 0) Bots.walk(pker, pker.tile, run = true)
                            if (since() == 1) wield(pker, Items.ZARYTE_CROSSBOW, Items.DIZANAS_QUIVER, Items.ARMADYL_CHAPS)
                            if (since() == 2) {
                                // The owner's single Armour Piercing proc; every other bolt rolls for real.
                                if (!proc) pker.attr[EnchantedBolts.FORCE_NEXT_PROC] = true
                                proc = true
                                Bots.inject(pker, OpPlayer2Message(e.index))
                            }
                            if (since() in 3..5) Bots.inject(pker, OpPlayer2Message(e.index))
                            if (since() >= 6) next(Phase.AGS)
                        }
                        Phase.AGS -> {
                            // Owner: infernal cape + Armadyl godsword (or), ONE spec into his Deflect Melee - 1-ticked from next
                            // to him; the moment it has swung, back to mage and an Ice Barrage in the same tick (no follow-up
                            // godsword hit). The phase waits for the swing itself (the spec energy drops).
                            if (since() == 0) lateDeflect = true
                            if (specFrom < 0 && swungAt < 0) {
                                if (approach() || since() >= 8) startSpec(Items.ARMADYL_GODSWORD_OR, Items.INFERNAL_CAPE)
                            } else if (awaitSpec()) {
                                wield(pker, staffOf(pker), Items.MAGES_BOOK, Items.ELDER_CHAOS_ROBE_OR, Items.IMBUED_GUTHIX_CAPE)
                                castSpell(pker, e, ICE_BARRAGE)
                                lateDeflect = false
                                swungAt = -1
                                next(Phase.MAGE)
                            }
                        }
                        Phase.MAGE -> {
                            // On his mage switch (put on as the spec landed, with the first barrage); barrages while the freeze
                            // lasts (each a real hit, no new freeze), 5 ticks apart.
                            // No re-click in the freeze's last ticks: a barrage still queued then fired as he ran and froze him
                            // again (run 20:52, cycle 118 -> 121).
                            if (since() >= 1 && since() % 2 == 1 && frozen && freezeLeft > 5) castSpell(pker, e, ICE_BARRAGE)
                            if (!frozen && since() >= 2) next(Phase.CHASE)
                        }
                        Phase.CHASE -> {
                            // Owner 2026-10-08: he never stands waiting at the ditch - he mass-clicks the escaper the whole way
                            // as a player does (the attack re-sent every 2 ticks, the combat follows him into range): one
                            // Morrigan's javelin, then the Zaryte crossbow. The moment the escaper jumps, the pker stops on the
                            // north bank (a click on his own tile) and the blitz goes over the ditch as he lands.
                            if (since() == 0) {
                                javelinsAt = javelins(pker)
                                Bots.walk(pker, e.tile, run = true)
                                wield(pker, Items.MORRIGANS_JAVELIN)
                            }
                            if (since() >= 1 && javelinsAt - javelins(pker) >= 1 && pker.equipment[3]?.id == Items.MORRIGANS_JAVELIN) {
                                wield(pker, Items.ZARYTE_CROSSBOW, Items.DIZANAS_QUIVER, Items.ARMADYL_CHAPS)
                            }
                            val jumping = e.lock != LockState.NONE || escaperSouth
                            // No new shot once he is close to the ditch: the staff must be ready when he jumps (run 22:17: the last
                            // bolt held the blitz until he was on his way to the tunnel, and the freeze broke on the 10-tile leash).
                            val nearDitch = e.tile.z <= ditchRow + 4
                            if (since() >= 1 && since() % 2 == 1 && !jumping && !nearDitch) Bots.inject(pker, OpPlayer2Message(e.index))
                            if (nearDitch && !jumping && pker.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR] != null) {
                                Bots.walk(pker, Tile(e.tile.x, ditchRow + 2, e.tile.height), run = true)
                            }
                            if (jumping) {
                                // To the bank next to the segment he jumped (the freeze breaks beyond 10 tiles of the caster - the
                                // 20:52 run's blitz froze him from 12 tiles back and broke at once), the staff on the way.
                                val bank = escaperDitch?.let { Tile(it.tile.x + 1, ditchRow + 2, it.tile.height) } ?: Tile(e.tile.x, ditchRow + 2, e.tile.height)
                                Bots.walk(pker, bank, run = true)
                                wield(pker, staffOf(pker), Items.MAGES_BOOK, Items.ELDER_CHAOS_ROBE_OR, Items.IMBUED_GUTHIX_CAPE)
                                next(Phase.BLITZ)
                            }
                            if (escaperSafe) next(Phase.FOLLOW)
                        }
                        Phase.BLITZ -> {
                            // Over the ditch: Ice Blitz the moment he has landed, again every 4 ticks until it holds (a real
                            // roll - a splash is a splash). Frozen, the pker jumps the ditch after him (a neighbouring segment).
                            if (escaperSouth && e.lock == LockState.NONE && !frozen && !pkerSouth && pker.lock == LockState.NONE && (blitzAt == 0 || tick - blitzAt >= 4)) {
                                // Owner 2026-10-08 ("Force it"): the first blitz lands, as the forced bolt proc - the real cast,
                                // freeze, graphic and sound; only its accuracy roll is skipped.
                                if (blitzAt == 0) pker.attr[MagicCombatStrategy.FORCE_NEXT_LAND] = true
                                castSpell(pker, e, ICE_BLITZ)
                                blitzAt = tick
                            }
                            if (frozen && !pkerSouth && pker.lock == LockState.NONE && !pker.hasMoveDestination() && tick - ditchClickAt >= 3) {
                                ditchNear(world, pker.tile, ditchRow, avoid = escaperDitch?.tile)?.let { d -> Bots.inject(pker, OpLoc1Message(0, d.tile.x, d.id, d.tile.z)) }
                                ditchClickAt = tick
                            }
                            if (frozen) pker.attr.remove(MagicCombatStrategy.FORCE_NEXT_LAND)
                            if (frozen && pkerSouth && pker.lock == LockState.NONE) next(Phase.SPECS)
                            if (escaperSafe) next(Phase.FOLLOW)
                        }
                        Phase.SPECS -> {
                            // Owner: the Voidwaker spec after the second freeze, 1-ticked from next to him, on the wrong curse;
                            // the AGS (or) again only if his Leech Special Attack gave him the energy (no Recover special -
                            // removed 2026-09-15). After each spec straight to the Zaryte crossbow and a bolt in the same tick -
                            // never a normal melee hit after it (owner: "a korasi whack, this is not ok"). Then, frozen, he
                            // barrages back once.
                            if (since() == 0) {
                                lateDeflect = true
                                specStep = 0
                            }
                            when (specStep) {
                                0 -> {
                                    if (approach() || since() >= 8) {
                                        startSpec(Items.VOIDWAKER, Items.INFERNAL_CAPE)
                                        specStep = 1
                                    }
                                }
                                1 -> if (awaitSpec()) {
                                    if (AttackTab.getEnergy(pker) >= AGS_ENERGY) {
                                        startSpec(Items.ARMADYL_GODSWORD_OR)
                                        agsAgain = true
                                        specStep = 2
                                    } else {
                                        specStep = 3
                                    }
                                }
                                2 -> if (awaitSpec()) specStep = 3
                                3 -> {
                                    // Straight to Morrigan's javelins (owner: two throws after the ditch, for variety).
                                    javelinsAt = javelins(pker)
                                    wield(pker, Items.MORRIGANS_JAVELIN)
                                    Bots.inject(pker, OpPlayer2Message(e.index))
                                    lateDeflect = false
                                    backAt = tick
                                    specStep = 4
                                }
                                4 -> if (tick - backAt >= 3) {
                                    if (!barragedBack) castSpell(e, pker, ICE_BARRAGE)
                                    barragedBack = true
                                    backAt = tick
                                    specStep = 5
                                }
                                5 -> if (tick - backAt >= 2) next(Phase.BOLTS)
                            }
                        }
                        Phase.BOLTS -> {
                            // Bolts while he is frozen; as the freeze runs out, a barrage that meets his freeze immunity.
                            // Owner 2026-10-08: after the ditch two Morrigan's javelin throws for variety (mass-clicked), then the
                            // staff for the re-barrage. The last throw leaves time for him to be ready again: the 19:52 re-barrage
                            // waited on his weapon and landed a tick after the immunity ended - a fresh 32-tick freeze.
                            // The second throw only if it is out early: its attack timer held back the re-barrage until he had run
                            // into the tunnel (runs 21:22, 21:28). Otherwise it goes as he runs for the tunnel (CHASE_TUNNEL).
                            // His javelin attack repeats by itself: it ends here, so its last throw is early enough for the staff
                            // (run 21:55: a late repeat held the re-barrage until after the immunity - a new 32-tick freeze).
                            // The repeat throws the second javelin when it comes while 9+ ticks of freeze are left; then he stops.
                            if (!javelinStop && (javelinsAt - javelins(pker) >= 2 || freezeLeft <= 8)) {
                                Bots.walk(pker, pker.tile, run = true)
                                javelinStop = true
                            }
                            if (frozen && freezeLeft <= 6) next(Phase.REBARRAGE)
                            if (!frozen && since() >= 2) next(Phase.REBARRAGE)
                        }
                        Phase.REBARRAGE -> {
                            // Cast as the freeze runs out, so it lands in his 5 ticks of immunity (real rule: no new freeze).
                            // The switch ends his javelin attack (a click under his feet): with the attack still running the staff
                            // took its next swing as a melee whack, and the barrage came late and froze him again (run 21:00).
                            if (since() == 0) {
                                Bots.walk(pker, pker.tile, run = true)
                                wield(pker, staffOf(pker), Items.MAGES_BOOK, Items.ELDER_CHAOS_ROBE_OR, Items.IMBUED_GUTHIX_CAPE)
                            }
                            // Cast right after the switch: it lands at the end of the freeze or within the 5 ticks of immunity.
                            if (rebarrageAt == 0 && since() >= 1) {
                                castSpell(pker, e, ICE_BARRAGE)
                                rebarrageAt = tick
                            }
                            // On once it has gone out (a manual cast clears itself when it fires): switching on a fixed tick put the
                            // crossbow in his hands while the barrage still waited on the javelin's attack timer (run 21:17).
                            val fired = !pker.attr.has(gg.rsmod.plugins.content.combat.Combat.CASTING_SPELL)
                            if (rebarrageAt > 0 && ((fired && tick - rebarrageAt >= 2) || tick - rebarrageAt >= 10)) next(Phase.CHASE_TUNNEL)
                        }
                        Phase.CHASE_TUNNEL -> {
                            // The second javelin after the ditch if the freeze left room for only one, then the crossbow.
                            if (since() == 0) {
                                if (javelinsAt - javelins(pker) < 2) wield(pker, Items.MORRIGANS_JAVELIN) else wield(pker, Items.ZARYTE_CROSSBOW, Items.DIZANAS_QUIVER, Items.ARMADYL_CHAPS)
                            }
                            if (since() >= 1 && pker.equipment[3]?.id == Items.MORRIGANS_JAVELIN && javelinsAt - javelins(pker) >= 2) {
                                wield(pker, Items.ZARYTE_CROSSBOW, Items.DIZANAS_QUIVER, Items.ARMADYL_CHAPS)
                            }
                            // Owner 2026-10-10 ("pker hangt bij tunnel"): once the escaper crawls into the tunnel the pker clicks it
                            // after him, as a player does - attacking on, his combat path found no way through the wall and walked
                            // him east along it, and the tunnel was clicked only when the escaper stood in the Exchange (5 s lost).
                            val escaperCrawling = escaperStep == 3 && e.lock != LockState.NONE
                            if (since() % 2 == 1 && !escaperSafe && !escaperCrawling) Bots.inject(pker, OpPlayer2Message(e.index))
                            if (escaperSafe || escaperCrawling) next(Phase.FOLLOW)
                        }
                        Phase.FOLLOW -> {
                            // Into the Exchange after him - through the tunnel; the real guards meet his skull there.
                            if (!pkerInGe) {
                                // Still north of the ditch (the escaper got in first): over the ditch, then the tunnel - each clicked
                                // as a player does (the handlers walk him there and cross); again while idle. From the north bank the
                                // tunnel click could never reach it (run 20:44: he stood there for the rest of the scene).
                                if (since() == 0 || (since() % 3 == 0 && !pker.hasMoveDestination() && pker.lock == LockState.NONE)) {
                                    if (!pkerSouth) {
                                        ditchNear(world, pker.tile, ditchRow)?.let { d -> Bots.inject(pker, OpLoc1Message(0, d.tile.x, d.id, d.tile.z)) }
                                    } else {
                                        val hole = at(TUNNEL_OUTSIDE)
                                        Bots.inject(pker, OpLoc1Message(0, hole.x, TUNNEL_OUT, hole.z))
                                    }
                                }
                                if (GuardedZones.contains(pker.zoneTile())) {
                                    pkerInGe = true
                                    // Owner 2026-10-08 ("Guards get the kill"): the killer is whoever dealt him most damage recently
                                    // (PlayerDeathAction, DamageMap.getMostDamage) - the PvMer's barrages and titan, not the guards.
                                    pker.damageMap.reset()
                                    // The watcher steps along (hidden), so his zone box turns Guarded with the pker's.
                                    player.moveTo(pker.tile)
                                    next(Phase.LAST_STAND)
                                }
                            }
                        }
                        Phase.LAST_STAND -> {
                            // The guards and the Wizguard meet him at the tunnel's inner end; he dies there (owner 2026-10-08:
                            // "the pker dies instantly", no teleport attempt any more).
                        }
                    }
                    if (pker.isDead() || pker.getCurrentLifepoints() <= 0) {
                        dead = tick
                        deathTile = Tile(pker.tile)
                    }
                } else {
                    // The real respawn puts him in the real world: hidden the moment he leaves the copy.
                    if (!map.area.contains(pker.tile)) pker.invisible = true
                    // The PvMer walks up to the loot pile, looks at it and laughs; his geyser titan follows him (a real familiar).
                    val pile = deathTile
                    // South-west of the pile, away from the wall: right behind it the wall hid him from the camera (run 22:28).
                    if (tick - dead == 1 && pile != null) Bots.walk(e, Tile(pile.x - 1, pile.z - 2, pile.height), run = false)
                    if (tick - dead == 5 && pile != null) e.faceTile(pile)
                    if (tick - dead == 6) e.animate(LAUGH)
                    // Owner 2026-10-08: he dies fast, his loot lies on the ground, then the introduction goes on.
                    if (tick - dead >= 14) break
                }
                // A watcher move that rebuilt his map shifts the client's local tiles under the camera: the glide then started from
                // a point across the Exchange and showed its roof for seconds (run 2026-10-09 01:38). After a rebuild it cuts.
                val base = player.lastKnownRegionBase
                val rebuilt = base != cameraBase
                cameraBase = base
                val fallen = deathTile
                if (fallen != null) {
                    // After the death: the laughing PvMer two tiles from the loot pile, his titan beside him - the same steep
                    // view as the guards' fight (a separate pile-and-PvMer framing centred over the wall, run 22:12).
                    // Cuts, not a glide: after the death the glide never settled and drifted over the wall (run 22:23).
                    if (!player.tile.isWithinRadius(fallen, 5)) player.moveTo(fallen)
                    if (tick - dead == 1 || tick - dead == 5 || rebuilt) camera(player, e, null, 0, View.INSIDE, insideMaxZ)
                } else if (map.area.contains(pker.tile)) {
                    keepClose(player, pker, if (pkerInGe || escaperSafe) null else e)
                    // Owner 2026-10-08 ("High, from the north"): past the ditch the camera stands high on the Wilderness side
                    // looking south at the ditch, the wall and the tunnel (from the east the wall walkway hid him); in the
                    // Exchange it looks from inside at the guards' fight.
                    // Owner: "when crossing the ditch we need to see the combat perfectly" - the north view from his run-up on.
                    val view = if (pkerInGe) View.INSIDE else if (e.tile.z <= ditchRow + 6 || pker.tile.z <= ditchRow + 4) View.NORTH else View.EAST
                    // Through the tunnel the camera cuts inside (a glide over the wall started on the trees, run 21:44).
                    camera(player, pker, if (pkerInGe) null else e, if (rebuilt || (view == View.INSIDE && lastView != View.INSIDE)) 0 else 4, view, insideMaxZ)
                    lastView = view
                }
                task.wait(1)
            }
            TutorIntro.say(
                task,
                arrayOf("Guards ignore protection prayers, a hit stops a", "skulled teleport, and a death to a guard drops", "everything he carried. Loot keys are always lost."),
            )
            TutorIntro.say(task, arrayOf("Your world map shows where Safe and Dangerous", "ground lies. Take a look."))
        } finally {
            // Owner 2026-10-09: the loot flashed before the map - cine|0 here switched the client back to its own graphics
            // while the fade to black was only starting. The curtain goes black at once, still in scene mode (no switch).
            gg.rsmod.game.model.hud.ClientHud.send(player, "cine|black")
            player.openInterface(TutorIntro.FADE_TO_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
            // The copy stays mirrored until he leaves it (the tour's next move, releaseMap): released here, he stood on
            // unmirrored ground and his zone box said "Deadman" over the world map and the "Next" choice (run 2026-10-09 00:47).
            // A move to the hall instead held the world map on "Loading..." for most of its 10 s (run 00:59).
            cleanUp(player, lift = false, keepMap = true)
        }
        task.wait(2)
        // The world map on that border, the Deadman zones drawn on it (client DeadmanWorldMapOverlay); centred by its varcs.
        player.openFullscreenInterface(interfaceId = 755)
        val tileId = (BORDER.z and 0x3fff) + ((BORDER.x and 0x3fff) shl 14)
        player.setVarc(622, tileId)
        player.setVarc(674, tileId)
        // Curtain off, scene mode kept (client RspsCinematic.hide draws the world map in full). Owner 2026-10-10: cine|0 here
        // switched the client back to the player's own graphics and the map stood on "Loading - please wait" for 8 of its 10 s.
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|1")
        var waited = 0
        while (waited++ < MAP_TICKS && player.interfaces.isVisible(755)) task.wait(1)
        if (player.interfaces.isVisible(755)) player.closeFullscreenInterface()
        // Owner 2026-10-08: after the map the loot stayed in view under the "Next" choice - the client closes the fade with the
        // map and the reopened one (not loaded after the map) never went black. The client curtain (as at the opening) holds
        // the black until the next stop's fade covers the view (TutorIntro.liftCurtain).
        TutorIntro.curtain(player)
        player.openInterface(TutorIntro.FADE_TO_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
        task.wait(2)
    }

    private enum class Phase { BARRAGE, CROSSBOW, AGS, MAGE, CHASE, BLITZ, SPECS, BOLTS, REBARRAGE, CHASE_TUNNEL, FOLLOW, LAST_STAND }

    // ---- Real inputs ----------------------------------------------------------------------------------------------------

    /** An Ancient spell ([spell] = its 193 component) on [target], as the client sends it. */
    private fun castSpell(
        caster: Client,
        target: Player,
        spell: Int,
    ) = Bots.inject(caster, OpPlayerTMessage(target.index, (ANCIENT_BOOK shl 16) or spell, -1, 0))

    /** The special bar (after the wield, which resets it), then the attack. */
    private fun spec(
        bot: Client,
        target: Player,
    ) {
        Bots.button(bot, SPECIAL_BAR_PARENT, SPECIAL_BAR_COMPONENT)
        Bots.inject(bot, OpPlayer2Message(target.index))
    }

    /** The toxic staff of the dead he carries: charged (22763) or, once its charge is spent, uncharged (22761). */
    private fun staffOf(bot: Client): Int =
        if (bot.inventory.getItemIndex(Items.TOXIC_STAFF_OF_THE_DEAD, skipAttrItems = false) >= 0) Items.TOXIC_STAFF_OF_THE_DEAD else TOXIC_STAFF_UNCHARGED

    /** Wields [items] from the inventory in one tick, as a player's quick switches. */
    private fun wield(
        bot: Client,
        vararg items: Int,
    ) = items.forEach { item -> EQUIP_OPTIONS.any { useItem(bot, item, it) } }

    /** The equip option an item's inventory menu uses (crossbow "Wield", quiver and chaps "Wear", ...). */
    private val EQUIP_OPTIONS = listOf("Wield", "Wear", "Equip", "Hold")

    /** The inventory option [option] on [item] (Bots.itemOption: the real button and handler). */
    private fun useItem(
        bot: Client,
        item: Int,
        option: String,
    ): Boolean {
        val slot = bot.inventory.getItemIndex(item, skipAttrItems = false)
        if (slot < 0) return false
        return runCatching { Bots.itemOption(bot, slot, option) }.getOrDefault(false)
    }

    /** Eating like a player (owner 2026-10-08): a shark under 75%, a shark and a karambwan in the same tick under 50%. */
    private fun eatLikeAPlayer(bot: Client) {
        val lp = bot.getCurrentLifepoints()
        val max = bot.getMaximumLifepoints()
        if (lp <= 0) return
        if (lp < max * 50 / 100) {
            useItem(bot, Items.SHARK, "Eat")
            useItem(bot, Items.COOKED_KARAMBWAN, "Eat")
        } else if (lp < max * 75 / 100) {
            useItem(bot, Items.SHARK, "Eat")
        }
    }

    private fun javelins(bot: Client): Int =
        (bot.equipment[3]?.takeIf { it.id == Items.MORRIGANS_JAVELIN }?.amount ?: 0) + bot.inventory.getItemCount(Items.MORRIGANS_JAVELIN)


    /** Potions like a player: a super restore under 30% prayer, a super energy under 30% run energy (any dose). */
    private fun drinkLikeAPlayer(bot: Client) {
        if (bot.getCurrentLifepoints() <= 0) return
        if (bot.getCurrentPrayerPoints() < bot.getMaximumPrayerPoints() * 30 / 100) {
            SUPER_RESTORES.firstOrNull { useItem(bot, it, "Drink") }
        } else if (bot.runEnergy < 30.0) {
            SUPER_ENERGIES.firstOrNull { useItem(bot, it, "Drink") }
        }
    }

    private val SUPER_RESTORES = listOf(Items.SUPER_RESTORE_1, Items.SUPER_RESTORE_2, Items.SUPER_RESTORE_3, Items.SUPER_RESTORE_4)
    private val SUPER_ENERGIES = listOf(Items.SUPER_ENERGY_1, Items.SUPER_ENERGY_2, Items.SUPER_ENERGY_3, Items.SUPER_ENERGY_4)

    /**
     * The Wilderness ditch segment nearest [from] around the copy's ditch row (3x2 segments, any of the cross ids). The old
     * search (2 tiles either side, the one row) found none where he stood and he never left (owner 2026-10-08).
     */
    private fun ditchNear(
        world: gg.rsmod.game.model.World,
        from: Tile,
        row: Int,
        avoid: Tile? = null,
    ): GameObject? {
        var best: GameObject? = null
        for (dz in -2..2) {
            for (dx in -12..12) {
                val tile = Tile(from.x + dx, row + dz)
                val chunk = world.chunks.get(tile, createIfNeeded = true) ?: continue
                val found =
                    chunk.getEntities<GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                        .firstOrNull { it.id in DITCH_IDS && it.tile != avoid } ?: continue
                if (best == null || found.tile.getDistance(from) < best.tile.getDistance(from)) best = found
            }
        }
        return best
    }

    /**
     * The hidden watcher moves with the fight: players are sent only within 15 tiles (PlayerSynchronizationTask), so a fixed
     * post left the actors and their familiars off his screen. He jumps (invisible) to their midpoint when 6+ tiles away.
     */
    private fun keepClose(
        watcher: Player,
        a: Player?,
        b: Player?,
    ) {
        a ?: return
        val mid = if (b != null) Tile((a.tile.x + b.tile.x) / 2, (a.tile.z + b.tile.z) / 2, a.tile.height) else a.tile
        if (!watcher.tile.isWithinRadius(mid, 5)) watcher.moveTo(mid)
    }

    // ---- Camera ------------------------------------------------------------------------------------------------------

    private enum class View { EAST, NORTH, INSIDE }

    /** Above the fight, framing the PvMer too when [other] is given (their midpoint); rate 0 cuts. */
    private fun camera(
        watcher: Player,
        subject: Player,
        other: Player?,
        rate: Int,
        view: View = View.EAST,
        insideMaxZ: Int = Int.MAX_VALUE,
    ) {
        val base = watcher.lastKnownRegionBase ?: return
        if (subject.invisible) return
        val cx = if (other != null) (subject.tile.x + other.tile.x) / 2 else subject.tile.x
        val midZ = if (other != null) (subject.tile.z + other.tile.z) / 2 else subject.tile.z
        val cz = if (view == View.INSIDE) minOf(midZ, insideMaxZ) else midZ
        val x = cx - base.x
        val z = cz - base.z
        // The further apart they are, the higher and further back the camera, so both stay in the frame.
        val spread = if (other != null) subject.tile.getDistance(other.tile) else 0
        val back = 7 + spread / 2
        val height = (1100 + spread * 70).coerceAtMost(2600)
        val speed = if (rate == 0) 100 else rate
        val step = if (rate == 0) 0 else 1
        when (view) {
            // From the side of the chase (east of it): the familiars trail behind the players (north), never between camera and fight.
            View.EAST -> {
                watcher.moveCameraTo(speed, x + back + 2, z - 2, height, step)
                watcher.cameraLookAt(x - 1, z, 120, step, speed)
            }
            // Behind the pker on the Wilderness side, looking south over his shoulder at the ditch, the escaper and the tunnel
            // (owner: "we need to see the combat perfectly"; the higher, further view lost the pker behind the titan).
            // Both in the frame (run 20:44: weighted on the pker, the running escaper left the picture): their midpoint, the
            // camera further back and higher the further apart they are.
            View.NORTH -> {
                watcher.moveCameraTo(speed, x + 1, z + 5 + spread / 2, (1300 + spread * 60).coerceAtMost(2400), step)
                watcher.cameraLookAt(x, z - 1, 90, step, speed)
            }
            // Inside the Exchange, close and low south-east of him, looking back at the wall where the guards meet him - the
            // courtyard and the mansion stay out of the frame (owner: "it shows the whole grand exchange").
            // Steep, from just north-east above the tree tops: the trees by the tunnel's inner end hid the guards' fight from any
            // low camera, and the Royal Hall (3158-3171, 3486-3497) stays out of the frame (owner: "our mansion as white things").
            View.INSIDE -> {
                // The client caps how steeply the camera looks down: from closer the frame centre fell south of him (run 21:50).
                watcher.moveCameraTo(speed, x + 2, z + 6, 1150, step)
                watcher.cameraLookAt(x, z + 1, 40, step, speed)
            }
        }
    }
}
