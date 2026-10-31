package gg.rsmod.plugins.content.combat

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.CombatStyle
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.items.armor.EquipmentDegradation
import gg.rsmod.plugins.content.items.osrs.OsrsSeq
import gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.game.model.combat.CombatClass
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner buglist 2026-10-03 (agent-handoffs/2026-10-03-owner-buglijst.txt), player-combat items C2, D5, D6, D7, D8.
 * Each test guards the whole set the report belongs to, against the cache / items.yml / sourced values.
 */
class OwnerBuglist20261003CombatTests {
    // ---------------------------------------------------------------- D5 Vesta's longsword (+ PvP weapon family)

    /**
     * The client draws the combat tab from the cache's item param 686; the server picked styles and animations from items.yml
     * `weapon_type`. Vesta's longsword was 5 (dagger: Stab/Lunge/Slash/Block) in items.yml but 6 (Chop/Slash/Lunge/Block) in the
     * cache, so the server played the dagger lunge on Chop/Slash. Every PvP weapon (and its deg ids) must agree.
     */
    @Test
    fun `every PvP weapon's server style set is the cache style set the client shows`() {
        val pvpWeapons =
            listOf(
                Items.VESTAS_LONGSWORD, Items.VESTAS_LONGSWORD_DEG,
                Items.VESTAS_SPEAR, Items.VESTAS_SPEAR_DEG,
                Items.STATIUSS_WARHAMMER, Items.STATIUS_WARHAMMER_DEG,
                Items.ZURIELS_STAFF, Items.ZURIELS_STAFF_DEG,
                Items.MORRIGANS_JAVELIN, Items.MORRIGANS_THROWING_AXE,
            )
        val wrong =
            pvpWeapons.mapNotNull { id ->
                val cache = DEFINITIONS.get(ItemDef::class.java, id).params[686] as? Int ?: return@mapNotNull null
                val yml = YML.getValue(id).path("equipment").path("weapon_type").asInt()
                if (cache == yml) null else "$id ${YML.getValue(id).path("name").asText()}: items.yml $yml, cache 686 $cache"
            }
        assertEquals(emptyList(), wrong)
        assertEquals(6, DEFINITIONS.get(ItemDef::class.java, Items.VESTAS_LONGSWORD).params[686], "Vesta's longsword: cache sword set")
    }

    /** Void 667 [vestas_longsword] default 12311 / lunge 12310 / defend 388; Novite: Lunge box 12310, the rest 12311. */
    @Test
    fun `every Vesta's longsword id swings its own sourced sequences in every style box`() {
        val ids = listOf(Items.VESTAS_LONGSWORD, Items.VESTAS_LONGSWORD_DEG)
        ids.forEach { id ->
            val player = meleePlayer(id)
            val swings = (0..3).map { box -> player.setStyle(box); CombatConfigs.getAttackAnimation(player) }
            assertEquals(listOf(12311, 12311, 12310, 12311), swings, "$id Chop / Slash / Lunge / Block")
            assertEquals(388, CombatConfigs.getBlockAnimation(player), "$id block")
            // Chop and Slash are slash attacks, Lunge a stab - not the dagger set the old items.yml type gave it.
            player.setStyle(0)
            assertEquals(gg.rsmod.game.model.combat.StyleType.SLASH, CombatConfigs.getCombatStyle(player), "$id Chop")
            player.setStyle(2)
            assertEquals(gg.rsmod.game.model.combat.StyleType.STAB, CombatConfigs.getCombatStyle(player), "$id Lunge")
        }
        // The item row is declared after LONGSWORD: every other sword still resolves to the sword class.
        assertEquals(CombatAnimation.LONGSWORD, CombatAnimation.getItemAnimation(Items.DRAGON_LONGSWORD, WeaponType.LONG_SWORD.id))
        assertEquals(CombatAnimation.VESTAS_LONGSWORD, CombatAnimation.getItemAnimation(Items.VESTAS_LONGSWORD, WeaponType.LONG_SWORD.id))
        // The sequences the family plays exist in the game cache (Vesta's longsword 12311 / 12310, sword defend 388, spear sweep 440).
        listOf(12311, 12310, 388, 440).forEach { seq -> assertNotNull(STORE.data(20, seq ushr 7, seq and 0x7F), "seq $seq in the 667 cache") }
    }

    /** Spear Swipe: Void spear_swipe 440 = Novite "spear" style 1 440 (Vesta's spear, same class). */
    @Test
    fun `the spear class swipes with the sourced sweep`() {
        val spear = CombatAnimation.getItemAnimation(Items.VESTAS_SPEAR, WeaponType.SPEAR.id)!!
        assertEquals(440, CombatAnimation.getCorrectStyle(spear, CombatStyle.SECOND)!!.attackAnimation.id)
        assertEquals(WeaponType.SPEAR.id, YML.getValue(Items.VESTAS_SPEAR).path("equipment").path("weapon_type").asInt())
    }

    /**
     * The original cause (owner 2026-09-29, same chat as Torva): the first combat tick turns Vesta's longsword into its (deg) id, and
     * the replacement was created with amount 0 and deleted - the sword vanished from the hand the moment you attacked.
     * Guard on the real tick: the worn piece stays worn, as its next stage, with its amount.
     */
    @Test
    fun `the first combat degrade tick keeps every PvP weapon in the hand, unchanged since they no longer degrade`() {
        val pairs =
            listOf(
                Items.VESTAS_LONGSWORD to Items.VESTAS_LONGSWORD_DEG,
                Items.VESTAS_SPEAR to Items.VESTAS_SPEAR_DEG,
                Items.STATIUSS_WARHAMMER to Items.STATIUS_WARHAMMER_DEG,
                Items.ZURIELS_STAFF to Items.ZURIELS_STAFF_DEG,
            )
        val plugin = EquipmentDegradation(mockk<PluginRepository>(relaxed = true), mockk<World>(relaxed = true), mockk<Server>(relaxed = true))
        val tick = EquipmentDegradation::class.java.getDeclaredMethod("tick", Player::class.java).apply { isAccessible = true }
        pairs.forEach { (fresh, degraded) ->
            DEFINITIONS.get(ItemDef::class.java, fresh).equipSlot = EquipmentType.WEAPON.id
            DEFINITIONS.get(ItemDef::class.java, degraded).equipSlot = EquipmentType.WEAPON.id
            val world = mockk<World>(relaxed = true)
            every { world.definitions } returns DEFINITIONS
            val player = mockk<Player>(relaxed = true)
            val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
            equipment[EquipmentType.WEAPON.id] = Item(fresh)
            every { player.equipment } returns equipment
            every { player.world } returns world
            tick.invoke(plugin, player)
            val worn = equipment[EquipmentType.WEAPON.id]
            assertNotNull(worn, "$fresh vanished on the first combat tick")
            // Owner 2026-10-11 (OSRS Deadman Mode): no (deg) step any more - the piece stays exactly what it was.
            assertEquals(fresh, worn.id, "$fresh stays $fresh (not $degraded)")
            assertEquals(1, worn.amount, "$fresh amount")
        }
    }

    // ---------------------------------------------------------------- D7 Belle's folly

    /**
     * OSRS Wiki "Belle's folly": Accurate / Lunge / Slash / Block = stab / stab / slash / stab, and "the attack animation for Belle's
     * folly is identical to the Ghrazi rapier" (OSRS stab 8145, slash 390, block 4177). On this server OSRS 390 is the imported
     * HUMAN_SWORD_SLASH 15785 (batch osrsweaponseq: OSRS 390 on the merged player rig) - the same id every OSRS slash sword plays -
     * not the 667 sequence 390, so the Slash box must play 15785 and the three stab boxes the rapier's 15835.
     */
    @Test
    fun `Belle's folly attacks like the Ghrazi rapier and its sequences are in both caches`() {
        val table = (0..3).map { OsrsWeaponLooks.attackAnimation(Items.BELLES_FOLLY, it, againstNpc = false, stabStyle = it != 2) }
        assertEquals(OsrsSeq.HUMAN_SWORD_SLASH, OsrsWeaponLooks.HUMAN_SWORD_SLASH)
        assertEquals(listOf(OsrsSeq.GHRAZI_RAPIER_ATTACK, OsrsSeq.GHRAZI_RAPIER_ATTACK, OsrsSeq.HUMAN_SWORD_SLASH, OsrsSeq.GHRAZI_RAPIER_ATTACK), table)
        // Against npcs the same table (no _PVN variant for the rapier).
        assertEquals(table, (0..3).map { OsrsWeaponLooks.attackAnimation(Items.BELLES_FOLLY, it, againstNpc = true, stabStyle = it != 2) })
        assertEquals(OsrsSeq.WARGUILD_PARRY_DEFEND, OsrsWeaponLooks.blockAnimation(Items.BELLES_FOLLY))
        assertEquals(5, DEFINITIONS.get(ItemDef::class.java, Items.BELLES_FOLLY).params[686], "stab sword set (Stab/Lunge/Slash/Block)")
        assertEquals(5, YML.getValue(Items.BELLES_FOLLY).path("equipment").path("weapon_type").asInt(), "items.yml agrees with the cache set")
        val fileServer = CacheLibrary(Paths.get("..", "..", "..", "..", "file-server", "cache").toFile().toString())
        try {
            listOf(OsrsSeq.GHRAZI_RAPIER_ATTACK, OsrsSeq.HUMAN_SWORD_SLASH, OsrsSeq.WARGUILD_PARRY_DEFEND).forEach { seq ->
                val game = STORE.data(20, seq ushr 7, seq and 0x7F)
                assertNotNull(game, "seq $seq missing in the game cache")
                assertTrue(game.contentEquals(fileServer.data(20, seq ushr 7, seq and 0x7F)), "seq $seq differs in the file-server cache")
            }
        } finally {
            fileServer.close()
        }
    }

    // ---------------------------------------------------------------- C2 curse effect sounds

    /**
     * Owner 2026-09-29 "geen geluid meer bij Ancient Curses (sap/leech effect)": every Sap and Leech proc sends its cast sound at the
     * caster and its impact sound at the target one tick later (Darkan 727 PrayerManager.processLeeches: Sap 8115 / 8116, Leech
     * 8109 / 8110), and both ids are real synth sounds of this cache. Runtime 2026-10-03 20:19 (owner session): server
     * "areasound.recv id=8109 / 8110", client "sound played id=8109 positional=true", "id=8110 positional=true".
     */
    @Test
    fun `every Sap and Leech proc sends its cast and impact sound`() {
        val cases =
            listOf(
                AncientCurse.SAP_WARRIOR to CombatClass.MELEE, AncientCurse.SAP_RANGER to CombatClass.RANGED,
                AncientCurse.SAP_MAGE to CombatClass.MAGIC, AncientCurse.SAP_SPIRIT to CombatClass.MELEE,
                AncientCurse.LEECH_ATTACK to CombatClass.MELEE, AncientCurse.LEECH_STRENGTH to CombatClass.MELEE,
                AncientCurse.LEECH_RANGED to CombatClass.RANGED, AncientCurse.LEECH_MAGIC to CombatClass.MAGIC,
                AncientCurse.LEECH_DEFENCE to CombatClass.MELEE, AncientCurse.LEECH_ENERGY to CombatClass.MELEE,
                AncientCurse.LEECH_SPECIAL_ATTACK to CombatClass.MELEE,
            )
        assertEquals(AncientCurse.values().count { it.category == AncientCurse.Category.SAP || it.category == AncientCurse.Category.LEECH }, cases.size)
        cases.forEach { (curse, style) ->
            val fixture = CurseFixture()
            AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(fixture.player, curse)
            val target = mockk<Npc>(relaxed = true)
            every { target.attr } returns AttributeMap()
            every { target.tile } returns Tile(3201, 3200)
            AncientCurses.onDamageDealt(fixture.player, target, damage = 30, style = style)
            val (cast, impact) = if (curse.category == AncientCurse.Category.SAP) 8115 to 8116 else 8109 to 8110
            val sounds = fixture.sounds.map { it.id to it.delay }
            assertEquals(listOf(cast to 0, impact to 30), sounds, "$curse proc sounds")
            assertEquals(Tile(3200, 3200), fixture.sounds[0].tile, "$curse cast sound at the caster")
            assertEquals(Tile(3201, 3200), fixture.sounds[1].tile, "$curse impact sound at the target")
        }
        listOf(8109, 8110, 8115, 8116).forEach { assertNotNull(STORE.data(4, it, 0), "synth $it exists in the 667 cache") }
    }

    private class CurseFixture {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val sounds = mutableListOf<AreaSound>()

        init {
            val definitions = mockk<DefinitionSet>()
            every { definitions.get(gg.rsmod.game.fs.def.VarbitDef::class.java, any()) } answers {
                val id = secondArg<Int>()
                gg.rsmod.game.fs.def.VarbitDef(id).apply {
                    val prayer = Prayer.values.firstOrNull { it.varbit == id }
                    when {
                        id in 6820..6839 -> { varp = 1582; startBit = id - 6820 }
                        id in 6862..6881 -> { varp = 1587; startBit = id - 6862 }
                        prayer != null -> { varp = Prayers.ACTIVE_PRAYERS_VARP; startBit = prayer.slot }
                        else -> varp = id
                    }
                    endBit = startBit
                }
            }
            every { world.definitions } returns definitions
            every { world.percentChance(any()) } returns true
            every { world.spawn(any<AreaSound>()) } answers { sounds += firstArg<AreaSound>() }
            every { player.world } returns world
            every { player.tile } returns Tile(3200, 3200)
            every { player.attr } returns AttributeMap()
            every { player.varps } returns gg.rsmod.game.model.varp.VarpSet((0..8000).toSet())
            every { player.varcs } returns MutableList(2000) { 0 }
            every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 99) }
            every { player.getCurrentPrayerPoints() } returns 99
            every { player.isOnline } returns true
            every { player.isDead() } returns false
            every { player.lock.canUsePrayer() } returns true
            player.attr[AncientCurses.UNLOCKED_ATTR] = true
        }
    }

    // ---------------------------------------------------------------- D6 Dragonfire ward vs Odium ward

    /**
     * OSRS Wiki infoboxes: Odium ward ranged strength +4, Dragonfire ward (charged and uncharged) +8, Sunlight antler bolts +55. The
     * shield counts in the max hit like every other slot; the 4-point gap is worth Effective Ranged x 4 / 640 = at most one hit,
     * so with some gear sets the two wards round to the same max hit (OSRS too) - e.g. 99 Ranged, ranging potion (112), Rigour,
     * rapid, Hunters' sunlight crossbow + sunlight antler bolts: Odium 28, Dragonfire 29; with Leech Ranged (+23 %, N496) the same 28 vs 29.
     */
    @Test
    fun `Dragonfire ward carries its own ranged strength into the max hit`() {
        assertEquals(4, YML.getValue(ODIUM_WARD).path("equipment").path("ranged_strength").asInt(), "Odium ward (OSRS Wiki +4)")
        assertEquals(8, YML.getValue(DRAGONFIRE_WARD).path("equipment").path("ranged_strength").asInt(), "Dragonfire ward (OSRS Wiki +8)")
        assertEquals(55, YML.getValue(Items.SUNLIGHT_ANTLER_BOLTS).path("equipment").path("ranged_strength").asInt(), "bolts (OSRS Wiki +55)")
        mockkObject(CombatConfigs, Prayers, AncientCurses)
        try {
            every { Prayers.isActive(any(), any()) } returns false
            fun maxHit(shield: Int, rigour: Boolean, leech: Boolean): Int {
                val player = mockk<Player>(relaxed = true)
                every { player.prayerIcon } returns PrayerIcon.NONE.id
                val skills = SkillSet(maxSkills = 7).apply { setBaseLevel(Skills.RANGED, 99); setCurrentLevel(Skills.RANGED, 112) }
                every { player.skills } returns skills
                val bonuses = IntArray(18)
                listOf(Items.HUNTERS_SUNLIGHT_CROSSBOW, Items.SUNLIGHT_ANTLER_BOLTS, shield).forEach {
                    bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] += YML.getValue(it).path("equipment").path("ranged_strength").asInt()
                }
                every { player.equipmentBonuses } returns bonuses
                every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
                every { player.attr } returns AttributeMap()
                every { CombatConfigs.getAttackStyle(player) } returns WeaponStyle.RAPID
                if (rigour) every { Prayers.isActive(player, Prayer.RIGOUR) } returns true
                if (leech) {
                    every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.ANCIENT
                    every { AncientCurses.isCurseActive(player, any()) } answers { secondArg<AncientCurse>() == AncientCurse.LEECH_RANGED }
                }
                val target = mockk<Player>(relaxed = true)
                every { target.prayerIcon } returns PrayerIcon.NONE.id
                every { target.attr } returns AttributeMap()
                return RangedCombatFormula.getMaxHit(player, target, 1.0, 1.0).toInt()
            }
            assertEquals(28, maxHit(ODIUM_WARD, rigour = true, leech = false))
            assertEquals(29, maxHit(DRAGONFIRE_WARD, rigour = true, leech = false))
            // N496 (owner 2026-10-31): Leech Ranged x1.23 = Rigour (AncientCurses.LEECH_RANGED_DAMAGE; was x1.13 -> 26 / 27), so
            // without leeched levels it hits exactly what Rigour hits.
            assertEquals(28, maxHit(ODIUM_WARD, rigour = false, leech = true))
            assertEquals(29, maxHit(DRAGONFIRE_WARD, rigour = false, leech = true))
        } finally {
            unmockkObject(CombatConfigs, Prayers, AncientCurses)
        }
    }

    // ---------------------------------------------------------------- D8 owner leech decision (2026-09-29)

    /**
     * "leech ranged en leech maged structureel 1 hit verlagen, accuracy blijven, leech defence 15 %, verander ook tooltips".
     * Owner 2026-10-06 (bug 031309, Q5 answer A) replaced the Leech Magic / Leech Defence part: they boost the visible level by
     * +1 per proc like Leech Attack / Strength (AncientCurses2011Tests covers the whole set), so their tooltips are the 667
     * originals again; Leech Ranged keeps the 2026-09-29 values.
     */
    @Test
    fun `the owner's Leech balance and its tooltips are in place`() {
        assertEquals(1.20, AncientCurses.LEECH_RANGED_ACCURACY, 1e-9)
        // N496 (owner 2026-10-31): x1.23 = Rigour (was x1.13). Owner A8 2026-10-11: every tooltip states the real effect.
        assertEquals(1.23, AncientCurses.LEECH_RANGED_DAMAGE, 1e-9)
        assertTrue(Skills.MAGIC in AncientCurses.STAT_LEECHES_WITH_LEVEL_BOOST && Skills.DEFENCE in AncientCurses.STAT_LEECHES_WITH_LEVEL_BOOST)
        val texts =
            gg.rsmod.game.tools.importer.CurseTooltipCacheTool.TOOLTIPS.associate { it.struct to it.new }
        val ranged = "accuracy by ${Math.round((AncientCurses.LEECH_RANGED_ACCURACY - 1) * 100)}% and Ranged damage by " +
            "${Math.round((AncientCurses.LEECH_RANGED_DAMAGE - 1) * 100)}%"
        assertTrue(texts.getValue(899).contains(ranged), texts.getValue(899))
        val magic = "accuracy by ${Math.round((AncientCurses.LEECH_MAGIC_ACCURACY - 1) * 100)}% and Magic damage by " +
            "${Math.round(AncientCurses.LEECH_MAGIC_DAMAGE * 100)}%"
        assertTrue(texts.getValue(900).contains(magic), texts.getValue(900))
        val cap = "up to ${AncientCurses.LEECH_LEVEL_CAP} levels"
        listOf(898, 899, 900, 901, 902).forEach { assertTrue(texts.getValue(it).contains(cap), texts.getValue(it)) }
        // Owner curse table 2026-10-11: the tooltips carry the new boosts exactly.
        assertTrue(texts.getValue(898).startsWith("Boosts Attack by ${AncientCurses.LEECH_ATTACK_BOOST_PCT}%"), texts.getValue(898))
        assertTrue(texts.getValue(902).startsWith("Boosts Strength by ${AncientCurses.LEECH_STRENGTH_BOOST_PCT}%"), texts.getValue(902))
        assertTrue(texts.getValue(901).startsWith("Boosts Defence by ${AncientCurses.LEECH_DEFENCE_BOOST_PCT}%"), texts.getValue(901))
        val sap = AncientCurses.SAP_SELF_BOOST_PCT
        assertTrue(texts.getValue(889).startsWith("Boosts Attack and Strength by $sap%"), texts.getValue(889))
        assertTrue(texts.getValue(890).startsWith("Boosts Ranged accuracy and damage by $sap%"), texts.getValue(890))
        assertTrue(
            texts.getValue(891).startsWith("Boosts Magic accuracy by $sap% and Magic damage by ${Math.round(AncientCurses.SAP_MAGE_MAGIC_DAMAGE * 100)}%,"),
            texts.getValue(891),
        )
        listOf(889, 890, 891).forEach { assertTrue(texts.getValue(it).endsWith("by 10%, increasing to 20% over time."), texts.getValue(it)) }
        assertTrue(texts.getValue(904).contains("${AncientCurses.LEECH_SPECIAL_PCT}%"), texts.getValue(904))
        assertTrue(texts.getValue(897).contains("${AncientCurses.DEFLECT_REFLECT_CHANCE_PCT.toInt()}% chance"), texts.getValue(897))
        assertTrue(gg.rsmod.game.tools.importer.CurseTooltipCacheTool.plan(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString()).isEmpty(), "tooltips applied to the game cache")
        assertTrue(gg.rsmod.game.tools.importer.CurseTooltipCacheTool.plan(Paths.get("..", "..", "..", "..", "file-server", "cache").toFile().toString()).isEmpty(), "tooltips applied to the file-server cache")
    }

    // ---------------------------------------------------------------- helpers

    private fun meleePlayer(weapon: Int): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.entityType } returns EntityType.PLAYER
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        equipment[EquipmentType.WEAPON.id] = Item(weapon)
        every { player.equipment } returns equipment
        val attr = AttributeMap()
        attr[LAST_KNOWN_WEAPON_TYPE] = YML.getValue(weapon).path("equipment").path("weapon_type").asInt()
        every { player.attr } returns attr
        every { player.varps } returns gg.rsmod.game.model.varp.VarpSet((0..4000).toSet())
        every { player.world.definitions } returns DEFINITIONS
        return player
    }

    private fun Player.setStyle(box: Int) = varps.setState(Varps.ATTACK_STYLE, box)

    companion object {
        private const val ODIUM_WARD = 22888
        private const val DRAGONFIRE_WARD = 22954
        private val DEFINITIONS = DefinitionSet()
        private lateinit var STORE: CacheLibrary
        private lateinit var YML: Map<Int, JsonNode>

        @BeforeClass
        @JvmStatic
        fun load() {
            STORE = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString())
            DEFINITIONS.load(STORE, ItemDef::class.java)
            val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            YML = root.associateBy { it.path("id").asInt() }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            STORE.close()
        }
    }
}
