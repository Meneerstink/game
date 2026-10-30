package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.combat.isBeingAttacked

/**
 * N423 Beast of burden trinkets (owner 2026-10-29, design v6 - team\dossiers\GOEDGEKEURDE-ONTWERPEN.txt punt 8): bound to a pet,
 * the pet carries items like the familiar the trinket is named after and is called "Upgraded <name>".
 *
 * - The trinket is never used up: it stays an item (bank, inventory), untradeable, kept on every death, free back from Perdu.
 * - A binding belongs to the PET on the account (its [Pets.Variant.pet], so every Metamorphosis form keeps it), not to one item:
 *   the items carry no per-copy data. Several trinkets - also two of the same kind - may each be bound to a different pet; a
 *   binding counts only while the player still owns enough trinkets of that kind ([effective]).
 * - Bind only to the pet that is following (that is also "use the trinket on your pet"), never in combat; a bigger trinket replaces
 *   a smaller one (the smaller stays yours), a smaller one on a bigger is refused. Unbind only with an empty pack.
 */
object BurdenTrinkets {
    enum class Tier(
        val item: Int,
        val trinketName: String,
        val slots: Int,
        /** The beast of burden it is named after ("just like a Pack yak"). */
        val familiar: String,
        /** Examine: "The spirit of a <spirit>, sealed in stone." */
        val spirit: String,
    ) {
        KALPHITE(24232, "Kalphite burden trinket", 6, "Spirit kalphite", "kalphite"),
        BULL_ANT(24233, "Bull ant burden trinket", 9, "Bull ant", "bull ant"),
        TERRORBIRD(24234, "Terrorbird burden trinket", 12, "Spirit terrorbird", "terrorbird"),
        TORTOISE(24235, "Tortoise burden trinket", 18, "War tortoise", "war tortoise"),
        PACK_YAK(24236, "Pack yak burden trinket", 30, "Pack yak", "pack yak"),
    }

    private val byItem = Tier.values().associateBy { it.item }

    fun tier(item: Int): Tier? = byItem[item]

    fun isTrinket(item: Int): Boolean = item in byItem

    /** The purple of the Deadman's Chest broadcast (design v6: the unlock line and the examine line). */
    const val PURPLE = gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld.BROADCAST_COLOUR

    /** The Summoning obelisk's renew chime (Void renewSummoningPoints, synth 4214): the bind's "premium" sound. */
    const val BIND_SOUND = 4214

    /** A short glow on the pet when the trinket binds. */
    const val BIND_GRAPHIC = 1517

    /** pet -> trinket item, "pet=item;pet=item" (pet names hold neither ';' nor '='). */
    val BOUND_ATTR = AttributeKey<String>(persistenceKey = "burden_trinkets")

    /** Every trinket kind the player ever had (item ids), so Perdu can give a lost one back for free. */
    val OWNED_ATTR = AttributeKey<String>(persistenceKey = "burden_trinkets_owned")

    fun decode(text: String?): Map<String, Int> =
        text.orEmpty().split(';').mapNotNull { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) return@mapNotNull null
            val item = entry.substring(at + 1).trim().toIntOrNull()?.takeIf { isTrinket(it) } ?: return@mapNotNull null
            entry.substring(0, at) to item
        }.toMap()

    fun encode(bound: Map<String, Int>): String = bound.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    fun bindings(player: Player): Map<String, Int> = decode(player.attr[BOUND_ATTR])

    private fun store(
        player: Player,
        bound: Map<String, Int>,
    ) {
        if (bound.isEmpty()) player.attr.remove(BOUND_ATTR) else player.attr[BOUND_ATTR] = encode(bound)
    }

    /** How many of [item] the player has anywhere (every container, the Grand Exchange is impossible: untradeable). */
    fun owned(
        player: Player,
        item: Int,
    ): Int = gg.rsmod.plugins.content.mechanics.ownership.Possessions.count(player, item)

    /**
     * The bindings that count: of each kind, as many as the player owns trinkets of it, in pet-name order (a trinket that is
     * somehow gone - before Perdu gives it back - takes its upgrade with it; returned, the upgrade is back).
     */
    fun effective(
        bound: Map<String, Int>,
        ownedOf: (Int) -> Int,
    ): Map<String, Int> =
        bound.entries.groupBy { it.value }.flatMap { (item, entries) -> entries.sortedBy { it.key }.take(ownedOf(item).coerceAtLeast(0)) }
            .associate { it.key to it.value }

    /** The trinket that upgrades [pet] (a [Pets.Variant.pet] key) right now, or null. */
    fun upgradeOf(
        player: Player,
        pet: String,
    ): Tier? {
        val bound = bindings(player)
        val item = bound[pet] ?: return null
        return if (pet in effective(bound) { owned(player, it) }) tier(item) else null
    }

    /** The trinket of the pet that is out (its pack size), or null. */
    fun followerTier(player: Player): Tier? {
        val item = Pets.itemOf(player) ?: return null
        val pet = Pets.variantOfItem(item)?.pet ?: return null
        return upgradeOf(player, pet)
    }

    /** The name a pet goes by while upgraded (design v6: for everyone - above the pet, the menu, Follower Details). */
    fun upgradedName(name: String): String = "Upgraded $name"

    /** The name the player knows [pet] by: the follower's own name (N427: "Zilyana Jr.", not the item's "Pet Zilyana") of the form that is out, else of its first form. */
    fun petName(
        player: Player,
        pet: String,
    ): String {
        val out = Pets.itemOf(player)?.let { Pets.variantOfItem(it) }?.takeIf { it.pet == pet }
        val variant = out ?: Pets.variantsOf(pet).firstOrNull() ?: return pet
        return followerName(player, variant)
    }

    fun followerName(
        player: Player,
        variant: Pets.Variant,
    ): String =
        runCatching { player.world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, variant.npc)?.name }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: variant.name

    private fun shownName(
        player: Player,
        pet: String,
    ): String = petName(player, pet).let { if (upgradeOf(player, pet) != null) upgradedName(it) else it }

    // ---- Check ----------------------------------------------------------------------------------------------------------------

    fun checkText(
        player: Player,
        tier: Tier,
    ): String {
        val pets = effective(bindings(player)) { owned(player, it) }.filterValues { it == tier.item }.keys.sorted()
        return when (pets.size) {
            0 -> "Your ${tier.trinketName} is not bound to a pet."
            1 -> "Your ${tier.trinketName} is bound to your ${shownName(player, pets[0])} (${tier.slots} storage)."
            else -> "Your ${tier.trinketName}s are bound to your ${joinNames(pets.map { shownName(player, it) })} (${tier.slots} storage each)."
        }
    }

    private fun joinNames(names: List<String>): String = if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()

    // ---- Bind -----------------------------------------------------------------------------------------------------------------

    sealed class BindCheck {
        /** May bind; [replaces] is the smaller trinket the pet has now. */
        data class Ok(val pet: String, val name: String, val replaces: Tier?) : BindCheck()

        data class Refused(val message: String) : BindCheck()
    }

    fun inCombat(player: Player): Boolean = player.isBeingAttacked() || player.isAttacking()

    fun canBind(
        player: Player,
        tier: Tier,
    ): BindCheck {
        if (inCombat(player)) return BindCheck.Refused("You can't do that during combat.")
        val item = Pets.itemOf(player)
        val variant = item?.let { Pets.variantOfItem(it) }
        if (Pets.current(player) == null || variant == null) return BindCheck.Refused("A burden trinket can only be bound to the pet that is following you.")
        val pet = variant.pet
        val now = upgradeOf(player, pet)
        val name = followerName(player, variant)
        if (now == tier) return BindCheck.Refused("Your ${upgradedName(name)} already carries a ${tier.trinketName}.")
        if (now != null && now.slots > tier.slots) return BindCheck.Refused("Your ${upgradedName(name)} already carries a bigger trinket: the ${now.trinketName}.")
        // Every copy of this kind may be bound to another pet already.
        val inUse = effective(bindings(player)) { owned(player, it) }.filter { it.value == tier.item && it.key != pet }.keys.sorted()
        if (inUse.size >= owned(player, tier.item)) {
            return BindCheck.Refused("Your ${tier.trinketName} is already bound to your ${shownName(player, inUse.first())}. Unbind it first.")
        }
        return BindCheck.Ok(pet, followerName(player, variant), now)
    }

    fun bindQuestion(
        tier: Tier,
        ok: BindCheck.Ok,
    ): String =
        "Bind this trinket to your ${ok.name}? It will carry ${tier.slots} items for you - just like a ${tier.familiar}. " +
            (if (ok.replaces != null) "It replaces the ${ok.replaces.trinketName}, which stays yours. " else "") +
            "You keep the trinket and can unbind it at any time."

    /** The bind itself, after the player said yes (checked again: the pet may have gone meanwhile). */
    fun bind(
        player: Player,
        tier: Tier,
    ): Boolean {
        val ok =
            when (val check = canBind(player, tier)) {
                is BindCheck.Refused -> {
                    player.message(check.message)
                    return false
                }
                is BindCheck.Ok -> check
            }
        // A bigger trinket takes the pack along (the smaller pack's items always fit the bigger one).
        ok.replaces?.let { BeastOfBurden.moveUpgradedCargo(player, it, tier) }
        val bound = bindings(player).toMutableMap()
        bound[ok.pet] = tier.item
        store(player, bound)
        recordOwned(player)
        // The follower becomes its Upgraded copy first, so the glow plays on the pet the player now sees.
        Pets.refreshUpgrade(player)
        Pets.current(player)?.graphic(BIND_GRAPHIC)
        player.playSound(BIND_SOUND)
        ok.replaces?.let { player.message("Your ${it.trinketName} is no longer bound; it stays yours.") }
        player.message("You bind the ${tier.trinketName} to your ${ok.name}.")
        player.message("<col=$PURPLE>Your ${ok.name} has become an ${upgradedName(ok.name)}: it now carries ${tier.slots} items for you!</col>")
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket ${tier.trinketName} bound to ${ok.pet} for ${player.username}" }
        return true
    }

    // ---- Unbind ---------------------------------------------------------------------------------------------------------------

    /** The pets [tier] could be unbound from: the follower first when it carries this kind. */
    fun unbindTargets(
        player: Player,
        tier: Tier,
    ): List<String> {
        val pets = bindings(player).filterValues { it == tier.item }.keys.sorted()
        val out = Pets.itemOf(player)?.let { Pets.variantOfItem(it) }?.pet
        return if (out != null && out in pets) listOf(out) + (pets - out) else pets
    }

    /** Items the upgraded pet that is out is carrying (only the follower has a pack). */
    fun cargoCount(player: Player): Int = BeastOfBurden.storedCount(player)

    fun unbind(
        player: Player,
        tier: Tier,
        pet: String,
    ): Boolean {
        if (inCombat(player)) {
            player.message("You can't do that during combat.")
            return false
        }
        val bound = bindings(player).toMutableMap()
        if (bound[pet] != tier.item) return false
        val following = Pets.itemOf(player)?.let { Pets.variantOfItem(it) }?.pet == pet
        if (following && cargoCount(player) > 0) {
            player.message("Your ${upgradedName(petName(player, pet))} is still carrying items. Take them out first.")
            return false
        }
        val name = petName(player, pet)
        bound.remove(pet)
        store(player, bound)
        player.message("You unbind the ${tier.trinketName} from your ${upgradedName(name)}. It is a plain $name again; the trinket stays yours.")
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket ${tier.trinketName} unbound from $pet for ${player.username}" }
        if (following) {
            FamiliarInventory.close(player)
            Pets.refreshUpgrade(player)
        }
        return true
    }

    // ---- never lost: Perdu ----------------------------------------------------------------------------------------------------

    fun ownedOnce(player: Player): Set<Int> = player.attr[OWNED_ATTR].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> isTrinket(id) } }.toSet()

    /** Records every trinket kind the player has now (login, Perdu, bind). */
    fun recordOwned(player: Player) {
        val seen = ownedOnce(player).toMutableSet()
        val before = seen.size
        Tier.values().filter { owned(player, it.item) > 0 }.forEach { seen += it.item }
        if (seen.size != before) player.attr[OWNED_ATTR] = seen.sorted().joinToString(",")
    }

    /** The trinket kinds the player had once and has nowhere now - Perdu gives each back for free (design v6: never lost). */
    fun lost(player: Player): List<Tier> = ownedOnce(player).filter { owned(player, it) <= 0 }.mapNotNull { tier(it) }.sortedBy { it.slots }

    fun examine(tier: Tier): String = "The spirit of a ${tier.spirit}, sealed in stone."
}
