package com.orangefox.unofficial.util

/**
 * Reflection-based access to hidden android.os.SystemProperties.
 * Used by the Recovery Checker to look for TWRP / OrangeFox version props.
 */
fun systemProperty(key: String): String? = runCatching {
    val cls = Class.forName("android.os.SystemProperties")
    val get = cls.getMethod("get", String::class.java)
    get.invoke(null, key) as? String
}.getOrNull()
