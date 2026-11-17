package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.File

/** Existing Bloodhound definitions only. Wiki rev15375113 adds Dig; no pet/model identity is allocated. */
object CluePetDigCacheTool {
    val PETS=mapOf(14631 to "Bloodhound",14844 to "Upgraded Bloodhound")
    fun patch(id: Int,raw: ByteArray): ByteArray {
        require(raw.isNotEmpty() && raw.last().toInt()==0)
        fun decode(bytes: ByteArray)=NpcDef(id).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
        val before=decode(raw)
        require(before.name==PETS.getValue(id) && before.options[0]=="Talk-to" && before.options[2]=="Pick-up")
        require(before.options[3].isNullOrBlank() || before.options[3]=="Dig") { "Existing pet option4 must not be overwritten" }
        if(before.options[3]=="Dig")return raw
        // Preserve every original model, BAS, transform, custom Store option and byte. Add only fourth op.
        val result=raw.copyOf(raw.size-1)+byteArrayOf(33,68,105,103,0,0)
        val after=decode(result)
        check(after.name==before.name && after.basId==before.basId && after.size==before.size && after.options[3]=="Dig")
        check(before.options.indices.filter { it!=3 }.all { before.options[it]==after.options[it] })
        return result
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "Usage: <plan-tsv> [--apply] (Claude only)" }
        val apply="--apply" in args
        val caches=OsrsItemImportTool.TARGETS.map { CacheLibrary(it,false,null) }
        try {
            val mutations=PETS.keys.map { id ->
                val originals=caches.map { it.data(18,id ushr 7,id and 127) ?: error("Existing pet$id missing") }
                check(originals[0].contentEquals(originals[1])) { "Pet$id differs between target caches" }
                val raw=originals.first();val bytes=patch(id,raw)
                CacheMutation(18,id ushr 7,id and 127,bytes,"CLUE Bloodhound$id fourth option Dig",CacheItemProbeTool.sha1(raw))
            }
            val tx=CacheTransaction(OsrsItemImportTool.TARGETS,mutations)
            val plan=tx.preflight();check(tx.blockingErrors(plan).isEmpty())
            val report=File(args[0]);report.parentFile?.mkdirs()
            fun report(applied: Boolean)=report.writeText("# ${tx.id}; applied=$applied\n"+
                PETS.keys.joinToString("\n",postfix="\n") { "npc-option\t$it\t4\tDig" })
            report(false)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size}; no new NPC/model/sequence identities")
            if(!apply) { println("PLAN_ONLY; source14498 still requires its own verified import before gameplay");return }
            val result=tx.apply(plan);val errors=tx.verify()
            if(errors.isNotEmpty()) { tx.rollback();error("Pet dig verify failed and rolled back: $errors") }
            report(true)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { caches.forEach { it.close() } }
    }
}
