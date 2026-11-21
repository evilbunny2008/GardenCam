@file:JvmName("Log")
// Test stub for android.util.Log: called at runtime in place of the real class,
// and must return Int to match the real method signatures
@file:Suppress("SameReturnValue", "unused")

package android.util

fun e(tag: String, msg: String): Int {
    println("ERROR: $tag: $msg")
    return 0
}

fun i(tag: String, msg: String): Int {
    println("INFO: $tag: $msg")
    return 0
}

fun w(tag: String, msg: String): Int {
    println("WARN: $tag: $msg")
    return 0
}

fun d(tag: String, msg: String): Int {
    println("DEBUG: $tag: $msg")
    return 0
}