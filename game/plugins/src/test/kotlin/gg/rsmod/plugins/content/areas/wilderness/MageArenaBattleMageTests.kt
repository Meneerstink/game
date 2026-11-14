package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.mechanics.aggro.NpcHuntModes
import io.mockk.*
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class MageArenaBattleMageTests {
    private class Fixture {
        val definitions = mockk<DefinitionSet>()
        val world = mockk<World>(relaxed = true)
        val repo = PluginRepository(world)
        val equipment = ItemContainer(definitions, EQUIPMENT_KEY)
        val player = mockk<Player>(relaxed = true)
        init {
            every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
            every { world.definitions } returns definitions
            every { world.plugins } returns repo
            every { player.equipment } returns equipment
            every { player.isOnline } returns true
            every { player.invisible } returns false
        }
        fun script(name: String): Any = URLClassLoader(
            arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader,
        ).use { loader ->
            loader.loadClass(name).getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(repo, world, mockk<Server>(relaxed = true))
        }
        fun npc(id: Int) = Npc(id, Tile(3105, 3933), world)
    }

    @Test fun `all three actual training scripts publish120HP150tick respawn and their own four tick spell`() {
        val f = Fixture()
        for (god in listOf("Saradomin", "Guthix", "Zamorak")) {
            f.script("gg.rsmod.plugins.content.npcs.definitions.humanoids.${god}_battle_mage_lvl_54_plugin")
        }
        MageArenaBattleMages.SPELLS.forEach { (id, spell) ->
            val def = f.repo.allNpcCombatDefs().getValue(id)
            assertEquals(120, def.lifepoints)
            assertEquals(150, def.respawnDelay)
            assertEquals(4, def.attackSpeed)
            assertEquals(spell.uniqueId, def.spell)
            assertEquals(listOf(1, 1, 1, 50, 1), def.stats)
            assertFalse(def.poisonImmunity || def.venomImmunity)
        }
    }

    @Test fun `actual global acquisition check respects matching capes even after any spawn callback replaces aggroCheck`() {
        val f = Fixture()
        mockkObject(NpcHuntModes)
        try {
            every { NpcHuntModes.table } returns NpcHuntModes.Table()
            val script = f.script("gg.rsmod.plugins.content.mechanics.aggro.Npc_aggro_plugin")
            val check = script.javaClass.getMethod("canAttack", Npc::class.java, Player::class.java)
            fun attacks(npc: Npc) = check.invoke(script, npc, f.player) as Boolean
            MageArenaBattleMages.SPELLS.forEach { (id, spell) ->
                val npc = f.npc(id)
                MagicCombatFormula.CHARGE_CAPES.forEach { (capeSpell, capes) ->
                    capes.forEach { cape ->
                        f.equipment[EquipmentType.CAPE.id] = Item(cape)
                        npc.aggroCheck = { _, _ -> true }
                        assertEquals(capeSpell != spell, attacks(npc), "npc$id cape$cape")
                        npc.aggroCheck = null
                        assertEquals(capeSpell != spell, attacks(npc))
                        npc.aggroCheck = { _, _ -> false }
                        assertFalse(attacks(npc), "cape rules must not force aggression")
                    }
                }
                f.equipment[EquipmentType.CAPE.id] = Item(Items.GUTHIX_CAPE_10720)
                npc.aggroCheck = { _, _ -> true }
                assertEquals(spell != gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.CLAWS_OF_GUTHIX, attacks(npc))
                f.equipment[EquipmentType.CAPE.id] = null
                assertTrue(attacks(npc))
            }
            f.equipment[EquipmentType.CAPE.id] = Item(Items.SARADOMIN_CAPE)
            val unrelated = f.npc(14446)
            unrelated.aggroCheck = { _, _ -> true }
            assertTrue(attacks(unrelated), "Breach and other NPCs keep their own aggression")
        } finally { unmockkObject(NpcHuntModes) }
    }
}
