package me.autolock.m100.cmi

fun ByteArray.toHexString() = joinToString(separator = " ") { it.toInt().and(0xff).toString(16).padStart(2, '0').uppercase() }
