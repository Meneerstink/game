package gg.rsmod.plugins.content.mechanics.pvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Source contracts for every route changed by the Deadman abuse audit. Runtime timing remains an owner LIVE test. */
class DeadmanRouteGateTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `transport and portal routes use the shared seven second action`() {
        val ship = read("areas/ardougne/captain_barnaby_ship.plugin.kts")
        assertTrue(ship.contains("SevenSecondAction.Kind.TRANSPORT"))

        val poh = read("areas/poh/player_house.plugin.kts")
        assertTrue(poh.contains("SevenSecondAction.Kind.PORTAL"))

        listOf(
            "mechanics/travel/gnome_glider.plugin.kts",
            "mechanics/travel/magic_carpet.plugin.kts",
            "mechanics/travel/jatizso_neitiznot_boat.plugin.kts",
            "mechanics/travel/rellekka_boat.plugin.kts",
            "mechanics/canoes/canoes_interfaces.plugin.kts",
            "areas/portsarim/charter.plugin.kts",
            "areas/lumbridge/fremennik_shipmaster.plugin.kts",
        ).forEach { route -> assertTrue(read(route).contains("DeadmanTimerGate.requestRoute"), route) }
    }

    @Test
    fun `magical portal census uses the normal teleport gate`() {
        assertTrue(read("areas/barbarianvillage/stronghold_of_security.plugin.kts").contains("canTeleport"))
        assertTrue(read("areas/yanille/magic_guild.plugin.kts").contains("canTeleport"))
        assertTrue(read("areas/entrana/entrana.plugin.kts").contains("canTeleport(TeleportType.FAIRY)"))
        assertTrue(read("areas/zanaris/zanaris.plugin.kts").contains("canTeleport(TeleportType.FAIRY)"))
        assertTrue(read("areas/lumbridge/lostcity_shed.plugin.kts").contains("canTeleport(TeleportType.FAIRY)"))
        assertTrue(read("areas/poh/poh_furniture.plugin.kts").contains("canTeleport(TeleportType.FAIRY)"))
        assertTrue(read("objs/levers/levers.plugin.kts").contains("canTeleport(TeleportType.LEVER)"))
        assertTrue(read("areas/wilderness/wilderness_obelisk.plugin.kts").contains("canTeleport(TeleportType.WILDERNESS_OBELISK)"))
        assertTrue(read("areas/barbarianvillage/stronghold_of_security.plugin.kts").contains("canTeleport(TeleportType.SKULL_SCEPTRE)"))
        assertTrue(read("items/teleport_items.plugin.kts").contains("canTeleport(TeleportType.GRAND_SEED_POD)"))
        val summoning = read("skills/summoning/SummoningSpecialMoves.kt")
        assertTrue(summoning.contains("CALL_TO_ARMS_SCROLL"))
        assertTrue(summoning.contains("canTeleport(TeleportType.SCROLL)"))
    }

    @Test
    fun `spellbook switching and logout clear pending route callbacks`() {
        assertTrue(read("magic/Spellbooks.kt").contains("SevenSecondAction.cancel(player"))
        val lifecycle = read("mechanics/pvp/seven_second_action.plugin.kts")
        assertTrue(lifecycle.contains("on_logout"))
        assertTrue(lifecycle.contains("on_player_pre_death"))
    }

    @Test
    fun `disconnect persistence and deliberate-action cancellation live at shared boundaries`() {
        val player = File("../src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        assertTrue(player.contains("timers.has(DEADMAN_LOGOUT_TIMER)"))
        assertTrue(player.contains("timers[DEADMAN_LOGOUT_TIMER] = 12"))

        val combat = read("combat/Combat.kt")
        assertTrue(combat.contains("!BossNpcs.isBoss(pawn)"))
        assertTrue(combat.contains("!BossNpcs.isBoss(target)"))
        assertTrue(combat.contains("DEADMAN_LOGOUT_TIMER"))

        val gameSystem = File("../src/main/kotlin/gg/rsmod/game/system/GameSystem.kt").readText()
        assertTrue(gameSystem.contains("PLAYER_ACTION_INTERRUPT_ATTR"))
        assertTrue(gameSystem.contains("interrupt()"))
    }

    @Test
    fun `npc boxing releases ordinary npcs but preserves bosses and deadman guards`() {
        val combat = read("combat/combat.plugin.kts")
        assertTrue(combat.contains("boxedByOrdinaryNpc"))
        assertTrue(combat.contains("!BossNpcs.isBoss(npc)"))
        assertTrue(combat.contains("!CityGuards.isGuard(npc)"))
        assertTrue(combat.contains("npc.resetInteractions()"))
        assertTrue(combat.contains("Combat.reset(npc)"))
    }

    @Test
    fun `freeze pursuit contracts cover route lock immunity and seed displacement`() {
        val pawn = File("../src/main/kotlin/gg/rsmod/game/model/entity/Pawn.kt").readText()
        val objectPath = File("../src/main/kotlin/gg/rsmod/game/action/ObjectPathAction.kt").readText()
        assertTrue(pawn.contains("timers.has(FROZEN_TIMER)"))
        assertTrue(objectPath.contains("player.timers.has(FROZEN_TIMER)"))

        val seeds = read("items/mithril_seeds.plugin.kts")
        assertTrue(seeds.contains("Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH"))
        assertTrue(seeds.contains("world.collision.canTraverse"))
        assertTrue(seeds.contains("player.moveTo(tile.step(step))"), "seed displacement must bypass ordinary frozen walking")

        val freeze = File(content, "combat/strategy/magic/CombatSpell.kt").readText()
        listOf("SpellEffect.Freeze(8)", "SpellEffect.Freeze(16)", "SpellEffect.Freeze(24)", "SpellEffect.Freeze(32)")
            .forEach { assertTrue(freeze.contains(it), it) }
    }

    private fun read(relative: String): String = File(content, relative).readText()
}
