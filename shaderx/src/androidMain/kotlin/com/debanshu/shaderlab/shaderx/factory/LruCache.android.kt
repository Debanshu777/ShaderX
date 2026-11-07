package com.debanshu.shaderlab.shaderx.factory

/**
 * Android actual: backed by [java.util.LinkedHashMap] with `accessOrder = true`
 * so that every `get` automatically reorders the entry to the most-recently-used position.
 * All public methods are synchronized on the map instance for thread safety.
 */
internal actual class LruCache<K : Any, V : Any> actual constructor(
    private val maxSize: Int,
    private val onEvict: (V) -> Unit,
) {
    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }
    private val map =
        object : java.util.LinkedHashMap<K, V>(16, 0.75f, /* accessOrder = */ true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>?): Boolean {
                val evict = size > maxSize
                if (evict && eldest != null) onEvict(eldest.value)
                return evict
            }
        }

    actual val size: Int get() = synchronized(map) { map.size }

    actual fun getOrPut(
        key: K,
        compute: () -> V,
    ): V {
        // Fast path: already cached. The synchronized get also marks the entry as
        // most-recently-used via access-order LinkedHashMap semantics.
        synchronized(map) { map[key] }?.let { return it }

        // Slow path: compile the shader *outside* the lock so other threads are not
        // blocked during potentially long shader compilation (50–500 ms on Android).
        val candidate = compute()

        // Write under lock. If another thread compiled the same shader concurrently,
        // prefer the existing value; the candidate is discarded (RuntimeShader has no
        // close() requirement, so GC handles it).
        return synchronized(map) {
            val existing = map[key]
            if (existing != null) {
                if (candidate !== existing) onEvict(candidate)
                existing
            } else {
                map[key] = candidate
                candidate
            }
        }
    }

    actual fun clear(onEvict: (V) -> Unit) =
        synchronized(map) {
            map.values.forEach {
                this.onEvict(it)
                onEvict(it)
            }
            map.clear()
        }
}
