package gg.rsmod.plugins.content.mechanics.deadman.sigils

/**
 * PROJECT48 SIGILS: the effect text of every sigil, word for word from the OSRS Wiki (Deadman: Armageddon/Sigils; stored in
 * team/dossiers/PROJECT48-notities/SIGILS.tsv, column 8). Shown by Inspect and - once built - in the approved "Attuned Sigils"
 * window (owner 21-11, team/dossiers/GOEDGEKEURDE-ONTWERPEN.txt). Keyed by the sigil number S01-S65.
 */
object SigilTexts {
    private val TEXT: Map<Int, String> =
        mapOf(
            // Sigil of Consistency
            1 to
                "Every attack does +1 damage to the target.",
            // Sigil of Deft Strikes
            2 to
                "You gain 20% more accuracy in all styles against all non-player enemies.",
            // Sigil of Enhanced Harvest
            3 to
                "You always gain 3x resources from Mining, Fishing, Woodcutting, and Farming. This sigil does not stack " +
                "with Sigil of the Forager.",
            // Sigil of Exaggeration
            4 to
                "All non-combat skills are permanently boosted by 5.",
            // Sigil of Mobility
            5 to
                "Teleports do not consume runes and do not grant any XP.",
            // Sigil of Resilience
            6 to
                "Your hitpoints restore 15x faster than normal.",
            // Sigil of Resistance
            7 to
                "All attacks from monsters are reduced by 25%. The damage reduction effect does not stack with the Sigil " +
                "of Titanium.",
            // Sigil of Sustenance
            8 to
                "You have a 50% chance not to consume secondary ingredients when creating potions.",
            // Sigil of The Well Fed
            9 to
                "You have a 30% chance to gain twice the number of hitpoints from any food or potions consumed.",
            // Sigil of the Abyss
            10 to
                "When Runecrafting, you gain 5x as many runes as usual.",
            // Sigil of the Alchemaniac
            11 to
                "High Level Alchemy will provide 30% extra coins.",
            // Sigil of the Eternal Jeweller
            12 to
                "Teleporting via the following methods will never use charges: master scroll book, ring of dueling, ring " +
                "of returning, Slayer ring, ring of wealth, amulet of glory, games necklace, skills necklace, Digsite " +
                "pendant, combat bracelet, burning amulet, and necklace of passage.",
            // Sigil of the Food Master
            13 to
                "You have a 40% chance not to consume food when used. This effect has a 6 second cooldown.",
            // Sigil of the Forager
            14 to
                "You always gain 2x resources from Mining, Fishing, Woodcutting, and Farming. This sigil does not stack " +
                "with the Sigil of Enhanced Harvest.",
            // Sigil of the Meticulous Mage
            15 to
                "Your magic accuracy is increased by +40 and you have a 50% chance of saving runes or staff charges when " +
                "using Magic.",
            // Sigil of the Potion Master
            16 to
                "You have a 20% chance not to consume potions when using them. Once the effect activates, it cannot be " +
                "triggered again within six seconds.",
            // Sigil of the Rigorous Ranger
            17 to
                "Your ranged accuracy is increased by +20, and you have a 50% chance of saving ammunition when using " +
                "Ranged weapons, except when throwing Chinchompas.",
            // Sigil of the Treasure Hunter
            18 to
                "Clue Scrolls drop 3x more often.",
            // Sigil of Arcane Swiftness
            19 to
                "The attack speed of all magic weapons with a base attack speed above 4 will be reduced by 1. Cannot be " +
                "used in conjunction with the sigil of the swashbuckler or the sigil of the gunslinger.",
            // Sigil of Barrows
            20 to
                "Prevents Barrows armour sets from degrading and adds +40% Accuracy to attacks from Barrows weaponry " +
                "against players and NPCs.",
            // Sigil of Devotion
            21 to
                "Dropped Bones are automatically buried. Ashes are automatically scattered. Prayer XP gained from these " +
                "activities is increased by 200%.",
            // Sigil of Faith
            22 to
                "You have a 40% chance to gain twice the number of Prayer points when consuming Prayer-restoring potions.",
            // Sigil of Fortification
            23 to
                "Your defence is increased by +50 for stab, slash, crush, and ranged.",
            // Sigil of Precision
            24 to
                "You gain 50% more accuracy in all styles against all monsters.",
            // Sigil of Preservation
            25 to
                "Stats boosted above your base level will not drain over time.",
            // Sigil of Prosperity
            26 to
                "You have a 20% increased chance to receive loot from the Global Deadman Mode loot table.",
            // Sigil of Restoration
            27 to
                "Upon a successful hit, restore 10% of the damage dealt (rounded up) to your Hitpoints. This works " +
                "against both players and NPCs but excludes multi-target attacks and Special Attacks.",
            // Sigil of Revoked Limitation
            28 to
                "Allows Revenant weapons' +50% Accuracy/Damage buff to apply to NPCs anywhere.",
            // Sigil of the Adroit
            29 to
                "Increases your Accuracy by 1% against players and NPCs for each Hitpoint you're missing. This means if " +
                "your max HP is 99 and you're sitting at 50 HP, you'll gain +49% Accuracy against enemies.",
            // Sigil of the Gunslinger
            30 to
                "The attack speed of all ranged weapons with a base attack speed above 4 will be reduced by 1. Cannot be " +
                "used in conjunction with the sigil of the swashbuckler or the sigil of arcane swiftness. Excludes the " +
                "crystal bow, Bow of Faerdhinen, and Morrigan's thrownaxe.",
            // Sigil of the Hunter
            31 to
                "You gain three Chinchompas per successful Chinchompa capture.",
            // Sigil of the Infernal Chef
            32 to
                "Provides a 20% chance to instantly cook fish that are caught while Fishing, granting Cooking experience " +
                "regardless of level requirements.",
            // Sigil of the Infernal Smith
            33 to
                "Provides a 20% chance to instantly smelt bars from the ore you mine, granting Smithing experience " +
                "regardless of level requirements.",
            // Sigil of the Lightbearer
            34 to
                "Special attack regeneration will become 15 seconds per 10%. If a Lightbearer ring is also equipped, " +
                "special attack regeneration will become 10 seconds per 10%.",
            // Sigil of the Swashbuckler
            35 to
                "The attack speed of all melee weapons with a base attack speed above 4 will be reduced by 1. Cannot be " +
                "used in conjunction with the sigil of the gunslinger or the sigil of arcane swiftness.",
            // Sigil of Aggression
            36 to
                "All attacks deal 10% more damage, but you take 5% more damage from all sources. This sigil does not " +
                "stack with the Sigil of Rampage.",
            // Sigil of Meticulousness
            37 to
                "Grants +75% Accuracy in all styles against NPCs. This is a higher-tier variant of the Sigil of Deft " +
                "Strikes.",
            // Sigil of Pious Protection
            38 to
                "Decrease the amount of damage you take from attacks of a given combat style by 35% when using the " +
                "correct protection Prayer for that style. Increase the damage you take by protecting against the wrong " +
                "combat style by 5%.",
            // Sigil of Titanium
            39 to
                "All attacks from monsters are reduced by 60%. The damage reduction effect does not stack with the Sigil " +
                "of Resistance.",
            // Sigil of the Rampart
            40 to
                "Increases your Stab, Slash, Crush, and Ranged defences by +100. Does not stack with the Sigil of " +
                "Fortification.",
            // Sigil of Deception
            41 to
                "Automatically re-pickpocket NPCs until you're otherwise unable to do so. You'll still have a chance to " +
                "fail pickpockets.",
            // Sigil of Litheness
            42 to
                "Grants 2x your Agility level as Agility XP while running, approximately every 10 ticks. Does not grant " +
                "XP while in a safe zone.",
            // Sigil of Storage
            43 to
                "You have a 90% chance to send your newly gathered resources to the Bank when gathering with Mining, " +
                "Fishing, Woodcutting or Farming.",
            // Sigil of Binding
            44 to
                "Activate the Sigil to get a 75% magic accuracy buff on your next binding spell within the next 12 " +
                "seconds. The Sigil has a 15 second cooldown.",
            // Sigil of Last Recall
            45 to
                "Activate the Sigil to teleport back to the location from which your most recent teleport occurred. Once " +
                "activated, the Sigil has a 5-minute cooldown.",
            // Sigil of Onslaught
            46 to
                "Increases the number of targets that Chinchompas and Ancient Magicks can damage from 9 to 11, as long as " +
                "they're within a 3x3 radius of the initial target.",
            // Sigil of Specialised Strikes
            47 to
                "Activate the Sigil to reduce the cost of your next Special Attack that costs more than 10 energy by 10. " +
                "This effect lasts for 12 seconds and has a 20 second cooldown.",
            // Sigil of Versatility
            48 to
                "Activate the Sigil to instantly swap your Spellbook to any unlocked Spellbook while outside of combat.",
            // Sigil of the Augmented Thrall
            49 to
                "The max hits of your thralls are multiplied by 5.",
            // Sigil of the Bloodhound
            50 to
                "You have a 75% chance for a clue scroll to return the reward casket after you complete a clue step for " +
                "any tier.",
            // Sigil of the Formidable Fighter
            51 to
                "Your melee accuracy is increased by +30 in stab, slash, and crush. Upon a successful hit, you have a 30% " +
                "chance for your next hit to deal +10 damage. The chance to deal +10 damage on your next hit does not " +
                "apply to special attacks.",
            // Sigil of the Menacing Mage
            52 to
                "Upon dealing Magic damage, you have a 10% chance to curse the target. The curse deals 18 damage (12 " +
                "damage in the level 3-50 combat bracket) over the next 6 seconds and heals you for the same amount. A " +
                "target can only be under the effect of one curse at a time.",
            // Sigil of the Porcupine
            53 to
                "On level 71-90, 91-110, and 111+ Worlds: Activate the Sigil to recoil 4 damage to your attacker for " +
                "every hit you take. On level 3-50 and 51-70 worlds: Activate the Sigil to recoil 2 damage to your " +
                "attacker for every hit you take. The effect lasts for 15 seconds and has a 1-minute cooldown.",
            // Sigil of the Ruthless Ranger
            54 to
                "Upon dealing Ranged damage, you have a 10% chance to cripple the target's movement. The effect deals 15 " +
                "damage (10 damage in the level 3-50 combat bracket) over the next 6 seconds and drains a total of 15 run " +
                "energy. The damage is doubled against targets with no run energy. A target can only suffer from one " +
                "crippling effect at a time.",
            // Sigil of the Serpent
            55 to
                "Activate the Sigil to cure venom and poison, as well as gain immunity from both effects for 10 minutes. " +
                "Once activated, the Sigil has a 3-minute cooldown.",
            // Sigil of Finality
            56 to
                "Activate the Sigil to guarantee 100% accuracy on your next special attack within the next 12 seconds. " +
                "Once activated, the Sigil has a 30 second cooldown.",
            // Sigil of Rampage
            57 to
                "Alternating combat styles will increase your rampage count, up to a maximum of 3. Consecutive attacks " +
                "with the same style will reset the counter. Each rampage count increases your damage and accuracy by 3%. " +
                "Your rampage count will reset after not attacking for 6 seconds. The damage bonus effect does not stack " +
                "with the Sigil of Aggression.",
            // Sigil of Remote Storage
            58 to
                "Resources gathered from Mining, Fishing, Woodcutting, and Farming will be automatically sent to your " +
                "Bank.",
            // Sigil of Woodcraft
            59 to
                "Instead of receiving logs when chopping specific trees, you will receive the appropriate planks. Does " +
                "not work in guarded areas or with the infernal axe. Requires 50 gp per regular plank, 125 gp per oak " +
                "plank, 250 gp per teak plank, and 700 gp per mahogany plank.",
            // Sigil of Agile Fortune
            60 to
                "When a Mark of Grace appears while training on a Rooftop Agility Course, 10,000 coins will appear " +
                "alongside it.",
            // Sigil of Hoarding
            61 to
                "When subduing the Wintertodt, you will receive 3 crates. When subduing Tempoross or completing Guardians " +
                "of the Rift, you will receive 3x the amount of reward points.",
            // Sigil of Nature
            62 to
                "Choose your own Farming Contracts. Farming cycles occur every minute instead of every five minutes, " +
                "allowing crops to grow 5x quicker.",
            // Sigil of Slaughter
            63 to
                "Pay 60 Slayer Points to choose your own Slayer Task. You have a 50% chance for a Slayer Task kill to not " +
                "count towards the task while still gaining you Slayer XP. This Sigil does not stack with any bracelet " +
                "effects.",
            // Sigil of the Fortune Farmer
            64 to
                "All clue scrolls will drop as stackable scroll boxes that can be opened when this sigil is attuned.",
            // Sigil of the Guardian Angel
            65 to
                "Upon dying to another player, this Sigil is consumed, and you will lose the items you are carrying as " +
                "normal. However, you will not drop a Bank Key.",
        )

    /** The wiki effect text of [sigil], or an empty string for a sigil without one. */
    fun of(sigil: Sigil): String = TEXT[sigil.number] ?: ""

    /** [of] cut into chat lines of at most [width] characters at word boundaries (a game message does not wrap). */
    fun lines(
        sigil: Sigil,
        width: Int = 76,
    ): List<String> {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in of(sigil).split(" ")) {
            if (line.isNotEmpty() && line.length + 1 + word.length > width) {
                out += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(" ")
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    val size: Int get() = TEXT.size
}
