/**
 * "Infuse-pouch" opens the pouch and scroll creation interface: "taking these to a fully-charged
 * Summoning obelisk, you will be able to infuse a pouch".
 *
 * Bound from the object definitions in data/cache for the same reason as the Renew-points
 * handler: the seven world obelisks listed here before (Taverley, Desert, Piscatoris, Gu'Tanoth,
 * Well of Voyage, Wishing Well and Brimhaven) are only seven of the twelve objects this revision
 * gives the option to - the five "Summoning obelisk" ids 50205, 50206, 50207, 53883 and 55605
 * carry it as well, and infusing at those did nothing.
 */
val infusePouchObelisks =
    world.definitions
        .getAll(ObjectDef::class.java)
        .values
        .filterIsInstance<ObjectDef>()
        .filter { def -> def.options.any { it?.equals("Infuse-pouch", ignoreCase = true) == true } }
        .map { it.id }
        .sorted()

check(infusePouchObelisks.size == 12) {
    "Expected the 12 sourced Infuse-pouch obelisks, found ${infusePouchObelisks.size}: $infusePouchObelisks"
}

infusePouchObelisks.forEach { obelisk ->
    on_obj_option(obelisk, "Infuse-pouch") {
        openPouchInterface(player)
    }
}
