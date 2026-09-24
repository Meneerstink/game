package gg.rsmod.plugins.content.combat.magic

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.tools.importer.AutocastInterfaceLayout
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.CLIENT_SPELLBOOK_VARBIT
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guard for the OSRS autocast subsystem: the cache layout <-> server spell table, the weapon/spellbook/spell registry, and the state
 * rules (retention, resets, PvP swap window, manual cast, defensive XP flag, legacy migration) on real item definitions.
 */
class AutocastTests {
    private var cycle = 1000
    private val target = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
    private val otherTarget = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)

    private fun def(id: Int): ItemDef = DEFINITIONS.get(ItemDef::class.java, id)

    private fun newPlayer(magicLevel: Int = 99): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { world.currentCycle } answers { cycle }
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.varps } returns VarpSet(VARP_IDS)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3200, 3200)
        val skills = SkillSet(maxSkills = Skills.MAGIC + 1)
        skills.setBaseLevel(Skills.MAGIC, magicLevel)
        skills.setCurrentLevel(Skills.MAGIC, magicLevel)
        every { player.skills } returns skills
        return player
    }

    private fun Player.wield(id: Int?) {
        equipment[EquipmentType.WEAPON.id] = id?.let { Item(it) }
        Autocast.onWeaponChanged(this)
    }

    @Test
    fun `every autocastable spell is in the cache layout exactly once and every layout slot is an autocastable spell`() {
        val fromLayout = AutocastInterfaceLayout.ALL.map { entry -> assertNotNull(Autocast.spellFor(entry), "${entry.name} has no autocast spell") }
        assertEquals(fromLayout.size, fromLayout.toSet().size, "a spell sits in two layout slots")
        assertEquals(Autocast.autocastable().toSet(), fromLayout.toSet(), "server autocast table and cache layout differ")
        AutocastInterfaceLayout.ALL.forEach { entry ->
            val spell = Autocast.spellFor(entry)!!
            assertEquals(entry.book, spell.interfaceId, "${entry.name} is in the wrong book grid")
        }
    }

    @Test
    fun `spell icons are the imported OSRS sprites and dim below the spell's level or without the runes`() {
        Autocast.autocastable().forEach { assertNotNull(Autocast.iconOf(it), "${it.name} has no icon") }
        // OSRS icons are imported in OSRS_ICON_ENTRIES order, lit then dark: Fire Strike is the 4th, Wind Bolt the 5th.
        val l = AutocastInterfaceLayout
        assertEquals(l.SPRITE_ICON_BASE + 2 * 3, Autocast.iconOf(CombatSpell.FIRE_STRIKE))
        assertEquals(l.SPRITE_ICON_BASE + 2 * 4, Autocast.iconOf(CombatSpell.WIND_BOLT))
        assertEquals(21, l.entryOf(192, 32)!!.osrsSprite)
        assertEquals(l.OSRS_ICON_ENTRIES.size * 2, l.OSRS_ICON_ENTRIES.map { it.sprite }.toSet().size + l.OSRS_ICON_ENTRIES.map { it.disabledSprite }.toSet().size)
        val surge = l.entryOf(192, 91)!!
        val runes = MagicSpells.getMetadata(CombatSpell.FIRE_SURGE.uniqueId)!!.runes
        fun withRunes(level: Int) = newPlayer(level).also { p -> runes.forEach { p.inventory.add(it.id, it.amount) } }
        assertEquals(surge.sprite, Autocast.listIcon(withRunes(95), surge))
        assertEquals(surge.disabledSprite, Autocast.listIcon(withRunes(94), surge))
        assertEquals(surge.disabledSprite, Autocast.listIcon(newPlayer(99), surge), "no runes -> dark icon, as in OSRS")
    }

    @Test
    fun `autocast ids are unique so a saved id resolves to one spell`() {
        val ids = Autocast.autocastable().map { it.autoCastId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CombatSpell.values.filter { it.componentId == -1 }.all { it.autoCastId == -1 }, "npc-only spells must not carry an autocast id")
    }

    @Test
    fun `registry follows the OSRS weapon rules`() {
        val air = def(Items.STAFF_OF_AIR)
        assertNull(AutocastWeapons.incompatibility(air, CombatSpell.WIND_STRIKE))
        assertNull(AutocastWeapons.incompatibility(air, CombatSpell.FIRE_SURGE))
        assertNotNull(AutocastWeapons.incompatibility(air, CombatSpell.CRUMBLE_UNDEAD), "OSRS Wiki: plain staves autocast only elemental spells")
        assertNull(AutocastWeapons.incompatibility(def(Items.SLAYERS_STAFF), CombatSpell.CRUMBLE_UNDEAD))
        assertNull(AutocastWeapons.incompatibility(def(Items.SLAYERS_STAFF), CombatSpell.FIRE_WAVE))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.SLAYERS_STAFF), CombatSpell.FIRE_BOLT), "Slayer's staff: only Wave and Surge")
        assertNull(AutocastWeapons.incompatibility(def(Items.VOID_KNIGHT_MACE), CombatSpell.CLAWS_OF_GUTHIX))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.VOID_KNIGHT_MACE), CombatSpell.WIND_STRIKE), "Void knight mace: only Wave and Surge")
        assertNotNull(AutocastWeapons.incompatibility(air, CombatSpell.ICE_BARRAGE), "a plain staff cannot autocast Ancient Magicks")
        assertNotNull(AutocastWeapons.incompatibility(air, CombatSpell.IBAN_BLAST))
        assertNotNull(AutocastWeapons.incompatibility(air, CombatSpell.MAGIC_DART))
        assertNotNull(AutocastWeapons.incompatibility(air, CombatSpell.SARADOMIN_STRIKE), "god spells need their staff")
        assertNull(AutocastWeapons.incompatibility(def(Items.ANCIENT_STAFF), CombatSpell.ICE_BARRAGE))
        assertNull(AutocastWeapons.incompatibility(def(Items.KODAI_WAND), CombatSpell.BLOOD_BLITZ))
        assertNull(AutocastWeapons.incompatibility(def(Items.IBANS_STAFF), CombatSpell.IBAN_BLAST))
        assertNull(AutocastWeapons.incompatibility(def(Items.SLAYERS_STAFF), CombatSpell.MAGIC_DART))
        assertNull(AutocastWeapons.incompatibility(def(Items.STAFF_OF_LIGHT), CombatSpell.SARADOMIN_STRIKE))
        assertNull(AutocastWeapons.incompatibility(def(Items.STAFF_OF_THE_DEAD), CombatSpell.FLAMES_OF_ZAMORAK))
        assertNull(AutocastWeapons.incompatibility(def(Items.STAFF_OF_BALANCE), CombatSpell.CLAWS_OF_GUTHIX))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.STAFF_OF_THE_DEAD), CombatSpell.ICE_BARRAGE))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.HARMONISED_NIGHTMARE_STAFF), CombatSpell.ICE_BARRAGE))
        assertNull(AutocastWeapons.incompatibility(def(Items.HARMONISED_NIGHTMARE_STAFF), CombatSpell.FIRE_SURGE))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.ZURIELS_STAFF), CombatSpell.ICE_BARRAGE), "Zuriel's staff is not in the OSRS Ancient Magicks table")
        assertNull(AutocastWeapons.incompatibility(def(Items.ZURIELS_STAFF), CombatSpell.MIASMIC_BARRAGE))
        assertNotNull(AutocastWeapons.incompatibility(def(Items.ANCIENT_STAFF), CombatSpell.MIASMIC_BARRAGE))
        assertFalse(AutocastWeapons.isAutocastWeapon(def(Items.TRIDENT_OF_THE_SEAS)), "powered staves never autocast")
        assertFalse(AutocastWeapons.isAutocastWeapon(def(Items.ABYSSAL_WHIP)))
        assertFalse(AutocastWeapons.isAutocastWeapon(def(Items.MAGIC_SHORTBOW)))
    }

    @Test
    fun `every magic weapon on the server is classified and none can autocast a spell it may not cast manually`() {
        val weapons = DEFINITIONS.getAllKeys(ItemDef::class.java).mapNotNull { id -> DEFINITIONS.getNullable(ItemDef::class.java, id) }
            .filter { !it.noted && AutocastWeapons.isAutocastWeapon(it) }
        assertTrue(weapons.size > 30, "expected the staves, wands and sceptres of the server, found ${weapons.size}")
        weapons.forEach { weapon ->
            Autocast.autocastable().forEach { spell ->
                if (AutocastWeapons.incompatibility(weapon, spell) == null) {
                    val required = AutocastWeapons.requiredWeapons(spell)
                    assertTrue(required.isEmpty() || weapon.id in required, "${weapon.name} autocasts ${spell.name} without its weapon")
                    if (spell.interfaceId == Autocast.ANCIENT_BOOK && required.isEmpty()) assertTrue(weapon.id in AutocastWeapons.ANCIENT_WEAPONS, "${weapon.name} autocasts ${spell.name}")
                }
            }
        }
    }

    @Test
    fun `selection survives missing runes, low level and melee weapons, and resumes by itself`() {
        val player = newPlayer(magicLevel = 1)
        player.wield(Items.STAFF_OF_AIR)
        Autocast.select(player, CombatSpell.FIRE_BOLT, Autocast.Mode.STANDARD)
        val metadata = MagicSpells.getMetadata(CombatSpell.FIRE_BOLT.uniqueId)
        if (metadata != null) assertFalse(MagicSpells.canCast(player, metadata.lvl, metadata.runes, spellId = CombatSpell.FIRE_BOLT.uniqueId))
        assertEquals(CombatSpell.FIRE_BOLT, Autocast.selected(player), "level/runes never clear the choice")
        assertEquals(CombatSpell.FIRE_BOLT, Autocast.resolve(player), "still the autocast spell - the engine refuses the cast, no staff bash")

        player.wield(Items.ABYSSAL_WHIP)
        assertEquals(CombatSpell.FIRE_BOLT, Autocast.selected(player), "a melee weapon keeps the choice")
        assertNull(Autocast.resolve(player), "but the whip attacks with melee")
        player.wield(Items.STAFF_OF_AIR)
        assertEquals(CombatSpell.FIRE_BOLT, Autocast.resolve(player), "back on the staff, autocast resumes")
    }

    @Test
    fun `leaving autocast gives back a style the new weapon has - the hidden 4th box never reaches melee`() {
        val player = newPlayer()
        player.wield(Items.STAFF_OF_AIR)
        player.setVarp(AttackTab.ATTACK_STYLE_VARP, 1)
        Autocast.select(player, CombatSpell.WIND_STRIKE, Autocast.Mode.STANDARD)
        assertEquals(Autocast.AUTOCAST_STYLE, player.getVarp(AttackTab.ATTACK_STYLE_VARP))
        player.wield(Items.ABYSSAL_WHIP)
        assertEquals(1, player.getVarp(AttackTab.ATTACK_STYLE_VARP), "the whip gets the style from before autocast, not the hidden 4th box")
        player.setVarp(AttackTab.ATTACK_STYLE_VARP, 3)
        Autocast.sync(player)
        assertEquals(2, player.getVarp(AttackTab.ATTACK_STYLE_VARP), "a 3-style weapon on the 4th box falls back to the 3rd")
        player.wield(null)
        assertTrue(player.getVarp(AttackTab.ATTACK_STYLE_VARP) in 0..2, "unarmed has three styles")
    }

    @Test
    fun `every weapon style table lists each box once, so style lookups never fall through`() {
        gg.rsmod.plugins.content.combat.WeaponCombatData.values().forEach { data ->
            val boxes = data.style.map { it.combatStyle }
            assertEquals(boxes.size, boxes.toSet().size, "${data.name} lists a box twice")
            assertEquals(boxes.indices.toList(), boxes.map { it.id }, "${data.name} boxes must be consecutive from the 1st")
        }
    }

    @Test
    fun `an incompatible staff, a powered staff and a spellbook change forget the choice`() {
        val player = newPlayer()
        player.wield(Items.IBANS_STAFF)
        Autocast.select(player, CombatSpell.IBAN_BLAST, Autocast.Mode.STANDARD)
        player.wield(Items.STAFF_OF_AIR)
        assertNull(Autocast.selected(player))

        player.wield(Items.STAFF_OF_AIR)
        Autocast.select(player, CombatSpell.WIND_BOLT, Autocast.Mode.STANDARD)
        player.wield(Items.TRIDENT_OF_THE_SEAS)
        assertNull(Autocast.selected(player), "powered staves cannot autocast")

        player.wield(Items.ANCIENT_STAFF)
        Autocast.select(player, CombatSpell.WIND_BOLT, Autocast.Mode.STANDARD)
        player.setSpellbook(Spellbook.ANCIENT)
        Autocast.onSpellbookChanged(player)
        assertNull(Autocast.selected(player))
    }

    @Test
    fun `pvp swap window counts only the player's own attacks and is configurable`() {
        val player = newPlayer()
        player.wield(Items.STAFF_OF_AIR)
        Autocast.select(player, CombatSpell.WIND_WAVE, Autocast.Mode.STANDARD)
        Autocast.onOutgoingPlayerAttack(player)
        cycle += AutocastPolicy.pvpSwapWindowTicks() + 1
        player.wield(Items.ABYSSAL_WHIP)
        player.wield(Items.STAFF_OF_AIR)
        assertEquals(CombatSpell.WIND_WAVE, Autocast.selected(player), "21 ticks after the last own attack the swap keeps autocast")

        Autocast.onOutgoingPlayerAttack(player)
        cycle += 5
        player.wield(Items.ABYSSAL_WHIP)
        assertEquals(CombatSpell.WIND_WAVE, Autocast.selected(player), "equipping a melee weapon is not the rule's staff equip")
        player.wield(Items.STAFF_OF_AIR)
        assertNull(Autocast.selected(player), "a staff equipped within 20 ticks of an own player attack on a PvP tile clears it")

        val saved = AutocastPolicy.permanentDeadmanSwapWindowTicks
        try {
            AutocastPolicy.permanentDeadmanSwapWindowTicks = 3
            Autocast.select(player, CombatSpell.WIND_WAVE, Autocast.Mode.STANDARD)
            Autocast.onOutgoingPlayerAttack(player)
            cycle += 5
            player.wield(Items.STAFF_OF_FIRE)
            assertEquals(CombatSpell.WIND_WAVE, Autocast.selected(player))
        } finally {
            AutocastPolicy.permanentDeadmanSwapWindowTicks = saved
        }
    }

    @Test
    fun `melee style switches autocast off but keeps the spell, manual casts never touch it`() {
        val player = newPlayer()
        player.wield(Items.STAFF_OF_AIR)
        Autocast.select(player, CombatSpell.EARTH_BLAST, Autocast.Mode.DEFENSIVE)
        assertEquals(Autocast.AUTOCAST_STYLE, player.getVarp(AttackTab.ATTACK_STYLE_VARP))
        assertEquals(CombatSpell.EARTH_BLAST.autoCastId, player.getVarp(Combat.SELECTED_AUTOCAST_VARP))

        Autocast.markManualCast(player, CombatSpell.WIND_STRIKE, target)
        Autocast.prepareAttack(player, target)
        assertEquals(CombatSpell.WIND_STRIKE, player.attr[Combat.CASTING_SPELL], "a pending manual cast wins this attack")
        assertFalse(Autocast.isDefensiveCast(player), "manual casts give the offensive XP split")
        assertEquals(CombatSpell.EARTH_BLAST, Autocast.selected(player))

        Autocast.markManualCast(player, CombatSpell.WIND_STRIKE, target)
        Autocast.prepareAttack(player, otherTarget)
        assertEquals(CombatSpell.EARTH_BLAST, player.attr[Combat.CASTING_SPELL], "an interrupted manual cast never fires at another target")

        player.attr.remove(Combat.CASTING_SPELL)
        Autocast.prepareAttack(player, target)
        assertEquals(CombatSpell.EARTH_BLAST, player.attr[Combat.CASTING_SPELL])
        assertTrue(Autocast.isDefensiveCast(player))

        player.setVarp(AttackTab.ATTACK_STYLE_VARP, 0)
        Autocast.deactivate(player, Autocast.Reason.MELEE_STYLE)
        assertEquals(CombatSpell.EARTH_BLAST, Autocast.selected(player))
        assertNull(Autocast.resolve(player))
        Autocast.prepareAttack(player, target)
        assertNull(player.attr[Combat.CASTING_SPELL], "a stale autocast spell never fires after autocast is switched off")
        assertEquals(0, player.getVarp(Combat.SELECTED_AUTOCAST_VARP))
    }

    @Test
    fun `switching weapons mid-fight drops the loaded autocast spell`() {
        val player = newPlayer()
        player.wield(Items.STAFF_OF_AIR)
        Autocast.select(player, CombatSpell.WIND_STRIKE, Autocast.Mode.STANDARD)
        Autocast.prepareAttack(player, target)
        assertEquals(CombatSpell.WIND_STRIKE, player.attr[Combat.CASTING_SPELL])
        player.wield(Items.ABYSSAL_WHIP)
        assertNull(player.attr[Combat.CASTING_SPELL], "no spell may fire from the whip")
    }

    @Test
    fun `legacy varp save migrates and the spellbook bits of varp 439 survive`() {
        val player = newPlayer()
        player.setSpellbook(Spellbook.ANCIENT)
        player.setVarp(Combat.SELECTED_AUTOCAST_VARP, CombatSpell.ICE_BARRAGE.autoCastId)
        player.setVarp(Autocast.LEGACY_DEFENSIVE_VARP, player.getVarp(Autocast.LEGACY_DEFENSIVE_VARP) or Autocast.LEGACY_DEFENSIVE_BIT)
        Autocast.migrateLegacy(player)
        assertEquals(CombatSpell.ICE_BARRAGE, Autocast.selected(player))
        assertEquals(Autocast.Mode.DEFENSIVE, Autocast.mode(player))
        assertEquals(0, player.getVarp(Autocast.LEGACY_DEFENSIVE_VARP) and Autocast.LEGACY_DEFENSIVE_BIT)
        assertEquals(Spellbook.ANCIENT.id, DEFINITIONS.get(VarbitDef::class.java, CLIENT_SPELLBOOK_VARBIT).let { player.varps.getBit(it.varp, it.startBit, it.endBit) })
    }

    @Test
    fun `login forgets a saved choice the weapon or spellbook can no longer autocast`() {
        val player = newPlayer()
        player.wield(Items.ANCIENT_STAFF)
        player.setSpellbook(Spellbook.ANCIENT)
        Autocast.select(player, CombatSpell.ICE_BARRAGE, Autocast.Mode.STANDARD)
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.STAFF_OF_AIR)
        Autocast.revalidate(player)
        assertNull(Autocast.selected(player))
        assertEquals(0, player.getVarp(AttackTab.ATTACK_STYLE_VARP), "the staff is back on a real style")
    }

    @Test
    fun `equipping never touches the attack timer and the old varp writers are gone`() {
        val sources = File("src/main/kotlin/gg/rsmod/plugins").walkTopDown().filter { it.isFile && it.readText().contains("ATTACK_DELAY] =") }.map { it.name }.toSet()
        // Only attacks (and eating - Foods, consumables - / Granite maul, which OSRS delays too) write the attack timer - never equip, spell selection or UI.
        assertEquals(setOf("Combat.kt", "TormentedDemonCombatScript.kt", "Foods.kt", "consumables.plugin.kts", "GraniteMaul.kt"), sources)
    }

    @Test
    fun `no legacy path writes autocast state any more`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins")
        val offenders = root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.name.endsWith(".kts")) }
            .filter { it.name != "Autocast.kt" }
            .filter { file -> file.readText().let { "setVarp(Combat.SELECTED_AUTOCAST_VARP" in it || "setVarp(108" in it || "SELECTED_AUTOCAST_VARP, 0" in it } }
            .map { it.name }.toList()
        assertEquals(emptyList(), offenders)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var VARP_IDS: Set<Int>

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
            val varbitVarps = listOf(Varbits.SPELLBOOK, CLIENT_SPELLBOOK_VARBIT, MagicSpells.INF_RUNES_VARBIT).map { DEFINITIONS.get(VarbitDef::class.java, it).varp }
            VARP_IDS = (varbitVarps + listOf(Combat.SELECTED_AUTOCAST_VARP, AttackTab.ATTACK_STYLE_VARP, Autocast.LEGACY_DEFENSIVE_VARP)).toSet()
        }
    }
}
