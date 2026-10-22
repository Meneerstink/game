package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.cameraLookAt
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.forceCameraAngle
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.moveCameraTo
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.resetCamera
import gg.rsmod.plugins.api.ext.setMinimapState
import gg.rsmod.plugins.content.mechanics.pvp.DangerWarning
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig

/**
 * The new-player introduction by the Quest Tutor (owner 2026-10-07 night run): a 3-4 minute camera tour - the Grand
 * Exchange and its hall, the player's own house, the Guild Cellar, then a Safe, a Guarded and a Dangerous Deadman area -
 * narrated by the Tutor, ending back at the Tutor with "Choose your path" open (the Quest Helper is the player's own choice).
 * Only new accounts get it on their first login (starter_kit.plugin.kts); anyone can watch it again through the Tutor.
 *
 * The mechanism is ported from Darkan's cutscene engine (com.rs.engine.cutscene.Cutscene + its actions): on start the
 * player is locked and taken out of the world's reach (FULL_WITH_DAMAGE_IMMUNITY, invisible), the minimap is blocked
 * (setBlockMinimapState(2)); each scene fades to black (interface 115), moves the player to the scene so its map loads
 * behind the black screen, places the camera (CamMoveTo / CamLookAt, local scene tiles) and fades back (interface 170);
 * on stop the player returns to the end tile, minimap and camera are reset and the lock is lifted. A disconnect or any
 * interruption runs the same stop (terminateAction / on_logout): the player is back in the hall, intro marked as seen.
 *
 * Camera values (client Camera.moveTo / lookAt, revision 667): x/z are local scene tiles, height is above the ground
 * in units of 4, a rate below 100 eases in (rate/1000 of the remaining distance per client cycle plus step).
 */
object TutorIntro {
    /** The introduction was shown (or skipped / interrupted) once: new accounts get it only until then. */
    val SEEN = AttributeKey<Boolean>(persistenceKey = "tutor_intro_seen")

    /** The client's black curtain (cine|black) is up between the guard scene's world map and the next stop (never saved). */
    private val CURTAIN = AttributeKey<Boolean>()

    /** The tour is running (never saved). */
    val ACTIVE = AttributeKey<Boolean>()

    const val FADE_TO_BLACK = 115
    const val FADE_FROM_BLACK = 170

    /** In front of the Quest Tutor (new_player.yml choice_npc 3162,3491 facing south). */
    fun endTile(): Tile {
        val tutor = NewPlayerConfig.current.choiceNpcTile
        return Tile(tutor.x, tutor.z - 2, tutor.height)
    }

    /** A camera point: a world tile and the height above the ground there. */
    class Cam(val x: Int, val z: Int, val height: Int)

    /**
     * One stop of the tour. [stand] is where the (hidden) player waits so the scene's map is loaded; [house] stops take
     * place in the player's own house and give their camera points relative to the house arrival tile.
     */
    class Scene(
        val title: String,
        val stand: Tile,
        val from: Cam,
        val to: Cam,
        val lookFrom: Cam,
        val lookTo: Cam,
        val lines: List<Array<String>>,
        val house: Boolean = false,
        val moveRate: Int = 4,
        val moveStep: Int = 1,
        val lookRate: Int = 4,
        val lookStep: Int = 1,
    )

    private fun line(vararg text: String) = arrayOf(*text)

    val SCENES: List<Scene> by lazy {
        listOf(
            Scene(
                "the Grand Exchange",
                // Over the Grand Exchange gatehouse, down the south avenue to the palace's entrance (framed in the client
                // 2026-10-07, camworld). The player waits under the gatehouse roof - a Guarded tile (GuardedZones z >= 3462).
                stand = Tile(3165, 3463, 0),
                from = Cam(3165, 3450, 1800), to = Cam(3166, 3468, 800),
                lookFrom = Cam(3165, 3492, 0), lookTo = Cam(3165, 3488, 350),
                lines =
                    listOf(
                        line("Now let me show you around before you set off."),
                        line("This is the Grand Exchange, the heart of 78.", "Buy and sell almost anything here with other", "players - your offers keep working while you're away."),
                        line("The Exchange stands in Varrock, a Guarded city.", "No other player can attack you here."),
                    ),
            ),
            Scene(
                "the hall",
                // Inside, from the south entrance over the fountain; the player waits in the south-east corner, out of frame.
                stand = Tile(3170, 3487, 0),
                from = Cam(3168, 3483, 330), to = Cam(3164, 3485, 280),
                lookFrom = Cam(3161, 3493, 120), lookTo = Cam(3164, 3492, 100),
                lines =
                    listOf(
                        line("Inside is the hall - your home in 78. You start", "here, and you come back here after a death."),
                        line("The Quartermaster keeps supplies: food, potions,", "runes and house teleport tabs."),
                        line("I stand by the fountain. Talk to me any time for", "your quest choices, the way to every quest, or to", "watch this tour again."),
                    ),
            ),
            Scene(
                "the quest lounge",
                // The hall's first floor (level 1): every hall quest giver stands here (world_edits.json, owner 2026-09-27
                // layout plus his later moves). The player waits in the lounge's south-east corner, out of frame.
                // Owner 2026-10-10 ("alle 5 in beeld"): the givers stand all round the lounge (Azzanadra east, Aleck and King
                // Narnode north, the Archaeologist and the Oneiromancer west), so the camera sweeps east -> north -> west in
                // ~10 s while the first line names them (framed in the client 2026-10-10, camworld ... 1 1).
                stand = Tile(3170, 3487, 1),
                from = Cam(3168, 3487, 340), to = Cam(3161, 3487, 340),
                lookFrom = Cam(3172, 3491, 120), lookTo = Cam(3156, 3491, 120),
                moveRate = 1, lookRate = 1,
                lines =
                    listOf(
                        line("Upstairs, in the lounge, the quest givers wait: the", "Archaeologist, Azzanadra, the Oneiromancer, Aleck", "and King Narnode. Take a marble staircase up."),
                        line("Two quests start further away: the Gossip at", "Sinclair Mansion and Edmond in East Ardougne."),
                        line("The Quest Helper's arrow always shows who to talk", "to next - and when they're on another floor, it", "points you to the stairs."),
                    ),
            ),
            Scene(
                "your house",
                // Relative to the house arrival (PlayerHouse.build: the garden's local 2,2; chapel east, study west, costume
                // room north, portal chamber south). High over the whole house from the south (framed in the client 2026-10-07);
                // the player waits on the lawn east of the house, out of frame.
                stand = Tile(14, -8, 0),
                from = Cam(6, -13, 2200), to = Cam(3, -9, 1700),
                lookFrom = Cam(1, 4, 0), lookTo = Cam(2, 2, 0),
                house = true,
                lines =
                    listOf(
                        line("Every player in 78 owns a house - and yours is", "already built, furnished with the finest there is."),
                        line("The ornate rejuvenation pool restores your life", "points, prayer, run energy and special attack."),
                        line("The portal nexus holds every teleport in the game.", "The garden has a spirit tree, a fairy ring and an", "obelisk, and there's a jewellery box too."),
                        line("Pray at the gilded altar, practise on the combat", "dummy, and keep your armour and costumes in the", "house's storage."),
                        line("To get here, break a house teleport tab. Your", "house is a Safe place: nobody can attack you inside."),
                    ),
            ),
            Scene(
                "the Guild Cellar",
                // Over the stations from the south-east corner (AfkBasementMapTool square 46,95); the player waits in the room's
                // south-east corner, beside the camera and out of its view (the room is the Safe zone, AfkArea MIN..MAX).
                stand = Tile(2986, 6104, 0),
                from = Cam(2988, 6097, 900), to = Cam(2985, 6101, 650),
                lookFrom = Cam(2972, 6114, 0), lookTo = Cam(2975, 6111, 0),
                lines =
                    listOf(
                        line("Below the hall lies the Guild Cellar. Take the", "cellar stairs in the hall to come down."),
                        line("Click a station and you train its skill - fifteen", "skills, from Woodcutting and Mining to Herblore", "and Thieving."),
                        line("You gain experience every moment you stay, right", "up to level 55. Go AFK as long as you like - you", "won't be logged out down here."),
                        line("Train here before you face the dangers outside.", "The cellar is a Safe place too."),
                    ),
            ),
        )
    }

    /** The closing lines, back at the Tutor. */
    private val CLOSING =
        listOf(
            line("And that's 78! Now, your quests."),
            line("Eight quests can be played here. Some choices are", "permanent: pick one quest of a group and it counts", "as done, rewards and all - the others you play", "yourself."),
            line("The Quest Helper plugin, in the 78 Plugins sidebar", "on the right, lists every step, points an arrow at", "who to talk to next and can take you there."),
            line("I've opened Choose your path for you. Good luck!"),
        )

    fun isActive(player: Player): Boolean = player.attr[ACTIVE] == true

    /** Black over the game view (client RspsCinematic curtain) until [liftCurtain] or [restore]. */
    fun curtain(player: Player) {
        player.attr[CURTAIN] = true
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|black")
    }

    /** Lifted under a fade to black, so the view stays black until that fade opens again. */
    private fun liftCurtain(player: Player) {
        if (player.attr[CURTAIN] != true) return
        player.attr.remove(CURTAIN)
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|0")
    }

    /** Starts the tour for [player] (a new account's first login, or "Show me the introduction again"). */
    fun start(player: Player) {
        if (isActive(player)) return
        player.attr[ACTIVE] = true
        // Black from the very first frame (client RspsCinematic curtain): a new account's intro starts at login, before the
        // fade to black has run, and the hall showed for a few frames (owner 2026-10-08).
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|black")
        // Owner 2026-09-23: the Dangerous-area warning exists only while the introduction runs.
        DangerWarning.startIntro(player)
        player.queue(TaskPriority.STRONG) {
            terminateAction = { finish(player, interrupted = true) }
            begin(player)
            var skipped = false
            // Owner 2026-10-08: the intro opens with the guard scene - the game's only Deadman explanation - and its world map.
            IntroGuardScene.play(this)
            val first = SCENES.firstOrNull()?.title ?: "back to the hall"
            if (options("Next: ${first.replaceFirstChar { it.uppercase() }}.", "Skip the rest of the introduction.", title = "Introduction") != 1) {
                skipped = true
            }
            for ((index, scene) in SCENES.withIndex()) {
                if (skipped) break
                if (!play(this, scene)) {
                    skipped = true
                    break
                }
                val next = SCENES.getOrNull(index + 1)?.title ?: "back to the hall"
                val choice = options("Next: ${next.replaceFirstChar { it.uppercase() }}.", "Skip the rest of the introduction.", title = "Introduction")
                if (choice != 1) {
                    skipped = true
                    break
                }
            }
            back(this, closing = !skipped)
            finish(player, interrupted = false)
            QuestGuideUi.open(player)
        }
    }

    /** Admin tuning tool (`introscene <n>`): plays one scene, then everything goes back as after the tour. */
    fun preview(player: Player, index: Int) {
        val scene = SCENES.getOrNull(index) ?: return player.message("Scenes 0..${SCENES.size - 1}.")
        player.attr[ACTIVE] = true
        player.queue(TaskPriority.STRONG) {
            terminateAction = { player.attr.remove(ACTIVE); restore(player) }
            begin(player)
            play(this, scene)
            options("Back.", "Back.", title = "Scene ${scene.title}")
            back(this, closing = false)
            player.attr.remove(ACTIVE)
        }
    }

    /** Darkan Cutscene.startCutscene: lock, out of reach, minimap blocked. */
    private fun begin(player: Player) {
        player.stopMovement()
        // Logging out stays possible (a disconnect mid-tour puts the player back in the hall); nothing can attack an
        // invisible pawn (Combat.canAttack), and other players do not see it.
        player.lock = LockState.FULL_WITH_LOGOUT
        player.invisible = true
        player.attr[DangerWarning.SUPPRESSED] = true
        player.setMinimapState(2)
    }

    suspend fun say(task: QueueTask, text: Array<String>) = tutor(task, text)

    private suspend fun tutor(task: QueueTask, text: Array<String>) {
        task.chatNpc(*text, npc = NewPlayerConfig.current.choiceNpc, title = "Quest Tutor", facialExpression = FacialExpression.CALM_TALK, wrap = false)
    }

    /** One scene: black screen, move, camera placed, back from black, the camera glides while the Tutor talks. */
    private suspend fun play(task: QueueTask, scene: Scene): Boolean {
        val player = task.player
        player.openInterface(FADE_TO_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
        task.wait(2)
        liftCurtain(player)
        val origin: Tile
        if (scene.house) {
            origin = gg.rsmod.plugins.content.areas.poh.PlayerHouse.build(player) ?: return true.also { closeFade(player) }
            player.moveTo(Tile(origin.x + scene.stand.x, origin.z + scene.stand.z, origin.height))
        } else {
            origin = Tile(0, 0, 0)
            player.moveTo(scene.stand)
        }
        // Out of the guard scene's copy: its mirror goes now (kept over the world map so the zone box stayed right).
        IntroGuardScene.releaseMap(player)
        // The map rebuild goes out with this tick's update; the camera works in the new scene's local tiles.
        task.wait(2)
        val base = player.lastKnownRegionBase ?: return true
        val bx = base.x
        val bz = base.z
        fun lx(c: Cam) = origin.x + c.x - bx
        fun lz(c: Cam) = origin.z + c.z - bz
        player.moveCameraTo(100, lx(scene.from), lz(scene.from), scene.from.height, 0)
        player.cameraLookAt(lx(scene.lookFrom), lz(scene.lookFrom), scene.lookFrom.height, 0, 100)
        task.wait(1)
        player.openInterface(FADE_FROM_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
        player.moveCameraTo(scene.moveRate, lx(scene.to), lz(scene.to), scene.to.height, scene.moveStep)
        player.cameraLookAt(lx(scene.lookTo), lz(scene.lookTo), scene.lookTo.height, scene.lookStep, scene.lookRate)
        task.wait(2)
        closeFade(player)
        scene.lines.forEach { tutor(task, it) }
        return true
    }

    /**
     * The fade windows sit in the main-screen modal slot: left open they would hold back every standard task queued in
     * front of the tour (a queued task goes first and waits while a modal is open - the Guild Cellar's welcome froze the
     * tour behind a black screen, 2026-10-07). Closed as soon as their fade has played.
     */
    fun closeFade(player: Player) {
        if (player.isInterfaceVisible(FADE_FROM_BLACK)) player.closeInterface(FADE_FROM_BLACK)
        if (player.isInterfaceVisible(FADE_TO_BLACK)) player.closeInterface(FADE_TO_BLACK)
    }

    /**
     * Back to the Tutor behind a black screen. After the whole tour the camera looks over the player's shoulder at the
     * Tutor for the closing lines; a skip goes straight back to the normal camera.
     */
    private suspend fun back(task: QueueTask, closing: Boolean) {
        val player = task.player
        player.openInterface(FADE_TO_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
        task.wait(2)
        val end = endTile()
        player.moveTo(end)
        player.faceTile(NewPlayerConfig.current.choiceNpcTile)
        task.wait(2)
        if (closing) {
            val base = player.lastKnownRegionBase
            if (base != null) {
                val tutor = NewPlayerConfig.current.choiceNpcTile
                player.moveCameraTo(100, end.x - 1 - base.x, end.z - 3 - base.z, 330, 0)
                player.cameraLookAt(tutor.x - base.x, tutor.z - base.z, 220, 0, 100)
                player.openInterface(FADE_FROM_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
                player.moveCameraTo(5, end.x - base.x, end.z - 2 - base.z, 290, 1)
                task.wait(2)
                closeFade(player)
                CLOSING.forEach { tutor(task, it) }
            }
            restore(player)
        } else {
            restore(player)
            player.openInterface(FADE_FROM_BLACK, InterfaceDestination.MAIN_SCREEN_FULL)
            task.wait(2)
            closeFade(player)
        }
    }

    /** Darkan Cutscene.stopCutscene: end tile, minimap and camera back (facing the Tutor), unlocked. */
    private fun restore(player: Player) {
        // A logout or skip during the guard scene: costume, head icons and lifepoints back (IntroGuardScene).
        if (IntroGuardScene.isActive(player)) IntroGuardScene.cleanUp(player)
        player.attr.remove(CURTAIN)
        gg.rsmod.game.model.hud.ClientHud.send(player, "cine|0")
        val end = endTile()
        if (player.tile != end) player.moveTo(end)
        IntroGuardScene.releaseMap(player)
        player.setMinimapState(0)
        player.resetCamera()
        // The player's own camera faces north, at the Tutor (client Camera.forceAngle sets the player camera too).
        player.forceCameraAngle(230, 0)
        player.invisible = false
        player.attr.remove(DangerWarning.SUPPRESSED)
        if (player.lock == LockState.FULL_WITH_LOGOUT) player.unlock()
    }

    /**
     * The tour is over - finished, skipped or interrupted (logout, disconnect, another strong action). The player is back
     * in the hall, the warning's intro window is closed and the intro counts as seen. The Quest Helper does not start by
     * itself (owner 2026-10-09: "choose your path opens but no questhelper runeliteplugin").
     */
    fun finish(player: Player, interrupted: Boolean) {
        if (!isActive(player)) return
        player.attr.remove(ACTIVE)
        if (interrupted) {
            closeFade(player)
            restore(player)
        }
        val firstTime = player.attr[SEEN] != true
        player.attr[SEEN] = true
        DangerWarning.endIntro(player)
        // Owner 2026-10-08: the Deadman starter pack is a new player's only item, handed out once the introduction is over.
        if (firstTime) gg.rsmod.plugins.content.starter.NewPlayerStart.grantStarterPack(player)
        // N212: the beginner protection's 60 minutes start now, not at account creation (hidden and paused during the intro).
        if (firstTime) gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.grantAfterIntro(player)
        if (interrupted) player.message("<col=0b6fb8>Talk to the Quest Tutor any time to watch the introduction again.</col>")
    }
}
