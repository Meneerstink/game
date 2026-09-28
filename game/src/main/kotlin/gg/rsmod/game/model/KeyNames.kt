package gg.rsmod.game.model

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TimerKey
import io.github.classgraph.ClassGraph
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Names for attribute and timer keys in conformance timelines ("timer.FREEZE_TIMER" instead of an anonymous object). Keys are
 * plain objects held in static fields (top-level vals, companions, objects), so one ClassGraph scan of gg.rsmod finds every
 * static AttributeKey/TimerKey field and remembers "Owner.FIELD" for it. A key with no static field (a local one) falls back
 * to its persistence key or identity hash.
 */
object KeyNames {
    private val attrs = IdentityHashMap<AttributeKey<*>, String>()
    private val timers = IdentityHashMap<TimerKey, String>()

    @Volatile
    private var scanned = false

    // The class loader that sees the plugin classes (set by the recorder before the first lookup).
    @Volatile
    var loader: ClassLoader = KeyNames::class.java.classLoader

    fun attr(key: AttributeKey<*>): String {
        ensure()
        return attrs[key] ?: key.persistenceKey ?: "attr@${System.identityHashCode(key).toString(16)}"
    }

    fun timer(key: TimerKey): String {
        ensure()
        return timers[key] ?: key.persistenceKey ?: "timer@${System.identityHashCode(key).toString(16)}"
    }

    @Synchronized
    private fun ensure() {
        if (scanned) return
        scanned = true
        val started = System.currentTimeMillis()
        val attrType = AttributeKey::class.java.name
        val timerType = TimerKey::class.java.name
        runCatching {
            ClassGraph().whitelistPackages("gg.rsmod").enableFieldInfo().ignoreFieldVisibility().addClassLoader(loader).scan().use { result: io.github.classgraph.ScanResult ->
                for (info in result.allClasses) {
                    val wanted =
                        info.declaredFieldInfo.filter { f: io.github.classgraph.FieldInfo ->
                            f.isStatic && f.typeSignatureOrTypeDescriptor.toString().let { it.startsWith(attrType) || it.startsWith(timerType) }
                        }
                    if (wanted.isEmpty()) continue
                    val cls = runCatching { Class.forName(info.name, true, loader) }.getOrNull() ?: continue
                    val owner = info.simpleName.removeSuffix("Kt").removeSuffix("_plugin")
                    for (f in wanted) {
                        val field = runCatching { cls.getDeclaredField(f.name) }.getOrNull() ?: continue
                        if (!Modifier.isStatic(field.modifiers)) continue
                        field.isAccessible = true
                        when (val v = runCatching { field.get(null) }.getOrNull()) {
                            is AttributeKey<*> -> attrs.putIfAbsent(v, "$owner.${f.name}")
                            is TimerKey -> timers.putIfAbsent(v, "$owner.${f.name}")
                        }
                    }
                }
            }
        }.onFailure { logger.warn("Conformance key-name scan failed: {}", it.toString()) }
        logger.info("Conformance key names: {} attributes, {} timers ({} ms)", attrs.size, timers.size, System.currentTimeMillis() - started)
    }
}
