package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA
import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.game.model.attr.receivedStaff
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import java.lang.ref.WeakReference

/** MA1 mechanics. OSRS references and the outstanding OSRS form imports: docs/n415/RESEARCH.md. */
object MageArena {
    /**
     * The OSRS forms KOLHUMAN..KOLDEMON 1605-1609 (RuneLite gameval NpcID), imported by OsrsNpcImportTool batch mage-arena and resolved
     * through data/cfg/mage-arena/ids.tsv. [legacy] (the old 667 art, troll/dark beast for forms 3/4) only stands in while that
     * import is absent (unit tests); a release requires the mapping. Animations: each rig's casting / death / block sequence
     * (gameval HUMAN_CASTING 811, GIANT_CASTING 132, SPIDER_UPDATE_CASTING_LARGE 5322, HUMAN_TRANS_CASTING 812, DEMON_ATTACK 64).
     */
    data class Form(val upstream: Int, val legacy: Int, val hp: Int, val attack: Int, val strength: Int, val defence: Int, val magic: Int,
                    val speed: Int, val cast: Int, val death: Int, val block: Int, val legacyAnims: Triple<Int, Int, Int>) {
        val npc: Int get() = MageArenaAssets.npc(upstream) ?: legacy
        val imported: Boolean get() = MageArenaAssets.npc(upstream) != null
        fun anims(): Triple<Int, Int, Int> {
            if (!imported) return legacyAnims
            return Triple(MageArenaAssets.sequence(cast) ?: legacyAnims.first, MageArenaAssets.sequence(death) ?: legacyAnims.second,
                MageArenaAssets.sequence(block) ?: legacyAnims.third)
        }
    }

    val FORMS = listOf(
        Form(1605, Npcs.KOLODION_907, 3, 15, 10, 20, 15, 7, 811, 836, 424, Triple(811, 2888, 404)),
        Form(1606, Npcs.KOLODION_908, 65, 55, 70, 72, 50, 7, 132, 131, 129, Triple(8577, 8576, 8578)),
        Form(1607, Npcs.KOLODION_11301, 78, 69, 78, 47, 70, 7, 5322, 5321, 5320, Triple(13790, 13785, 13787)),
        Form(1608, Npcs.KOLODION_11302, 78, 28, 23, 58, 30, 7, 812, 843, 424, Triple(2731, 2733, 2732)),
        Form(1609, Npcs.KOLODION_911, 107, 85, 98, 105, 400, 4, 64, 67, 65, Triple(64, 67, 65)),
    )
    val STAFF_BY_CAPE = linkedMapOf(
        Items.SARADOMIN_CAPE to Items.SARADOMIN_STAFF,
        Items.GUTHIX_CAPE to Items.GUTHIX_STAFF,
        Items.ZAMORAK_CAPE to Items.ZAMORAK_STAFF,
    )

    /** OSRS Wiki Mage Arena I: basic god staves cannot be worn with a different god's normal cape. */
    fun compatibleGear(cape: Int?, weapon: Int?): Boolean {
        // Wearable667 Guthix duplicate (same cape/model slot in data/cfg/items.yml).
        val canonicalCape = if (cape == Items.GUTHIX_CAPE_10720) Items.GUTHIX_CAPE else cape
        val staff = STAFF_BY_CAPE[canonicalCape] ?: return true
        return weapon !in STAFF_BY_CAPE.values || weapon == staff
    }

    fun canEquip(player: Player, item: Int): Boolean = when {
        item in STAFF_BY_CAPE.values -> compatibleGear(player.getEquipment(EquipmentType.CAPE)?.id, item)
        item in STAFF_BY_CAPE || item == Items.GUTHIX_CAPE_10720 -> compatibleGear(item, player.getEquipment(EquipmentType.WEAPON)?.id)
        else -> true
    }

    /** OSRS Wiki Charge: claiming the first god staff is required, even when using an upgraded staff afterwards. */
    fun canCharge(player: Player): Boolean = MageArenaII.legacyCompleted(player) ||
        (completed(player) && player.attr[receivedStaff] == true)

    val FORM = AttributeKey<Int>(persistenceKey = "mage_arena_form")
    private val ACTIVE = AttributeKey<WeakReference<Npc>>()
    private val DUEL_FORM = AttributeKey<Int>()
    val ARRIVAL = Tile(3104, 3933)
    val FOE_TILE = Tile(3105, 3933)
    val BANK = Tile(2541, 4715)

    fun completed(player: Player): Boolean = player.attr[COMPLETED_MAGE_ARENA] == true
    fun canStart(player: Player): Boolean = !completed(player) && player.skills.getMaxLevel(Skills.MAGIC) >= 60
    fun active(player: Player): Npc? = player.attr[ACTIVE]?.get()?.takeIf { it.isSpawned() && it.isAlive() }

    /** Progress is saved at each transformation, including across retreat, death and logout. */
    fun start(player: Player): Boolean {
        if (!canStart(player)) return false
        if (active(player) != null) return false
        player.teleport(ARRIVAL, TeleportType.MODERN)
        // teleport() queues its animation; spawning/attacking before landing loses the initial attack to the lock.
        player.world.queue {
            repeat(20) {
                wait(1)
                if (!player.isOnline || player.isDead() || completed(player)) return@queue
                if (!player.isLocked() && player.tile.isWithinRadius(ARRIVAL, 2)) {
                    spawn(player, player.attr[FORM] ?: 0, FOE_TILE)
                    return@queue
                }
            }
        }
        return true
    }

    private fun spawn(player: Player, form: Int, tile: Tile) {
        if (form !in FORMS.indices || active(player) != null) return
        val foe = Npc(player, FORMS[form].npc, tile, player.world)
        foe.respawnOverride = false
        foe.attr[DUEL_FORM] = form
        installImmunity(foe)
        if (!player.world.spawn(foe)) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) { "N415 Kolodion spawn failed form=$form tile=$tile" }
            return
        }
        player.attr[ACTIVE] = WeakReference(foe)
        foe.attack(player)
        player.world.queue {
            while (foe.isSpawned() && foe.isAlive()) {
                wait(1)
                if (!player.isOnline || player.isDead() || !player.tile.isWithinRadius(foe.tile, Player.NORMAL_VIEW_DISTANCE)) {
                    if (foe.isSpawned() && foe.isAlive()) player.world.remove(foe)
                    if (player.attr[ACTIVE]?.get() === foe) player.attr.remove(ACTIVE)
                    return@queue
                }
            }
        }
    }

    /** Use the hit's captured launch style, never the source's current weapon/style at impact. */
    fun installImmunity(npc: Npc) {
        npc.hitModifier = { hit: Hit ->
            if (hit.sourceCombatClass != CombatClass.MAGIC || hit.source !== npc.owner) {
                hit.hitmarks.forEach { it.damage = 0 }
            }
        }
    }

    /** Launch-time gate too: a forbidden style must not award launch-time combat XP. */
    fun modifyIncomingDamage(npc: Npc, attacker: Pawn, type: HitType, damage: Int): Int {
        if (FORMS.none { it.npc == npc.id }) return damage
        return if (npc.owner === attacker && (type == HitType.MAGIC || type == HitType.CRIT_MAGIC)) damage else 0
    }

    /** A stale, unowned or repeated death callback cannot grant completion or skip a form. */
    fun killed(npc: Npc) {
        val player = npc.owner ?: return
        val form = npc.attr[DUEL_FORM] ?: return
        if (player.attr[ACTIVE]?.get() !== npc || (player.attr[FORM] ?: 0) != form || completed(player)) return
        if (npc.killer() !== player || !player.isOnline || player.isDead()) return
        player.attr.remove(ACTIVE)
        if (form == FORMS.lastIndex) {
            player.attr[FORM] = FORMS.size
            player.attr[COMPLETED_MAGE_ARENA] = true
            player.teleport(BANK, TeleportType.MODERN)
        } else {
            player.attr[FORM] = form + 1
            spawn(player, form + 1, npc.tile)
        }
    }

    enum class StaffResult { GRANTED, ALREADY_CLAIMED, NOT_COMPLETED, NO_CAPE, INVENTORY_FULL }

    /** All checks and the reward happen without a suspension; a failed add never consumes the one-time entitlement. */
    fun claimFirstStaff(player: Player): StaffResult {
        if (!completed(player) || player.attr[prayedAtStatue] != true) return StaffResult.NOT_COMPLETED
        if (player.attr[receivedStaff] == true) return StaffResult.ALREADY_CLAIMED
        val wornCape = player.getEquipment(EquipmentType.CAPE)?.id
        val staff = STAFF_BY_CAPE.entries.firstOrNull { (cape, _) ->
            player.inventory.contains(cape) || wornCape == cape ||
                (cape == Items.GUTHIX_CAPE && (player.inventory.contains(Items.GUTHIX_CAPE_10720) || wornCape == Items.GUTHIX_CAPE_10720))
        }?.value ?: return StaffResult.NO_CAPE
        if (!player.inventory.add(staff).hasSucceeded()) return StaffResult.INVENTORY_FULL
        player.attr[receivedStaff] = true
        return StaffResult.GRANTED
    }
}
