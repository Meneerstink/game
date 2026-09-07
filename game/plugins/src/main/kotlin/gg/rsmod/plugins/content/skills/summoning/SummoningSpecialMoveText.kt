package gg.rsmod.plugins.content.skills.summoning

/**
 * The special-move name and description the follower panel shows, and the only place they can come
 * from on this revision.
 *
 * Decoded from this cache on 2026-09-06. The panel's special-move line is built entirely
 * client-side by clientscript 661, reached from 606 -> 659 -> 661:
 *
 *  - 606 pushes `OC_PARAM(var448, 394)` (the pouch's own "Summoning level required" param) as the
 *    "Level <n>:" prefix, `VARCSTR(204)` and `VARCSTR(205)` as the two strings, and
 *    `ENUM(o->o, 1283, var448)` (the familiar's scroll) as the family selector.
 *  - 661 switches on that scroll id. Only the seven *tiered* scroll families have cases there
 *    (`disasm 775` is the six minotaur Bull Rush tiers, `disasm 3357`..`3362` the Sundering
 *    Strike/nihil tiers); every ordinary familiar falls to the default branch, which uses the two
 *    string arguments unchanged. In other words the client deliberately expects the **server** to
 *    supply the move name in varcstr 204 and its description in varcstr 205.
 *  - The cost in "(<n> Special Move points)" is `varbit 4288` (varp 1175, bits 23..27).
 *  - 662:74 lists `varcstrTriggers=[205]`, so rewriting varcstr 205 is what makes the panel redraw.
 *
 * With none of those three written, the live panel read "Level 52: null (0 Special Move points)"
 * and "null" - which is exactly what the owner saw.
 *
 * Every name and description below is quoted verbatim from the archived Jagex 2011 Knowledge Base
 * article "Summoning - Scrolls" (local copy `C:\RSPS\2011RS_SUMMONING_SCROLLS.md`, mirrored at
 * 2011.rs). The same table's "Special Move Points" column was cross-checked against
 * [SummoningScrollData]'s existing `specialPoints` values: all 67 agree, so the costs already in
 * the codebase are confirmed period-correct rather than merely plausible.
 *
 * Not covered: Fetch Casket (Meerkats), which has no [SummoningScrollData] entry because the
 * familiar is not implemented. It is omitted rather than invented.
 */
data class SpecialMoveText(val move: String, val description: String)

object SummoningSpecialMoveText {
    private val byScroll: Map<SummoningScrollData, SpecialMoveText> =
        mapOf(
        SummoningScrollData.ABYSSAL_DRAIN_SCROLL to
            SpecialMoveText("Abyssal Drain", "Magic-based attack that gives you a Prayer point if it hits"),
        SummoningScrollData.ABYSSAL_STEALTH_SCROLL to
            SpecialMoveText("Abyssal Stealth", "4 point boost to both Agility and Thieving"),
        SummoningScrollData.ACORN_MISSILE_SCROLL to
            SpecialMoveText("Acorn Missile", "Inflicts up to 110 damage on up to 3 opponents. Chance of acorns being dropped"),
        SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL to
            SpecialMoveText("Adamant Bull Rush", "Magical attack that does up to 160 damage, with a chance of stunning your opponent"),
        SummoningScrollData.AMBUSH_SCROLL to
            SpecialMoveText("Ambush", "Calls the kyatt into combat for an instant hit with potential high damage"),
        SummoningScrollData.ARCTIC_BLAST_SCROLL to
            SpecialMoveText("Arctic Blast", "Magical attack that does up to 150 damage, with a chance of stunning your opponent"),
        SummoningScrollData.BLOOD_DRAIN_SCROLL to
            SpecialMoveText("Blood Drain", "Heals stat damage, poison and disease at the cost of life points"),
        SummoningScrollData.BOIL_SCROLL to
            SpecialMoveText("Boil", "Damages a player, doing more damage based on their armour"),
        SummoningScrollData.BRONZE_BULL_RUSH_SCROLL to
            SpecialMoveText("Bronze Bull Rush", "Magical attack that does up to 40 damage, with a chance of stunning your opponent"),
        SummoningScrollData.CALL_TO_ARMS_SCROLL to
            SpecialMoveText("Call to Arms", "Teleports you to Pest Control landers"),
        SummoningScrollData.CHEESE_FEAST_SCROLL to
            SpecialMoveText("Cheese Feast", "Puts 4 cheese into the rat's inventory"),
        SummoningScrollData.CRUSHING_CLAW_SCROLL to
            SpecialMoveText("Crushing Claw", "Inflicts up to 140 damage, as well as removing up to 5 Defence from your opponent"),
        SummoningScrollData.DEADLY_CLAW_SCROLL to
            SpecialMoveText("Deadly Claw", "Three Magic attacks"),
        SummoningScrollData.DISSOLVE_SCROLL to
            SpecialMoveText("Dissolve", "A Magic attack that inflicts up to 120 damage and drains the target's Attack"),
        SummoningScrollData.DOOMSPHERE_SCROLL to
            SpecialMoveText("Doomsphere Device", "Inflicts up to 160 damage on the target"),
        SummoningScrollData.DREADFOWL_STRIKE_SCROLL to
            SpecialMoveText("Dreadfowl Strike", "Magic attack that inflicts up to 30 damage"),
        SummoningScrollData.DUST_CLOUD_SCROLL to
            SpecialMoveText("Dust Cloud", "Inflicts up to 80 damage on up to 6 adjacent opponents"),
        SummoningScrollData.EBON_THUNDER_SCROLL to
            SpecialMoveText("Ebon Thunder", "Magic attack that also drains your target's Special Attack energy"),
        SummoningScrollData.EGG_SPAWN_SCROLL to
            SpecialMoveText("Egg Spawn", "Creates a random number of red spider eggs"),
        SummoningScrollData.ELECTRIC_LASH_SCROLL to
            SpecialMoveText("Electric Lash", "Magic attack that inflicts up to 50 damage and stuns your opponent"),
        SummoningScrollData.ESSENCE_SHIPMENT_SCROLL to
            SpecialMoveText("Essence Shipment", "Transports all pure essence from both your inventory and the titan's to your bank"),
        SummoningScrollData.EVIL_FLAMES_SCROLL to
            SpecialMoveText("Evil Flames", "Magic attack that drains the target's Ranged skill"),
        SummoningScrollData.EXPLODE_SCROLL to
            SpecialMoveText("Explode", "Detonates the chinchompa, damaging nearby enemies"),
        SummoningScrollData.FAMINE_SCROLL to
            SpecialMoveText("Famine", "Destroys target player's food"),
        SummoningScrollData.FIREBALL_ASSAULT_SCROLL to
            SpecialMoveText("Fireball Assault", "Hits two nearby targets for up to 70 damage each"),
        SummoningScrollData.FISH_RAIN_SCROLL to
            SpecialMoveText("Fish Rain", "Produces fish up to bass"),
        SummoningScrollData.FRUITFALL_SCROLL to
            SpecialMoveText("Fruitfall", "Produces random fruit nearby"),
        SummoningScrollData.GENERATE_COMPOST_SCROLL to
            SpecialMoveText("Generate Compost", "Fills a nearby compost bin with compost, with a small chance of producing supercompost"),
        SummoningScrollData.GOAD_SCROLL to
            SpecialMoveText("Goad", "Sends your spirit graahk to attack an enemy"),
        SummoningScrollData.HEALING_AURA_SCROLL to
            SpecialMoveText("Healing Aura", "Heals up to 15% of your life points"),
        SummoningScrollData.HERBCALL_SCROLL to
            SpecialMoveText("Herbcall", "Chance of creating herbs"),
        SummoningScrollData.HOWL_SCROLL to
            SpecialMoveText("Howl", "Causes NPC foes to flee"),
        SummoningScrollData.IMMENSE_HEAT_SCROLL to
            SpecialMoveText("Immense Heat", "Smelts a gold bar into an item of jewellery without a furnace"),
        SummoningScrollData.INFERNO_SCROLL to
            SpecialMoveText("Inferno", "Magic attack that can disarm your opponent's weapon or shield"),
        SummoningScrollData.INSANE_FEROCITY_SCROLL to
            SpecialMoveText("Insane Ferocity", "Reduces its Defence to increase its Attack and Strength"),
        SummoningScrollData.IRON_BULL_RUSH_SCROLL to
            SpecialMoveText("Iron Bull Rush", "Magical attack that does up to 60 damage, with a chance of stunning your opponent"),
        SummoningScrollData.IRON_WITHIN_SCROLL to
            SpecialMoveText("Iron Within", "The iron titan's next attack will be three powerful melee attacks"),
        SummoningScrollData.MAGIC_FOCUS_SCROLL to
            SpecialMoveText("Magic Focus", "Gives you a +7 Magic boost"),
        SummoningScrollData.MANTIS_STRIKE_SCROLL to
            SpecialMoveText("Mantis Strike", "Binds, causes Magic-based damage and drains target player's Prayer"),
        SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL to
            SpecialMoveText("Mithril Bull Rush", "Magical attack that does up to 120 damage, with a chance of stunning your opponent"),
        SummoningScrollData.MULTICHOP_SCROLL to
            SpecialMoveText("Multichop", "Cuts up to 3 logs from a nearby tree"),
        SummoningScrollData.OPHIDIAN_INCUBATION_SCROLL to
            SpecialMoveText("Ophidian Incubation", "Turns an egg into a cockatrice egg"),
        SummoningScrollData.PESTER_SCROLL to
            SpecialMoveText("Pester", "Sends the mosquito to attack an enemy"),
        SummoningScrollData.PETRIFYING_GAZE_SCROLL to
            SpecialMoveText("Petrifying gaze", "Deals up to 100 damage against an opponent, as well as reducing a combat skill by up to 3 (varies by type of cockatrice)"),
        SummoningScrollData.POISONOUS_BLAST_SCROLL to
            SpecialMoveText("Poisonous Blast", "Attack with 50% chance of poisoning your opponent and inflicting 20 damage"),
        SummoningScrollData.REGROWTH_SCROLL to
            SpecialMoveText("Regrowth", "Use on a Farming tree stump to instantly regrow the tree"),
        SummoningScrollData.RENDING_SCROLL to
            SpecialMoveText("Rending", "Magic-based attack that also drains opponent's Strength"),
        SummoningScrollData.RISH_FROM_THE_ASHES_SCROLL to
            SpecialMoveText("Rise from the Ashes", "Target ashes on the ground to cause phoenix to be reborn, healing all of its injuries and damaging adjacent targets. Damage is greater if the phoenix's health was lower before casting."),
        SummoningScrollData.RUNE_BULL_RUSH_SCROLL to
            SpecialMoveText("Rune Bull Rush", "Magical attack that does up to 190 damage, with a chance of stunning your opponent"),
        SummoningScrollData.SANDSTORM_SCROLL to
            SpecialMoveText("Sandstorm", "Strikes up to 5 nearby opponents for up to 20 damage each"),
        SummoningScrollData.SLIME_SPRAY_SCROLL to
            SpecialMoveText("Slime Spray", "Attack that inflicts up to 80 damage"),
        SummoningScrollData.SPIKE_SHOT_SCROLL to
            SpecialMoveText("Spike Shot", "Ranged attack that inflicts up to 180 damage and stuns your opponent"),
        SummoningScrollData.STEEL_BULL_RUSH_SCROLL to
            SpecialMoveText("Steel Bull Rush", "Magical attack that does up to 90 damage, with a chance of stunning your opponent"),
        SummoningScrollData.STEEL_OF_LEGENDS_SCROLL to
            SpecialMoveText("Steel of Legends", "The steel titan's next attack will be four powerful ranged attacks"),
        SummoningScrollData.STONY_SHELL_SCROLL to
            SpecialMoveText("Stony Shell", "Gives you a +4 Defence boost"),
        SummoningScrollData.SWALLOW_WHOLE_SCROLL to
            SpecialMoveText("Swallow Whole", "Allows you to eat an uncooked fish, assuming you have the level to cook it"),
        SummoningScrollData.SWAMP_PLAGUE_SCROLL to
            SpecialMoveText("Swamp Plague", "Area effect Magic attack that can poison your enemies"),
        SummoningScrollData.TESTUDO_SCROLL to
            SpecialMoveText("Testudo", "Gives you a +8 Defence boost"),
        SummoningScrollData.THIEVING_FINGERS_SCROLL to
            SpecialMoveText("Thieving Fingers", "2 point Thieving boost"),
        SummoningScrollData.TIRELESS_RUN_SCROLL to
            SpecialMoveText("Tireless Run", "2 point Agility boost, and restores your run energy based on your normal Agility"),
        SummoningScrollData.TITANS_CONSTITUTION_SCROLL to
            SpecialMoveText("Titan's Constitution", "Boosts your Defence and life points significantly"),
        SummoningScrollData.TOAD_BARK_SCROLL to
            SpecialMoveText("Toad Bark", "Inflicts up to 180 damage on your opponent"),
        SummoningScrollData.UNBURDEN_SCROLL to
            SpecialMoveText("Unburden", "Restores run energy based on your Agility"),
        SummoningScrollData.VAMPIRE_TOUCH_SCROLL to
            SpecialMoveText("Vampire Touch", "Inflicts up to 120 damage on an opponent, with a chance of restoring 20 of your life points"),
        SummoningScrollData.VENOM_SHOT_SCROLL to
            SpecialMoveText("Venom Shot", "Makes your next Ranged attack mildly poisonous, provided the ammunition you are using can be poisoned"),
        SummoningScrollData.VOLCANIC_STRENGTH_SCROLL to
            SpecialMoveText("Volcanic Strength", "Gives you a +9 Strength boost"),
        SummoningScrollData.WINTER_STORAGE_SCROLL to
            SpecialMoveText("Winter Storage", "Use special move on an item in your inventory to send it to your bank"),
        )

    operator fun get(scroll: SummoningScrollData): SpecialMoveText? = byScroll[scroll]

    /** Exposed for the completeness test; the map is otherwise read-only. */
    val entries: Map<SummoningScrollData, SpecialMoveText> get() = byScroll
}
