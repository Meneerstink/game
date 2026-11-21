package gg.rsmod.plugins.content.mechanics.deadman.sigils

/**
 * PROJECT48 SIGILS (Deadman: Armageddon): all 65 sigils (OSRS Wiki Deadman: Armageddon/Sigils, SIGILS.tsv). Local item ids come from the
 * import batch p48-sigils (OsrsItemImportTool, applied 21-11 directly after p48-items (28) and p48-items-c (8): first id 25501, attuned then
 * unattuned per sigil, table order). SigilTests checks every id against the item names in the cache.
 */
enum class Sigil(
    val number: Int,
    val label: String,
    val tier: Int,
    val kind: Kind,
    /** The attuned item (untradeable, always kept on death) and the unattuned item (tradeable on the Grand Exchange only). */
    val attunedId: Int,
    val unattunedId: Int,
    /** Cooldown in seconds of an activatable sigil, or -1 when the sigil is passive. */
    val cooldownSeconds: Int,
    /** How long an activation lasts, in seconds (0 = instant effect). */
    val windowSeconds: Int,
) {
    CONSISTENCY(1, "Sigil of Consistency", 1, Kind.COMBAT, 25501, 25502, -1, 0),
    DEFT_STRIKES(2, "Sigil of Deft Strikes", 1, Kind.COMBAT, 25503, 25504, -1, 0),
    ENHANCED_HARVEST(3, "Sigil of Enhanced Harvest", 1, Kind.SKILLING, 25505, 25506, -1, 0),
    EXAGGERATION(4, "Sigil of Exaggeration", 1, Kind.UTILITY, 25507, 25508, -1, 0),
    MOBILITY(5, "Sigil of Mobility", 1, Kind.UTILITY, 25509, 25510, -1, 0),
    RESILIENCE(6, "Sigil of Resilience", 1, Kind.COMBAT, 25511, 25512, -1, 0),
    RESISTANCE(7, "Sigil of Resistance", 1, Kind.COMBAT, 25513, 25514, -1, 0),
    SUSTENANCE(8, "Sigil of Sustenance", 1, Kind.SKILLING, 25515, 25516, -1, 0),
    THE_WELL_FED(9, "Sigil of The Well Fed", 1, Kind.UTILITY, 25517, 25518, -1, 0),
    THE_ABYSS(10, "Sigil of the Abyss", 1, Kind.SKILLING, 25519, 25520, -1, 0),
    THE_ALCHEMANIAC(11, "Sigil of the Alchemaniac", 1, Kind.UTILITY, 25521, 25522, -1, 0),
    THE_ETERNAL_JEWELLER(12, "Sigil of the Eternal Jeweller", 1, Kind.UTILITY, 25523, 25524, -1, 0),
    THE_FOOD_MASTER(13, "Sigil of the Food Master", 1, Kind.UTILITY, 25525, 25526, -1, 0),
    THE_FORAGER(14, "Sigil of the Forager", 1, Kind.SKILLING, 25527, 25528, -1, 0),
    THE_METICULOUS_MAGE(15, "Sigil of the Meticulous Mage", 1, Kind.COMBAT, 25529, 25530, -1, 0),
    THE_POTION_MASTER(16, "Sigil of the Potion Master", 1, Kind.UTILITY, 25531, 25532, -1, 0),
    THE_RIGOROUS_RANGER(17, "Sigil of the Rigorous Ranger", 1, Kind.COMBAT, 25533, 25534, -1, 0),
    THE_TREASURE_HUNTER(18, "Sigil of the Treasure Hunter", 1, Kind.UTILITY, 25535, 25536, -1, 0),
    ARCANE_SWIFTNESS(19, "Sigil of Arcane Swiftness", 2, Kind.COMBAT, 25537, 25538, -1, 0),
    BARROWS(20, "Sigil of Barrows", 2, Kind.COMBAT, 25539, 25540, -1, 0),
    DEVOTION(21, "Sigil of Devotion", 2, Kind.SKILLING, 25541, 25542, -1, 0),
    FAITH(22, "Sigil of Faith", 2, Kind.UTILITY, 25543, 25544, -1, 0),
    FORTIFICATION(23, "Sigil of Fortification", 2, Kind.COMBAT, 25545, 25546, -1, 0),
    PRECISION(24, "Sigil of Precision", 2, Kind.COMBAT, 25547, 25548, -1, 0),
    PRESERVATION(25, "Sigil of Preservation", 2, Kind.UTILITY, 25549, 25550, -1, 0),
    PROSPERITY(26, "Sigil of Prosperity", 2, Kind.UTILITY, 25551, 25552, -1, 0),
    RESTORATION(27, "Sigil of Restoration", 2, Kind.COMBAT, 25553, 25554, -1, 0),
    REVOKED_LIMITATION(28, "Sigil of Revoked Limitation", 2, Kind.UTILITY, 25555, 25556, -1, 0),
    THE_ADROIT(29, "Sigil of the Adroit", 2, Kind.COMBAT, 25557, 25558, -1, 0),
    THE_GUNSLINGER(30, "Sigil of the Gunslinger", 2, Kind.COMBAT, 25559, 25560, -1, 0),
    THE_HUNTER(31, "Sigil of the Hunter", 2, Kind.SKILLING, 25561, 25562, -1, 0),
    THE_INFERNAL_CHEF(32, "Sigil of the Infernal Chef", 2, Kind.SKILLING, 25563, 25564, -1, 0),
    THE_INFERNAL_SMITH(33, "Sigil of the Infernal Smith", 2, Kind.SKILLING, 25565, 25566, -1, 0),
    THE_LIGHTBEARER(34, "Sigil of the Lightbearer", 2, Kind.COMBAT, 25567, 25568, -1, 0),
    THE_SWASHBUCKLER(35, "Sigil of the Swashbuckler", 2, Kind.COMBAT, 25569, 25570, -1, 0),
    AGGRESSION(36, "Sigil of Aggression", 3, Kind.COMBAT, 25571, 25572, -1, 0),
    METICULOUSNESS(37, "Sigil of Meticulousness", 3, Kind.COMBAT, 25573, 25574, -1, 0),
    PIOUS_PROTECTION(38, "Sigil of Pious Protection", 3, Kind.COMBAT, 25575, 25576, -1, 0),
    TITANIUM(39, "Sigil of Titanium", 3, Kind.COMBAT, 25577, 25578, -1, 0),
    THE_RAMPART(40, "Sigil of the Rampart", 3, Kind.COMBAT, 25579, 25580, -1, 0),
    DECEPTION(41, "Sigil of Deception", 1, Kind.SKILLING, 25581, 25582, -1, 0),
    LITHENESS(42, "Sigil of Litheness", 1, Kind.SKILLING, 25583, 25584, -1, 0),
    STORAGE(43, "Sigil of Storage", 1, Kind.SKILLING, 25585, 25586, -1, 0),
    BINDING(44, "Sigil of Binding", 2, Kind.COMBAT, 25587, 25588, 15, 12),
    LAST_RECALL(45, "Sigil of Last Recall", 2, Kind.UTILITY, 25589, 25590, 300, 0),
    ONSLAUGHT(46, "Sigil of Onslaught", 2, Kind.COMBAT, 25591, 25592, -1, 0),
    SPECIALISED_STRIKES(47, "Sigil of Specialised Strikes", 2, Kind.COMBAT, 25593, 25594, 20, 12),
    VERSATILITY(48, "Sigil of Versatility", 2, Kind.UTILITY, 25595, 25596, 0, 0),
    THE_AUGMENTED_THRALL(49, "Sigil of the Augmented Thrall", 2, Kind.COMBAT, 25597, 25598, -1, 0),
    THE_BLOODHOUND(50, "Sigil of the Bloodhound", 2, Kind.UTILITY, 25599, 25600, -1, 0),
    THE_FORMIDABLE_FIGHTER(51, "Sigil of the Formidable Fighter", 2, Kind.COMBAT, 25601, 25602, -1, 0),
    THE_MENACING_MAGE(52, "Sigil of the Menacing Mage", 2, Kind.COMBAT, 25603, 25604, -1, 0),
    THE_PORCUPINE(53, "Sigil of the Porcupine", 2, Kind.COMBAT, 25605, 25606, 60, 15),
    THE_RUTHLESS_RANGER(54, "Sigil of the Ruthless Ranger", 2, Kind.COMBAT, 25607, 25608, -1, 0),
    THE_SERPENT(55, "Sigil of the Serpent", 2, Kind.UTILITY, 25609, 25610, 180, 600),
    FINALITY(56, "Sigil of Finality", 3, Kind.COMBAT, 25611, 25612, 30, 12),
    RAMPAGE(57, "Sigil of Rampage", 3, Kind.COMBAT, 25613, 25614, -1, 0),
    REMOTE_STORAGE(58, "Sigil of Remote Storage", 3, Kind.SKILLING, 25615, 25616, -1, 0),
    WOODCRAFT(59, "Sigil of Woodcraft", 3, Kind.SKILLING, 25617, 25618, -1, 0),
    AGILE_FORTUNE(60, "Sigil of Agile Fortune", 1, Kind.SKILLING, 25619, 25620, -1, 0),
    HOARDING(61, "Sigil of Hoarding", 1, Kind.SKILLING, 25621, 25622, -1, 0),
    NATURE(62, "Sigil of Nature", 2, Kind.SKILLING, 25623, 25624, -1, 0),
    SLAUGHTER(63, "Sigil of Slaughter", 2, Kind.UTILITY, 25625, 25626, -1, 0),
    THE_FORTUNE_FARMER(64, "Sigil of the Fortune Farmer", 2, Kind.UTILITY, 25627, 25628, -1, 0),
    THE_GUARDIAN_ANGEL(65, "Sigil of the Guardian Angel", 3, Kind.UTILITY, 25629, 25630, -1, 0),
    ;

    enum class Kind { COMBAT, SKILLING, UTILITY }

    val activatable: Boolean get() = cooldownSeconds >= 0

    companion object {
        private val byAttuned: Map<Int, Sigil> = values().associateBy { it.attunedId }
        private val byUnattuned: Map<Int, Sigil> = values().associateBy { it.unattunedId }

        fun ofAttuned(itemId: Int): Sigil? = byAttuned[itemId]

        fun ofUnattuned(itemId: Int): Sigil? = byUnattuned[itemId]

        fun ofItem(itemId: Int): Sigil? = byAttuned[itemId] ?: byUnattuned[itemId]
    }
}
