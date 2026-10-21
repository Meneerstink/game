package gg.rsmod.game.model

import gg.rsmod.util.io.SharedDataFiles
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.lookup.AbstractLookup
import org.apache.logging.log4j.core.lookup.StrLookup

/** Resolve logging paths through the same test isolation as the world registries, without logging recursively. */
@Plugin(name = "rspsscratch", category = StrLookup.CATEGORY)
class SharedDataPathLookup : AbstractLookup() {
    override fun lookup(event: LogEvent?, key: String?): String {
        try {
            return SharedDataFiles.of(requireNotNull(key), createIfMissing = false).path
        } catch (failure: Exception) {
            System.err.println("PROBLEM test logging path resolution failed: ${failure.javaClass.simpleName}")
            throw failure
        }
    }
}
