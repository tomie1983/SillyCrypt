package com.dev.exfat.exfat.cache

class LruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    operator fun get(key: K): V? = map[key]

    operator fun set(key: K, value: V) {
        map[key] = value
    }

    fun clear() = map.clear()
}
