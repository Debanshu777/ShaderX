package com.debanshu.shaderlab.shaderx.factory

/**
 * Skia (iOS / JVM-Desktop / Wasm) actual: manual access-order LRU using a
 * [LinkedHashMap] (insertion-order) with remove-then-reinsert on cache hit to
 * move the entry to the tail (most-recently-used position).
 *
 * No locking is applied. Compose's Skia rendering runs on a single thread per
 * window, which is the common-case caller. If you share a factory across threads
 * on Desktop or Wasm, synchronize externally.
 */
internal actual class LruCache<K : Any, V : Any> actual constructor(
    private val maxSize: Int,
    private val onEvict: (V) -> Unit,
) {
    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }
    private val map = LinkedHashMap<K, V>()

    actual val size: Int get() = map.size

    actual fun getOrPut(
        key: K,
        compute: () -> V,
    ): V {
        val existing = map[key]
        if (existing != null) {
            // Move to tail = mark as most-recently used
            map.remove(key)
            map[key] = existing
            return existing
        }
        // A failed compilation must not evict a useful cached effect.
        val value = compute()
        // Evict LRU entry if at capacity, releasing cache-owned native resources.
        if (map.size >= maxSize) {
            val iter = map.entries.iterator()
            if (iter.hasNext()) {
                val evicted = iter.next().value
                iter.remove()
                onEvict(evicted)
            }
        }
        map[key] = value
        return value
    }

    actual fun clear(onEvict: (V) -> Unit) {
        map.values.forEach {
            this.onEvict(it)
            onEvict(it)
        }
        map.clear()
    }
}
