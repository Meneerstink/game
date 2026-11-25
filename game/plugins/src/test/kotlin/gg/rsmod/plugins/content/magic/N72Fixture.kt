package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.content.n11.N11Fixture
import gg.rsmod.plugins.content.skills.farming.data.CompostBin
import gg.rsmod.plugins.content.skills.farming.data.Patch
import io.mockk.every
import io.mockk.mockk

/** Only the N72 tests: native queues/inventories/varps with cache-free pending item definitions. */
internal class N72Fixture(book: Spellbook = Spellbook.STANDARD) : AutoCloseable {
    val f = N11Fixture()
    val player get() = f.player
    val inventory get() = f.inventory
    val skills = SkillSet(25)
    var tile = Tile(3200, 3200)
    var cycle = 100
    private val oldPatchVarbits = Patch.values().associate { it.id to it.varbit }
    private val oldBinVarbits = CompostBin.values().associate { it.id to it.varbit }
    val interfaces = InterfaceSet(mockk(relaxed = true))
    val varps: VarpSet

    init {
        every { f.definitions.get(VarbitDef::class.java, any()) } answers {
            VarbitDef(secondArg()).apply { varp = id; startBit = 0; endBit = 15 }
        }
        every { f.definitions.get(ObjectDef::class.java, any()) } answers { ObjectDef(secondArg()).apply { varbit = id } }
        Patch.initialize(f.world); CompostBin.initialize(f.world)
        val farmIds = Patch.values().flatMap { p -> listOf(p.id, p.id + if (p.id < 10000) 20000 else 200000,
            p.id + if (p.id < 10000) 30000 else 300000, p.id + if (p.id < 10000) 40000 else 400000) }
        varps = VarpSet((0..10000).toSet() + farmIds + CompostBin.values().map { it.id })
        every { player.varps } returns varps
        every { player.interfaces } returns interfaces
        every { player.skills } returns skills
        every { player.tile } answers { tile }
        every { player.moveTo(any<Tile>()) } answers { tile = firstArg() }
        every { f.world.currentCycle } answers { cycle }
        every { player.isOnline } returns true
        skills.setBaseLevel(Skills.MAGIC, 99); skills.setCurrentLevel(Skills.MAGIC, 99)
        player.setSpellbook(book)
        RunePouch.RUNES.forEach { f.def(it).stacks = true }
        MagicSpells.loadSpellRequirements()
    }

    fun runes(spell: SpellbookData, casts: Int = 1) { spell.runes.forEach { inventory.add(it.id, it.amount * casts) } }
    fun tick(count: Int = 1) { repeat(count) {
        cycle++
        f.timers.getTimers().replaceAll { _, value -> value - 1 }
        f.timers.removeIf { f.timers[it] <= 0 }
        f.queues.cycle()
    } }
    override fun close() {
        every { f.definitions.get(ObjectDef::class.java, any()) } answers {
            ObjectDef(secondArg()).apply { varbit = oldPatchVarbits[id] ?: oldBinVarbits[id] ?: -1 }
        }
        Patch.initialize(f.world); CompostBin.initialize(f.world)
        f.close()
    }
}
