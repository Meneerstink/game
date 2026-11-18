package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Master only. Coordinates come from cache DB rows; the Wiki pins their guardian types. */
class TreasureTrailHotCold(val targets: List<Target>) {
    data class Target(val id: Int, val centre: Tile, val radius: Int, val guards: List<Int>) {
        fun distance(tile: Tile): Int = max(abs(tile.x - centre.x), abs(tile.z - centre.z))
        fun at(tile: Tile): Boolean = tile.height == centre.height && distance(tile) <= radius
    }
    data class Offer(val contract: ClueStepContract, val target: Target)
    data class Reading(val text: String, val damage: Int)
    enum class Issue { GIVEN, HELD, FULL, MISSING, STALE }

    init {
        require(targets.isNotEmpty() && targets.map { it.id }.distinct().size == targets.size)
        require(targets.all { it.radius == 4 && it.guards in listOf(listOf(7310), listOf(7307,7308,7309)) })
    }
    fun supports(row: TreasureTrailStepCatalogue.Step): Boolean = row.tier == ClueScrollTier.MASTER &&
        row.key == "master:jorral" && row.action == TreasureTrailStepCatalogue.Action.HOT_COLD
    fun available(world: World, row: TreasureTrailStepCatalogue.Step, guardsAvailable: (Int) -> Boolean): Boolean =
        supports(row) && targets.flatMap { it.guards }.distinct().all(guardsAvailable) &&
        ClueItems.local(19939)?.let { world.definitions.getNullable(ItemDef::class.java, it)?.name == "Strange device" } == true &&
        row.existingNpcs.any { world.definitions.getNullable(NpcDef::class.java, it)?.let { npc ->
            npc.name == "Jorral" && npc.options.any { option -> option.equals("Talk-to", true) }
        } == true }

    /** Never select a different place after banking, relogging or dropping the real scroll. */
    fun offer(player: Player, random: Random = Random.Default): Offer? {
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.MASTER) as? TreasureTrailSourceStep ?: return null
        if (!supports(step.source) || !player.isOnline) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        val saved = contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY)
        val target = if (saved == null) targets.random(random) else targets.singleOrNull { it.id == saved } ?: run {
            println("PROBLEM CLUE unknown saved hot/cold target$saved; scroll retained"); return null
        }
        if (saved == null) {
            contract.scroll.putAttr(ItemAttribute.CLUE_TASK_KEY, target.id)
            contract.scroll.removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
            player.inventory.dirty = true
        }
        return Offer(contract, target)
    }

    /** Merely talking to Jorral never replaces/advances the master clue. */
    fun issue(player: Player, offer: Offer): Issue {
        if (!offer.contract.valid(player) || offer.contract.scroll.getAttr(ItemAttribute.CLUE_TASK_KEY) != offer.target.id) return Issue.STALE
        val id = ClueItems.local(19939) ?: return Issue.MISSING
        if (player.inventory.contains(id)) return Issue.HELD
        return if (player.inventory.add(id,1,assureFullInsertion=true).hasSucceeded()) Issue.GIVEN else Issue.FULL
    }

    /** Each valid Feel emits the reading and exactly one3..8 hit, without a1HP floor. */
    fun feel(player: Player, device: Item, random: Random = Random.Default): Reading? {
        if (!player.inventory.any { it === device } || device.id != ClueItems.local(19939)) return null
        val offer = offer(player, random) ?: return null
        if (!offer.contract.valid(player)) return null
        val distance = offer.target.distance(player.tile)
        val previous = offer.contract.scroll.getAttr(ItemAttribute.CLUE_TASK_COMPLETE)?.minus(1)
        val change = when {
            previous == null -> "."
            distance < previous -> ", and warmer than last time."
            distance > previous -> ", but colder than last time."
            else -> ", and the same temperature as last time."
        }
        offer.contract.scroll.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, distance + 1)
        player.inventory.dirty = true
        return Reading("The device is ${temperature(distance)}$change",random.nextInt(3,9)).also {
            player.message(it.text)
            player.hit(it.damage)
        }
    }

    fun site(player: Player, step: TreasureTrailSourceStep): Target? {
        if (!supports(step.source)) return null
        val offer = offer(player) ?: return null
        return offer.target.takeIf { offer.contract.step === step && it.at(player.tile) }
    }

    companion object {
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/hotcold.json")).readText()) }
        fun temperature(distance: Int): String = when {
            distance >= 500 -> "ice cold"
            distance >= 200 -> "very cold"
            distance >= 150 -> "cold"
            distance >= 100 -> "warm"
            distance >= 70 -> "hot"
            distance >= 30 -> "very hot"
            distance >= 5 -> "incredibly hot"
            else -> "visibly shaking"
        }
        fun parse(text: String): TreasureTrailHotCold = TreasureTrailHotCold(JsonParser().parse(text).asJsonObject["targets"].asJsonArray.map {
            val row=it.asJsonObject; val p=row["centre"].asJsonArray
            Target(row["id"].asInt,Tile(p[0].asInt,p[1].asInt,p[2].asInt),row["radius"].asInt,row["guards"].asJsonArray.map { it.asInt })
        })
    }
}
