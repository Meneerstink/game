package gg.rsmod.game.tools.importer

/** Preserve only a byte-proven native ambient sound; an OSRS numeric id alone proves nothing. */
object ClueMimicArenaAssets {
    fun preserveAmbient(raw: ByteArray,source: ModernObjectDef,synth: ByteArray?,targets: List<ByteArray?>): ByteArray {
        require(raw.isNotEmpty() && raw.last().toInt()==0)
        require(source.ambientSoundId in 0..65534 && source.ambientSoundRadius in 0..255)
        require(source.ambientSoundRetain==0) { "Nonzero OSRS ambient retain is not representable by rev667 opcode78" }
        require(synth!=null && targets.size==2 && targets.all { it!=null && synth.contentEquals(it) }) {
            "Ambient source${source.ambientSoundId} must be byte-identical in BOTH targets before native-id reuse"
        }
        val id=source.ambientSoundId
        return raw.copyOf(raw.size-1)+byteArrayOf(78,(id ushr 8).toByte(),id.toByte(),source.ambientSoundRadius.toByte(),0)
    }
}
