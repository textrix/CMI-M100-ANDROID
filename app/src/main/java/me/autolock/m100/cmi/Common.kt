package me.autolock.m100.cmi

fun Byte.toPositiveInt() = toInt() and 0xFF

fun ByteArray.toHexString() = joinToString(separator = " ") {
    it.toPositiveInt().toString(16).padStart(2, '0').uppercase()
}

val APP_VERSION = "21.05.18.10.11"
