package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarbit
import java.io.File

/** Only verified, tool-published mappings can post scenery. Source IDs never stand in for local ones. */
object TreasureTrailStashWorld {
    private val mapping by lazy {
        val file = File(RspsPaths.data("cfg/clue/stash-mapping.tsv"))
        if (!file.isFile) emptyMap() else file.readLines().also { lines ->
            require(lines.firstOrNull()?.endsWith("applied=true") == true) { "STASH runtime mapping is not an applied transaction" }
        }.filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
            val f = line.split('\t'); require(f.size == 3)
            (f[0] to f[1].toInt()) to f[2].toInt()
        }
    }
    private val units get() = TreasureTrailStashCatalogue.current.units
    private fun local(kind: String, source: Int) = mapping[kind to source]

    fun post(world: World) {
        if (mapping.isEmpty()) return // No cache import has been applied; no guessed objects or selectors.
        // Validate all identities before posting any. A shared visual form is never a unit/save identity.
        for ((bit, unit) in units.sortedBy { it.sourceId }.withIndex()) {
            val root = local("loc", unit.sourceId) ?: error("STASH root mapping missing: ${unit.sourceId}")
            val selector = local("varbit", unit.sourceVarbit) ?: error("STASH selector mapping missing")
            val forms = unit.forms.map { local("loc", it) ?: error("STASH visual form mapping missing") } + -1
            val def = world.definitions.get(ObjectDef::class.java, root)
            val varbit = world.definitions.get(VarbitDef::class.java, selector)
            val control = local("control", units.minOf { it.sourceId }) ?: error("STASH private control missing")
            check(def.varbit == selector && def.transforms?.toList() == forms &&
                varbit.varp == control && varbit.startBit == bit && varbit.endBit == bit)
        }
        for (unit in units) {
            val id = local("loc", unit.sourceId)!!
            val chunk = world.chunks.getOrCreate(unit.tile)
            val occupant = chunk.getEntities<GameObject>(unit.tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .firstOrNull { world.locLayer(it.type) == world.locLayer(unit.type) }
            if (occupant != null && occupant.id != id) {
                // Claude 17-11 23:4x (clue-testserver: 3 of 23 units missing): OSRS Wiki STASH units - a STASH IS the bush / crate /
                // hole / rock on its tile ('Outside the Catherby bank' = the bush there). Our 2011 map still carries the plain scenery
                // (Bush 1124, Barrel 49047, Crate 14739) in the same layer, which OSRS cannot have next to the unit. Pure scenery (no
                // options) gives way to the OSRS unit; anything interactive stays and is reported.
                val sceneryOnly = world.definitions.get(ObjectDef::class.java, occupant.id).options.all { it.isNullOrBlank() || it == "hidden" }
                if (!sceneryOnly) {
                    println("PROBLEM CLUE STASH ${unit.sourceId} placement blocked by ${occupant.id} at ${unit.tile}; existing scenery retained")
                    continue
                }
                world.remove(occupant)
                world.spawn(DynamicObject(id, unit.type, unit.rotation, unit.tile))
                continue
            }
            if (occupant == null) world.spawn(DynamicObject(id, unit.type, unit.rotation, unit.tile))
        }
    }

    fun sync(player: Player) {
        if (mapping.isEmpty()) return
        units.forEach { unit -> local("varbit", unit.sourceVarbit)?.let {
            val value = if (TreasureTrailStashes.built(player, unit)) 1 else 0
            val selector = player.world.definitions.get(VarbitDef::class.java, it)
            if (player.varps.getBit(selector.varp, selector.startBit, selector.endBit) != value) player.setVarbit(it, value)
        } }
    }

    fun interact(player: Player, obj: GameObject, option: Int): Boolean {
        if (mapping.isEmpty()) return false
        val unit = units.firstOrNull { it.tile == obj.tile && it.type == obj.type && local("loc", it.sourceId) == obj.id } ?: return false
        // The server receives the base multiloc even though the client clicks its personal visual form.
        val built = TreasureTrailStashes.built(player, unit)
        if (!built && option == 1) {
            val check = TreasureTrailStashes.buildCheck(player, unit)
            if (check != TreasureTrailStashes.Result.BUILT) { report(player, unit, check); return true }
            player.queue {
                wait(unit.recipe.ticks)
                if (!obj.isSpawned(player.world) || player.tile.height != obj.tile.height ||
                    !player.tile.isWithinRadius(obj.tile, 1) || player.isDead()) return@queue
                report(player, unit, TreasureTrailStashes.build(player, unit))
                sync(player)
            }
        } else if (built && option == 2) report(player, unit, TreasureTrailStashes.search(player, unit))
        return true
    }

    private fun report(player: Player, unit: TreasureTrailStashCatalogue.Unit, result: TreasureTrailStashes.Result) {
        when (result) {
            TreasureTrailStashes.Result.LEVEL -> player.message("You need a Construction level of ${unit.recipe.level} to build this STASH unit.")
            TreasureTrailStashes.Result.TOOLS -> player.message("You need a hammer and a saw to build this STASH unit.")
            TreasureTrailStashes.Result.MATERIALS -> player.message("You don't have the materials required to build this STASH unit.")
            TreasureTrailStashes.Result.INCOMPLETE_SET -> player.message("You need the complete set of items to fill this STASH unit.")
            TreasureTrailStashes.Result.NO_SPACE -> player.message("You don't have enough inventory space to take all the items.")
            TreasureTrailStashes.Result.CORRUPT -> player.message("This STASH unit is temporarily unavailable.")
            // Claude 18-11 (client round 3): OSRS chat text per RuneLite ClueScrollPlugin.onChatMessage, which tracks STASH fills by them.
            TreasureTrailStashes.Result.STORED -> player.message("You deposit your items into the STASH unit.")
            TreasureTrailStashes.Result.WITHDRAWN -> player.message("You withdraw your items from the STASH unit.")
            else -> Unit // Building changes the object itself.
        }
    }
}
