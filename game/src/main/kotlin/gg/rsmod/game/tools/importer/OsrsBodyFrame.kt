package gg.rsmod.game.tools.importer

import kotlin.math.roundToInt

/**
 * Moves an OSRS worn mesh from the 2007 player body onto the 2010 rev-667 player body.
 *
 * Owner 2026-09-29: "alle osrs gear of wapens kunnen noclippen in muren vloeren of plafond ... dit speelt bij alle osrs items en
 * gear, vind eerst de rootcause". The root cause, measured on the caches (tools\cache667\bodyframe.py, labelmap.py, bases2.py):
 *
 *  * OSRS keeps Jagex's 2007 meshes; for rev 667 Jagex remodelled every player kit and item on a new body (the same model ids
 *    exist in both caches: Rune boots 4954, Rune platelegs 268, Rune full helm 218, the whip 5409, ...). Compared per vertex
 *    label (the bone group an animation moves) over 1588 shared worn models, the 2010 body stands 24 units higher at its root
 *    (label 255 exactly -24 in all 53 models), legs and feet 26-36 higher, the head 22-35 higher and wider/deeper - the 2007
 *    feet reach +24, i.e. through the floor.
 *  * The 667 animations also give the weapon grip (label 50) and the shield (label 161) their own translate/rotate groups on top
 *    of the hand (base 0: 50 = hand 196 + groups 47,48,72,104,136-138; 161 = hand 195 + 110-112), and 667 weapons are modelled
 *    28 units lower at the grip to match (10932 weapon vertices). An OSRS weapon on label 50 gets those 667 groups too, so it
 *    sits off its pivot and swings through walls and ceilings; OSRS shields hang on the wrist label 28 and miss the shield
 *    groups entirely.
 *  * The OSRS client paints every model over the scene, so none of this shows there; the 667 client depth-tests against the
 *    world, which cuts off whatever is under the floor, inside a wall or above the ceiling.
 *
 * The fix is the measured per-label translation (667 - OSRS x4, medians; sample sizes in the table) applied to every vertex,
 * plus the one label the 667 skeleton renamed (OSRS shield 28 -> 161). The mesh is written as version 13: fine units, so the
 * translations are exact and the client no longer multiplies it by 4 - which also marks a mesh as already converted.
 *
 * The OSRS per-item worn offset (item opcode 23/25's byte, `ModernItemDef.maleOffset`) is NOT a mesh property: it is item
 * opcode 125/126 (`ObjType.manwearyoff` / `womanwearyoff`, signed byte x4 = the OSRS unit, so the byte is the OSRS offset
 * itself), written by [OsrsItemImportTool.encodeItem]; it used to be written as offset / 4.
 */
object OsrsBodyFrame {
    const val FRAME_VERSION = 13

    /** Worn slots whose model is drawn on the player (equipment slot numbers, identical in OSRS and rev 667). */
    val RENDERED_SLOTS = setOf(0, 1, 2, 3, 4, 5, 7, 9, 10)

    data class Key(val slot: Int, val label: Int)

    /** Target label and translation in fine (667) units. */
    data class Shift(val label: Int, val dx: Int, val dy: Int, val dz: Int)

    /** The root of the body: label 255 moved exactly (0, -24, 0) in all 53 shared models. */
    private val ROOT = Shift(-1, 0, -24, 0)

    /** Measured per equipment slot and OSRS label (labelmap.py: nearest-vertex vote winner = target label, median centre offset). */
    private val BY_SLOT: Map<Key, Shift> =
        mapOf(
            Key(0, 1) to Shift(1, 0, -26, 12), // n=14066
            Key(0, 2) to Shift(2, 0, -16, 14), // n=218
            Key(0, 8) to Shift(8, 0, -35, 33), // n=147
            Key(1, 6) to Shift(6, 0, -56, -6), // n=516
            Key(1, 7) to Shift(7, 0, -24, -6), // n=344
            Key(1, 8) to Shift(8, 0, -24, 4), // n=8585
            Key(1, 9) to Shift(9, 0, -24, 22), // n=1478
            Key(1, 10) to Shift(10, 0, -32, 16), // n=541
            Key(1, 11) to Shift(11, 0, -26, 12), // n=2981
            Key(1, 12) to Shift(12, 0, 6, 50), // n=783
            Key(1, 13) to Shift(13, 0, 2, 44), // n=1027
            Key(1, 14) to Shift(14, 0, -36, 34), // n=1452
            Key(1, 16) to Shift(16, 0, -36, 16), // n=434
            Key(2, 7) to Shift(7, 0, -30, -2), // n=112
            Key(2, 8) to Shift(8, 0, -28, 11), // n=2834
            Key(3, 8) to Shift(8, 0, 6, 0), // n=543
            Key(3, 22) to Shift(22, 10, -20, -3), // n=305
            Key(3, 50) to Shift(50, -8, 28, -8), // n=10932
            Key(4, 8) to Shift(8, 0, -16, 6), // n=10900
            Key(4, 18) to Shift(18, -6, -32, 12), // n=50
            Key(4, 19) to Shift(19, -11, -27, 10), // n=134
            Key(4, 20) to Shift(20, -4, -9, 8), // n=72
            Key(4, 21) to Shift(21, -10, -28, 8), // n=440
            Key(4, 22) to Shift(22, 11, -27, 10), // n=163
            Key(4, 24) to Shift(24, 6, -32, 12), // n=49
            Key(4, 25) to Shift(25, 6, -24, 8), // n=415
            Key(4, 26) to Shift(26, 2, -28, 8), // n=70
            Key(4, 43) to Shift(43, 1, -17, -6), // n=24
            Key(4, 44) to Shift(44, -1, -17, -5), // n=24
            Key(4, 59) to Shift(59, 4, -26, 2), // n=50
            Key(4, 60) to Shift(60, -4, -24, 9), // n=65
            Key(5, 28) to Shift(161, 4, 38, -2), // n=1793, the 667 skeleton's shield label
            Key(5, 161) to Shift(161, 12, 3, -10), // n=279
            Key(7, 31) to Shift(31, -4, -22, 2), // n=600
            Key(7, 32) to Shift(32, -2, -36, -2), // n=1513
            Key(7, 33) to Shift(33, -2, -27, 13), // n=441
            Key(7, 34) to Shift(34, -6, -12, 2), // n=579
            Key(7, 35) to Shift(35, 4, -12, 4), // n=562
            Key(7, 36) to Shift(36, 2, -28, 12), // n=395
            Key(7, 37) to Shift(37, 2, -22, 2), // n=606
            Key(7, 38) to Shift(38, 2, -36, -2), // n=1555
            Key(7, 40) to Shift(40, 3, -6, 0), // n=359
            Key(7, 42) to Shift(42, -2, -6, 0), // n=363
            Key(7, 43) to Shift(43, 4, -10, 0), // n=2222
            Key(7, 44) to Shift(44, -4, -10, -2), // n=1946
            Key(7, 76) to Shift(76, 1, -26, -1), // n=199
            Key(7, 77) to Shift(77, -2, -29, -1), // n=180
            Key(9, 19) to Shift(19, -12, -33, 8), // n=575
            Key(9, 22) to Shift(22, 12, -45, 8), // n=629
            Key(10, 32) to Shift(32, -2, -26, 3), // n=1054
            Key(10, 38) to Shift(38, 0, -26, 4), // n=1054
            Key(10, 45) to Shift(45, -2, -26, 7), // n=733
            Key(10, 46) to Shift(46, 0, -26, 0), // n=732
            Key(10, 47) to Shift(47, 2, -28, 1), // n=265
            Key(10, 48) to Shift(48, -2, -27, 5), // n=269
        )

    /** Measured per OSRS label over every slot (bodyframe_median.py items), for labels a slot row does not cover. */
    private val BY_LABEL: Map<Int, Shift> =
        mapOf(
            1 to Shift(1, 0, -26, 12), // n=221
            2 to Shift(2, 0, -16, 14), // n=89
            3 to Shift(3, 0, -20, 18), // n=50
            6 to Shift(6, 0, -56, -6), // n=43
            7 to Shift(7, 0, -24, -4), // n=49
            8 to Shift(8, 0, -22, 6), // n=282
            9 to Shift(9, 0, -24, 22), // n=81
            10 to Shift(10, 0, -32, 16), // n=79
            11 to Shift(11, 0, -26, 10), // n=57
            12 to Shift(12, 0, 6, 50), // n=72
            13 to Shift(13, 0, 2, 44), // n=70
            14 to Shift(14, 0, -36, 34), // n=82
            15 to Shift(15, 0, 18, 58), // n=57
            16 to Shift(16, 0, -38, 16), // n=23
            17 to Shift(17, -14, -35, 15), // n=20
            19 to Shift(19, -12, -24, 8), // n=63
            22 to Shift(22, 12, -44, 10), // n=77
            23 to Shift(23, 14, -35, 15), // n=20
            29 to Shift(29, 0, 4, 6), // n=136
            31 to Shift(31, -4, -22, 2), // n=107
            32 to Shift(32, -2, -30, -2), // n=193
            33 to Shift(33, -4, -18, 3), // n=42
            34 to Shift(34, -6, -12, 3), // n=102
            35 to Shift(35, 4, -12, 4), // n=96
            36 to Shift(36, 4, -16, 4), // n=32
            37 to Shift(37, 2, -22, 2), // n=107
            38 to Shift(38, 2, -28, 0), // n=191
            40 to Shift(40, 4, -16, 2), // n=25
            41 to Shift(41, 0, 0, 0), // n=39
            42 to Shift(42, -4, -8, 2), // n=25
            43 to Shift(43, 4, -11, 0), // n=86
            44 to Shift(44, -4, -12, -2), // n=80
            45 to Shift(45, -2, -28, 27), // n=50
            46 to Shift(46, 2, -28, 25), // n=50
            47 to Shift(47, 1, -31, 2), // n=58
            48 to Shift(48, -2, -33, 3), // n=58
            50 to Shift(50, -8, 26, -6), // n=231
            76 to Shift(76, 1, -26, -1), // n=28
            77 to Shift(77, -2, -29, -1), // n=28
            255 to Shift(255, 0, -24, 0), // n=53
        )

    /**
     * The hands. In 667 base 0 label 27 is animated exactly like the right hand 196 and 28 like the left hand 195 (bases2.py),
     * so they keep their labels; gloves measured 27 -> 196 (-4, -30, 9) n=696 and 28 -> 195 (4, -30, 12) n=664.
     */
    private val RIGHT_HAND = Shift(27, -4, -30, 9)
    private val LEFT_HAND = Shift(28, 4, -30, 12)

    /** Equipment slot of gloves, gauntlets, vambraces and bracelets. */
    const val HANDS_SLOT = 9

    /**
     * The hands slot per gender (owner 2026-10-06, Eclipse / crystal / Barrows gloves "invisible hands"; foto\Schermafbeelding
     * 2026-10-06 161923.png). The rows above (Key(9, 19/22), [RIGHT_HAND], [LEFT_HAND]) were one median over male AND female
     * gloves: the hand moved -30, where the male hand needs +16 and the female hand -50, so every male OSRS glove drew its hand
     * 46 units up inside the 667 forearm and its cuff at mid-forearm. Measured on the player's own hands kits, the same model id
     * in both caches (tools\cache667, label centres, 667 - OSRS x4): male 176 / 10218 - wrist 19/22 (-+11, +14, -2), hand
     * 27 -> 196/207/209 and 28 -> 195/194/204 (-+4, +16, -8); female 353 / 10706 - wrist (-+7, -67, +10), hand (-5 / +8, -50, +13).
     * Labels keep their ids (27/28 animate as the 667 hands, see [RIGHT_HAND]).
     */
    private val MALE_HANDS: Map<Int, Shift> =
        mapOf(19 to Shift(19, -11, 14, -2), 22 to Shift(22, 11, 14, -2), 27 to Shift(27, -4, 16, -8), 28 to Shift(28, 4, 16, -8))
    private val FEMALE_HANDS: Map<Int, Shift> =
        mapOf(19 to Shift(19, -7, -67, 10), 22 to Shift(22, 7, -67, 10), 27 to Shift(27, -5, -50, 13), 28 to Shift(28, 8, -50, 13))

    /**
     * [female]: the gender of a hands-slot mesh; null gives the shift as it was before 2026-10-06 (one row for both genders),
     * which only [OsrsWristTool] uses, to recognise a mesh converted that way.
     */
    fun shift(
        slot: Int,
        label: Int,
        female: Boolean? = null,
    ): Shift {
        if (slot == HANDS_SLOT && female != null) (if (female) FEMALE_HANDS else MALE_HANDS)[label]?.let { return it }
        BY_SLOT[Key(slot, label)]?.let { return it }
        if (label == 27) return RIGHT_HAND
        if (label == 28) return LEFT_HAND
        if (slot == 3) {
            // A weapon is one rigid piece hanging on the grip: every other label of it (whip lash 51-72, 87-88, ...) follows the
            // grip, except the left-hand item label 161, which follows the measured shield row.
            val grip = if (label == 161) BY_SLOT.getValue(Key(5, 161)) else BY_SLOT.getValue(Key(3, 50))
            return Shift(label, grip.dx, grip.dy, grip.dz)
        }
        BY_LABEL[label]?.let { return it }
        return Shift(label, ROOT.dx, ROOT.dy, ROOT.dz)
    }

    /** Equipment slot of the legs. */
    const val LEGS_SLOT = 7

    /** Shin vertex labels (left / right): knee to ankle, the same in both skeletons. */
    val SHIN_LABELS = setOf(32, 38)

    /** Knee and ankle heights (fine units) of the shin of one gender: OSRS after [shift], and rev 667. */
    data class Shin(val knee: Int, val osrsAnkle: Int, val ankle: Int)

    /**
     * Owner 2026-09-30 ("remember all osrs ported gear"): every OSRS legs item left the ankle bare above the 667 feet. The shin
     * is longer on the 2010 body. Measured on the default kits of both caches (legs idk 36 male / 70 female, feet 42 / 79,
     * shin labels 32/38, after [shift]): OSRS legs end at -108 (male) / -104 (female) and the OSRS feet start there, the 667
     * legs kit ends at -80 / -100 and the 667 feet (181 / 358) start at -84 / -100; the knees match (L31 rings, male OSRS -210 /
     * 667 -210, female -206 / -204). The label-centre translation above cannot lengthen a segment, so a legs mesh keeps the
     * 2007 shin and stops 28 units above the 667 ankle. [fitShin] maps the OSRS knee-to-ankle span onto the 667 one (and the
     * ankle-to-floor span onto the 667 one, so nothing goes through the floor); every legs imported before gets the same
     * from [OsrsShinTool].
     */
    val MALE_SHIN = Shin(knee = -210, osrsAnkle = -108, ankle = -80)
    val FEMALE_SHIN = Shin(knee = -206, osrsAnkle = -104, ankle = -100)

    /** The fitted height of an OSRS shin vertex at [y] (after [shift]): piecewise linear, knee and floor (0) fixed. */
    fun shinY(
        y: Int,
        shin: Shin,
    ): Int =
        when {
            y <= shin.knee -> y
            y <= shin.osrsAnkle -> (shin.knee + (y - shin.knee).toDouble() * (shin.ankle - shin.knee) / (shin.osrsAnkle - shin.knee)).roundToInt()
            y <= 0 -> (shin.ankle + (y - shin.osrsAnkle).toDouble() * -shin.ankle / -shin.osrsAnkle).roundToInt()
            else -> y
        }

    /** Moves the shin vertices of a converted legs [model] of the given gender onto the 667 shin ([MALE_SHIN] / [FEMALE_SHIN]). */
    fun fitShin(
        model: ModelData,
        female: Boolean,
    ): ModelData {
        val labels = model.vertexLabel ?: return model
        val shin = if (female) FEMALE_SHIN else MALE_SHIN
        for (i in 0 until model.vertexCount) if (labels[i] in SHIN_LABELS) model.vertexY[i] = shinY(model.vertexY[i], shin)
        return model
    }

    /**
     * Converts a version-12 (coarse, 2007 body) worn [model] of equipment [slot] in place to a version-13 mesh on the 667 body:
     * [shiftToRev667], and for the legs [fitShin] ([female] = the mesh is a female wear model; required for the legs and the hands).
     * [legacyHands]: a hands mesh converted the pre-2026-10-06 way (no gender), only for recognition in [OsrsWristTool].
     * Refuses a mesh that is already version 13 or more, so no model is ever moved twice.
     */
    fun toRev667Frame(
        model: ModelData,
        slot: Int,
        female: Boolean? = null,
        legacyHands: Boolean = false,
    ): ModelData {
        require(slot != LEGS_SLOT || female != null) { "a legs mesh needs its gender for the shin fit" }
        require(slot != HANDS_SLOT || female != null || legacyHands) { "a hands mesh needs its gender for the hand position" }
        shiftToRev667(model, slot, if (slot == HANDS_SLOT && legacyHands) null else female)
        if (slot == LEGS_SLOT) fitShin(model, female!!)
        return model
    }

    /**
     * The per-label translation alone - [toRev667Frame] as it was before the shin fit (2026-09-30), used by [OsrsShinTool].
     * [female] picks the hands-slot rows of that gender ([shift]).
     */
    fun shiftToRev667(
        model: ModelData,
        slot: Int,
        female: Boolean? = null,
    ): ModelData {
        require(model.version < FRAME_VERSION) { "mesh is already version ${model.version}; the body-frame conversion runs once" }
        require(slot in RENDERED_SLOTS) { "slot $slot is not drawn on the player" }
        val labels = model.vertexLabel
        for (i in 0 until model.vertexCount) {
            val s = if (labels == null) Shift(-1, ROOT.dx, ROOT.dy, ROOT.dz) else shift(slot, labels[i], female)
            model.vertexX[i] = model.vertexX[i] * 4 + s.dx
            model.vertexY[i] = model.vertexY[i] * 4 + s.dy
            model.vertexZ[i] = model.vertexZ[i] * 4 + s.dz
            if (labels != null) labels[i] = s.label
        }
        model.version = FRAME_VERSION
        return model
    }
}
