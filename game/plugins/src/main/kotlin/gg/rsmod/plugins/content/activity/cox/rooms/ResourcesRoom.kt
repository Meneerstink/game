package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The resource rooms (RESOURCES_A / _B) and the raid's gathering. OSRS Wiki 'Chambers of Xeric/Food': resource rooms have a 50% chance of
 * bats and a 50% chance of fishing spots (here: room A fish, room B bats, as the donor); fish need a fishing rod and cave worms, bats an insect
 * net (= butterfly net 10010) or 99 Hunter barehanded; fish/bats tiers 0-6: level 1/15/30/45/60/75/90, catch XP fish 20-38 (+3 a tier), bats
 * 5-29 (+4), cooked XP 10-28 (+3), heal 5/8/11/14/17/20/23; one of three fishing spots holds a cave snake that bites and steals food.
 * Code help + ONZEKER values: tsps Resources.ChambersOfXeric.js (snake spot moves every 50 ticks or after 10 rises, catch chance
 * min(round((level-req) x 0.6) + 30, 70)% every 4 ticks; 2 + raiders/8 bats (max 6) of one type among the three best the top Hunter allows,
 * catch chance (floor(306 x (level-1)/98) - 78)/255, anims 6606/6605, a caught bat back 2 ticks later; snake rises 7333, bites 3-6 at 3 ticks
 * and takes one food, dives 4039 at 5).
 */
class ResourcesRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val fishing = laid.room == gg.rsmod.plugins.content.activity.cox.CoxRoom.RESOURCES_A
    private var spotIndex = 0
    private var ticks = 0
    private var rises = 0
    private var snake: Npc? = null
    lateinit var fishingSpot: Tile
    private lateinit var snakeSpot: Tile
    private val batType = HashMap<Npc, CoxSupplies.Catch>()

    override fun load() {
        if (fishing) moveSnake(world.random(2)) else loadBats()
    }

    private fun loadBats() {
        val members = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        val hunter = maxOf(1, members.maxOfOrNull { it.skills.getMaxLevel(Skills.HUNTER) } ?: 1)
        val type = CoxSupplies.BATS.filter { it.level <= hunter }.takeLast(3).randomOrNull() ?: CoxSupplies.BATS[0]
        val count = minOf(6, 2 + scaling().size / 8)
        repeat(count) { spawnBat(type, BAT_SPOTS[variant].random().let { tile(it.x, it.z) }) }
    }

    private fun spawnBat(type: CoxSupplies.Catch, at: Tile) { spawn(type.npc, at)?.let { batType[it] = type } }

    private fun moveSnake(index: Int) {
        spotIndex = index
        fishingSpot = tile(FISHING[variant][index].x, FISHING[variant][index].z)
        snakeSpot = tile(SNAKE[variant][index].x, SNAKE[variant][index].z)
    }

    override fun onTick() {
        if (!fishing) return
        if (++ticks >= 50 || rises >= 10) { ticks = 0; rises = 0; moveSnake(listOf(0, 1, 2).filter { it != spotIndex }.random()) }
    }

    fun isSnakeSpot(t: Tile) = fishing && t.x == fishingSpot.x && t.z == fishingSpot.z

    fun riseSnake() {
        if (snake != null) return
        rises++
        val s = spawn(7594, snakeSpot.transform(-1, -1)) ?: return
        snake = s
        s.faceTile(fishingSpot)
        animate(s, 7333)
        later(3) {
            world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) && it.tile.getDistance(fishingSpot) <= 1 }.forEach { p ->
                damage(s, p, world.random(3..6))
                val food = CoxSupplies.FOOD.keys.mapNotNull { CoxIds.item(it) }.firstOrNull { p.inventory.contains(it) }
                if (food != null) { p.inventory.remove(food, 1); p.message("The snake stole some food off you!") }
            }
        }
        later(5) { animate(s, 4039); later(2) { remove(s); snake = null } }
    }

    fun catchBat(p: Player, bat: Npc) {
        val type = batType[bat] ?: return
        val net = p.getEquipment(EquipmentType.WEAPON)?.id == 10010
        val level = p.skills.getCurrentLevel(Skills.HUNTER)
        if (!net && level < 99) { p.queue { messageBox("You need a butterfly net to catch these bats.") }; return }
        if (level < type.level) { p.message("You need a Hunter level of at least ${type.level} to catch this bat."); return }
        if (p.inventory.freeSlotCount <= 0) { p.message("You need some free inventory space to catch the bat."); return }
        val chance = (306 * (level - 1) / 98 - 78) / 255.0
        val caught = world.randomDouble() < chance
        val name = world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, bat.id).name.lowercase()
        animatePlayer(p, if (caught) 6606 else 6605)
        play(p, 2623)
        later(1) {
            if (!caught || !alive(bat)) { p.message("You fail to catch the $name."); return@later }
            p.message("You successfully catch the $name.")
            play(p, 293)
            CoxIds.item(type.raw)?.let { p.inventory.add(it) }
            p.addXp(Skills.HUNTER, type.xp)
            val at = gg.rsmod.game.model.Tile(bat.tile)
            batType.remove(bat); remove(bat)
            later(2) { spawnBat(type, at) }
        }
    }

    companion object {
        val BAT_SPOTS = listOf(
            listOf(Tile(3282, 5463), Tile(3288, 5461), Tile(3285, 5457)),
            listOf(Tile(3308, 5455), Tile(3301, 5458), Tile(3305, 5462)),
            listOf(Tile(3348, 5455), Tile(3344, 5459), Tile(3343, 5464)),
        )
        val FISHING = listOf(
            listOf(Tile(3274, 5457), Tile(3280, 5451), Tile(3281, 5454)),
            listOf(Tile(3302, 5459), Tile(3308, 5449), Tile(3306, 5461)),
            listOf(Tile(3337, 5453), Tile(3345, 5445), Tile(3343, 5461)),
        )
        val SNAKE = listOf(
            listOf(Tile(3275, 5457), Tile(3279, 5451), Tile(3280, 5454)),
            listOf(Tile(3302, 5458), Tile(3309, 5449), Tile(3306, 5460)),
            listOf(Tile(3338, 5453), Tile(3345, 5446), Tile(3343, 5460)),
        )
    }
}
