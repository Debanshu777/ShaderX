package com.debanshu.shaderlab.shaderx.factory

/**
 * A bounded, access-order Least-Recently-Used cache.
 *
 * Thread-safety guarantees depend on the platform actual:
 * - Android: fully synchronized via `java.util.LinkedHashMap(accessOrder=true)`.
 * - Skia (iOS/Desktop/Wasm): insertion-order map with manual move-to-tail on read;
 *   safe for single-threaded Compose rendering; callers must synchronize externally
 *   if accessed from multiple threads on those targets.
 *
 * @param maxSize Maximum number of entries to retain. When exceeded, the least-recently-used
 *   entry is evicted automatically. Must be positive.
 * @param onEvict Releases values discarded on eviction, clearing, or a concurrent cache miss.
 */
internal expect class LruCache<K : Any, V : Any>(
    maxSize: Int,
    onEvict: (V) -> Unit = {},
) {
    val size: Int

    /**
     * Returns the cached value for [key] if present, or computes and caches a new value
     * via [compute] on a cache miss. The returned value is marked as most-recently used.
     */
    fun getOrPut(
        key: K,
        compute: () -> V,
    ): V

    /**
     * Removes all entries, invoking the constructor's disposal callback and then [onEvict]
     * for each entry. The optional callback can observe explicitly cleared entries.
     */
    fun clear(onEvict: (V) -> Unit = {})
}
