package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.factory.LruCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CacheDisposalTest {
    @Test
    fun capacityEvictionAndClear_disposeEachEntryOnce() {
        val disposed = mutableListOf<String>()
        val cache = LruCache<String, String>(2) { disposed += it }
        cache.getOrPut("a") { "A" }
        cache.getOrPut("b") { "B" }
        cache.getOrPut("a") { error("A must be cached") }
        cache.getOrPut("c") { "C" }
        assertEquals(listOf("B"), disposed)
        cache.clear()
        cache.clear()
        assertEquals(listOf("B", "A", "C"), disposed)
    }

    @Test
    fun failedComputeAtCapacity_preservesCachedValuesAndLruOrder() {
        val disposed = mutableListOf<String>()
        val cache = LruCache<String, String>(2) { disposed += it }
        cache.getOrPut("a") { "A" }
        cache.getOrPut("b") { "B" }
        assertFailsWith<IllegalStateException> {
            cache.getOrPut("broken") { error("compile failed") }
        }
        assertEquals(2, cache.size)
        assertEquals(emptyList(), disposed)
        cache.getOrPut("c") { "C" }
        assertEquals(listOf("A"), disposed)
        assertEquals("B", cache.getOrPut("b") { error("B must still be cached") })
    }

    @Test
    fun cacheCapacity_mustBePositive() {
        assertFailsWith<IllegalArgumentException> { LruCache<String, String>(0) }
        assertFailsWith<IllegalArgumentException> { LruCache<String, String>(-1) }
    }
}
