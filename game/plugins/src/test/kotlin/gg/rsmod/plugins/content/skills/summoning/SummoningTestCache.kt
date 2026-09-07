package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import java.nio.file.Paths

/**
 * One shared, lazily-loaded copy of the production cache for Summoning tests.
 *
 * ## Why this exists
 *
 * 38 test classes across this module each build their **own** `DefinitionSet` in a companion
 * object and load the whole production cache into it. A companion object lives for the entire test
 * JVM, so none of those copies is ever released and the suite's peak heap grows with every class
 * added. Adding one more tipped it over: `NewPlayerStartTests` failed with
 * `OutOfMemoryError: Java heap space` in a `@BeforeClass`, which is the same intermittent
 * "OOM in a random test class" that was previously worked around by raising the test heap to 3g.
 *
 * Raising the heap again would postpone the problem rather than fix it. Loading the cache once and
 * sharing it removes a whole copy per class that adopts this.
 *
 * Deliberately scoped: this is used by the Summoning tests that were added or changed with it, not
 * retro-fitted across all 38 classes, because that is a module-wide refactor and not part of the
 * task in hand. The remaining classes are a known, recorded capacity issue with an obvious fix.
 *
 * Safe to share: [DefinitionSet] is only ever read by tests, never mutated.
 */
object SummoningTestCache {
    val definitions: DefinitionSet by lazy {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        val definitions = DefinitionSet()
        definitions.loadAll(store)
        check(definitions.getCount(ItemDef::class.java) != 0) {
            "the production cache at data/cache loaded no item definitions"
        }
        definitions
    }
}
