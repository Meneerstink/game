package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.quests.QuestStatus

/**
 * PROJECT48 run 4 O18: the OSRS magic carpet network of Ali Morrisane's fleet (replaces the invented 6-station teleport list
 * of R02.3).
 *
 * Sources: OSRS Wiki "Magic carpet" + "Rug merchant" (hubs, fare, quest locks): Shantay Pass is the hub for Uzer (The Golem),
 * the Bedabin Camp and north Pollnivneach; south Pollnivneach is the hub for Nardah and Sophanem (Icthlarin's Little Helper).
 * The end stations only fly back to their hub. Fare 200 coins; a worn Ring of charos(a) lets the player charm it down to 100.
 * Dialogue lines, flight paths, take-off/landing tiles and the effect ids (anim 654 take-off / 331 land, spotanim 2931/2932/2933,
 * render 191, sounds 1196 rise / 1195 land, jingle 132) come from the Void 634 server (RugMerchant.kt + magic_carpet.patrols.toml,
 * recorded from OSRS videos), all ids checked in the 667 cache.
 *
 * Not built (no content here): Menaphos (city not in this map, wiki: the third south-hub route), the Rogue Trader price (the
 * miniquest does not exist on this server) and the free rides of the Desert Diary (no diaries). The Uzer merchant (2295) only
 * shows himself after The Golem (cache multi-npc on varbit 346 = 10), as in OSRS.
 */
val FARE = 200
val CHARM_FARE = 100
val TAKEOFF_ANIM = 654
val LAND_ANIM = 331
val TAKEOFF_GFX = 2931
val FLY_GFX = 2932
val LAND_GFX = 2933
val RENDER = 191
val RISE_SOUND = 1196
val LAND_SOUND = 1195
val JINGLE = 132

class Station(
    val key: String,
    val npcs: IntArray,
    /** Where the carpet is unrolled and the player sits down (Void tele tile). */
    val pad: Tile,
    /** The tile the player faces while sitting on the pad. */
    val face: Tile,
    /** First tiles flown after take-off, before the route. */
    val leadOut: List<Tile>,
    /** Last tiles flown after the route, before touching down on [pad]. */
    val approach: List<Tile>,
    /** The tile walked onto after the carpet is rolled up. */
    val off: Tile,
)

val SHANTAY = Station("shantay_pass", intArrayOf(Npcs.RUG_MERCHANT), Tile(3308, 3110), Tile(3308, 3109), listOf(Tile(3308, 3108)), emptyList(), Tile(3309, 3110))
val BEDABIN = Station("bedabin_camp", intArrayOf(Npcs.RUG_MERCHANT_2292), Tile(3180, 3044), Tile(3180, 3045), listOf(Tile(3180, 3043)), listOf(Tile(3181, 3046)), Tile(3181, 3045))
val POLL_NORTH = Station("north_pollnivneach", intArrayOf(Npcs.RUG_MERCHANT_2294), Tile(3349, 3003), Tile(3350, 3003), listOf(Tile(3347, 3005), Tile(3346, 3007)), listOf(Tile(3348, 3005), Tile(3350, 3004)), Tile(3351, 3003))
val UZER = Station("uzer", intArrayOf(2295, 2296), Tile(3469, 3113), Tile(3469, 3114), listOf(Tile(3466, 3112)), emptyList(), Tile(3470, 3114))
val POLL_SOUTH = Station("south_pollnivneach", intArrayOf(Npcs.RUG_MERCHANT_2293), Tile(3351, 2942), Tile(3351, 2943), listOf(Tile(3351, 2939)), emptyList(), Tile(3352, 2941))
val SOPHANEM = Station("sophanem", intArrayOf(Npcs.RUG_MERCHANT_2298, 2297), Tile(3285, 2813), Tile(3285, 2814), listOf(Tile(3285, 2816), Tile(3289, 2820)), listOf(Tile(3289, 2817), Tile(3288, 2813)), Tile(3286, 2813))
val NARDAH = Station("nardah", intArrayOf(Npcs.RUG_MERCHANT_3020), Tile(3401, 2916), Tile(3402, 2916), listOf(Tile(3400, 2916), Tile(3393, 2915)), listOf(Tile(3396, 2915), Tile(3399, 2915)), Tile(3402, 2916))

fun golem(player: Player) = QuestStatus.isCompleted(player, "The Golem")

fun ilh(player: Player) = QuestStatus.isCompleted(player, "Icthlarin's Little Helper")

/** The route options of a station: (option text, destination), quest-locked ones only when done (Void Travel menu). */
fun routes(player: Player, from: Station): List<Pair<String, Station>> =
    when (from) {
        SHANTAY ->
            listOfNotNull(
                if (golem(player)) "I want to travel to Uzer." to UZER else null,
                "I want to travel to the Bedabin camp." to BEDABIN,
                "I want to travel to Pollnivneach." to POLL_NORTH,
            )
        POLL_SOUTH ->
            listOfNotNull(
                "I want to travel to Nardah." to NARDAH,
                if (ilh(player)) "I want to travel to Sophanem." to SOPHANEM else null,
            )
        UZER, BEDABIN, POLL_NORTH -> listOf("I want to travel to Shantay Pass." to SHANTAY)
        else -> listOf("I want to travel to Pollnivneach." to POLL_SOUTH)
    }

val STATIONS = listOf(SHANTAY, BEDABIN, POLL_NORTH, UZER, POLL_SOUTH, SOPHANEM, NARDAH)

/** "Travel": straight to the destination menu, no explanation lines (Void npcOperate Travel, skip = true). */
suspend fun QueueTask.travelMenu(station: Any, npcId: Int) {
    station as Station
    val list = routes(player, station)
    val endText = if (station == SHANTAY || (station == POLL_SOUTH && ilh(player))) "I don't want to travel to any of those places." else "Actually, I've changed my mind."
    val pick = options(*(list.map { it.first } + endText).toTypedArray(), title = "Where do you wish to travel?")
    if (pick < 1 || pick > list.size) return
    pay(station, list[pick - 1].second, npcId, skip = true)
}

/** "Talk-to": Void RugMerchant Talk-to (greeting, then the station's own travel lines). */
suspend fun QueueTask.talk(station: Any, npcId: Int) {
    station as Station
    chatPlayer("Hello.")
    chatNpc("Greetings, desert traveler. Do you require the services of Ali Morrisane's flying carpet fleet?", npc = npcId)
    when (options("Yes please.", "No thanks.")) {
        1 -> {
            chatPlayer("Yes please.")
            explainRoutes(station, npcId)
        }
        else -> {
            chatPlayer("No thanks.")
            // No reply line: none in the source (no invented text).
        }
    }
}

val NOT_FOR_EVERYONE = "Fair enough, magic carpet travel isn't for everyone. Enjoy the walk."

suspend fun QueueTask.explainRoutes(station: Any, npcId: Int) {
    station as Station
    when (station) {
        POLL_SOUTH ->
            if (ilh(player)) {
                // Void: "Nardah and the Menaphite cities of Sophanem and Menaphos" - Menaphos is not on this map, so the line names
                // only what can be reached here.
                chatNpc("From here you can travel to Nardah and the Menaphite city of Sophanem.", npc = npcId)
                when (options("I want to travel to Nardah.", "I want to travel to Sophanem.", "I don't want to travel to any of those places.")) {
                    1 -> pay(station, NARDAH, npcId)
                    2 -> pay(station, SOPHANEM, npcId)
                    else -> chatNpc(NOT_FOR_EVERYONE, npc = npcId)
                }
            } else {
                chatNpc("Travel to Menaphos and Sophanem from here would be possible, but for the strange goings on there.", npc = npcId)
                chatPlayer("What do you mean?")
                chatNpc(
                    "Well, for one, the gates to both of the towns have been locked to those trying to enter and leave, so there " +
                        "really isn't any point in servicing them at the moment.",
                    npc = npcId,
                )
                chatPlayer("So, I can't take a ride on a magic carpet then?")
                chatNpc("You can still travel from here to Nardah.", npc = npcId)
                when (options("I want to travel to Nardah.", "Actually, I've changed my mind.", title = "Where do you wish to travel?")) {
                    1 -> pay(station, NARDAH, npcId)
                    else -> chatNpc(NOT_FOR_EVERYONE, npc = npcId)
                }
            }
        SHANTAY -> {
            chatNpc("From here you can travel to Uzer, to the Bedabin camp or to the North of Pollnivneach.", npc = npcId)
            chatNpc("The second major carpet hub station, to the south of Pollnivneach is in easy walking distance from there.", npc = npcId)
            val list = routes(player, station)
            val pick = options(*list.map { it.first }.toTypedArray())
            if (pick in 1..list.size) pay(station, list[pick - 1].second, npcId)
        }
        else -> {
            val (line, yes) =
                when (station) {
                    SOPHANEM -> "The carpets here will take you to the south of Pollnivneach. Do you want to take a lift?" to "Pollnivneach will do."
                    NARDAH -> "The carpets here will take you to the south of Pollnivneach." to "Let's go then."
                    POLL_NORTH -> "From here you can travel to the Shantay Pass - the Southern gate of Al Kharid." to "Take me to the Pass then."
                    UZER -> "You can travel from here back to the Shantay Pass." to "That sounds good, take me there."
                    else -> "From here you can travel to the Shantay Pass." to "Take me there."
                }
            chatNpc(line, npc = npcId)
            when (options(yes, "I don't want to travel there.")) {
                1 -> pay(station, routes(player, station).first().second, npcId)
                else -> chatNpc(NOT_FOR_EVERYONE, npc = npcId)
            }
        }
    }
}

/** The fare (Void travel(from, to, skip)): 200 coins, a worn Ring of charos(a) may charm it down to 100. */
suspend fun QueueTask.pay(from: Any, to: Any, npcId: Int, skip: Boolean = false) {
    from as Station
    to as Station
    if (!skip) chatNpc("There is a fare for this service you know : it's $FARE gold per journey.", npc = npcId)
    var price = FARE
    if (player.equipment.contains(Items.RING_OF_CHAROS_A)) {
        when (options("Here you go.", "[Charm] Seeing as you've reduced it already, how about ${CHARM_FARE}gp?", title = "Pay $price coins?")) {
            2 -> {
                chatNpc("Ah, a world-class haggler, I see. Very well, ${CHARM_FARE}gp it is.", npc = npcId)
                price = CHARM_FARE
            }
            1 -> {}
            else -> return
        }
    }
    if (player.inventory.getItemCount(Items.COINS_995) < price) {
        chatPlayer("I don't have enough money with me.")
        chatNpc("Looks like you're walking then.", npc = npcId)
        return
    }
    if (!skip) chatPlayer("Here you go.")
    val p = player
    DeadmanTimerGate.requestRoute(p, SevenSecondAction.Kind.TRANSPORT) {
        // Paid when the countdown lets the ride start, so a refused (combat) ride costs nothing.
        if (!p.inventory.remove(Items.COINS_995, price).hasSucceeded()) {
            p.message("You don't have enough coins.")
            return@requestRoute
        }
        p.queue { ride(from, to) }
    }
}

/** Every flight path of the network; a missing one is the hub path flown backwards (Bedabin has only the way back recorded). */
fun path(from: Station, to: Station): List<Tile> =
    MagicCarpetRoutes.PATHS["${from.key}_to_${to.key}"]
        ?: MagicCarpetRoutes.PATHS["${to.key}_to_${from.key}"]?.reversed()
        ?: emptyList()

/** The ride (Void shantayStart/beginTravel/patrol/land/carpetEnd): sit down, rise, fly the recorded path, land, roll up. */
suspend fun QueueTask.ride(from: Any, to: Any) {
    from as Station
    to as Station
    val p = player
    // OSRS keeps the player in the world until the carpet has landed: no actions, no logout on the way.
    // TAPIJT-AGGRO (24-11): and out of reach - OSRS Wiki "Magic carpet": rugs are used "to safely traverse the desert". With FULL,
    // aggressive desert npcs (bandits 839/840, 6051) picked the flying player and hit him (A9 live 23-11 04:13). The damage-immune
    // lock is what teleports use: Combat.canEngage refuses it, so aggro neither picks nor keeps him and no hit lands.
    p.lock = LockState.FULL_WITH_DAMAGE_IMMUNITY
    p.closeMainInterface()
    p.playJingle(JINGLE)
    p.moveTo(from.pad)
    p.faceTile(from.face)
    wait(2)
    p.animate(TAKEOFF_ANIM)
    p.graphic(TAKEOFF_GFX)
    p.playSound(RISE_SOUND)
    wait(3)
    p.setRenderAnimation(RENDER)
    p.graphic(FLY_GFX)
    val steps = java.util.ArrayDeque<Tile>()
    (from.leadOut + path(from, to) + to.approach + to.pad).forEach { steps.add(Tile(it.x, it.z, 0)) }
    p.walkPath(steps, MovementQueue.StepType.FORCED_RUN, detectCollision = false)
    var ticks = 0
    var still = 0
    var last = p.tile
    // Safety: a stalled flight (frozen, blocked: 4 ticks without a step) still lands; the longest route is 160 tiles = 80 ticks running.
    while (!p.tile.sameAs(to.pad) && ticks < 200 && still < 4) {
        wait(1)
        ticks++
        if (p.tile.sameAs(last)) still++ else still = 0
        last = p.tile
        if (ticks % 5 == 0) p.graphic(FLY_GFX)
    }
    if (!p.tile.sameAs(to.pad)) {
        p.stopMovement()
        p.moveTo(to.pad)
    }
    p.faceTile(to.face)
    p.animate(LAND_ANIM)
    p.graphic(LAND_GFX)
    p.playSound(LAND_SOUND)
    wait(4)
    p.resetRenderAnimation()
    p.animate(-1)
    p.lock = LockState.NONE
    // Step off the rolled-up carpet (Void 'off' tile). Placed, not walked: several pads (Bedabin 3180,3044) are solid tiles in the 667
    // map, so a walk from the pad never starts and the player stays stuck on it ("I can't reach that!" on every click, run 4 O18).
    p.moveTo(to.off)
}

STATIONS.forEach { station ->
    station.npcs.forEach { npcId ->
        on_npc_option(npc = npcId, option = "Travel") {
            player.queue { travelMenu(station, npcId) }
        }
        on_npc_option(npc = npcId, option = "Talk-to") {
            player.queue { talk(station, npcId) }
        }
    }
}
