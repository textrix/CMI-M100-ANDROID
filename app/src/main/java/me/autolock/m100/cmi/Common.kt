package me.autolock.m100.cmi

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.text.NumberFormat

fun Byte.toPositiveInt() = toInt() and 0xFF

fun ByteArray.toHexString() = joinToString(separator = " ") {
    it.toPositiveInt().toString(16).padStart(2, '0').uppercase()
}

val Int.commaString: String
    get() = NumberFormat.getInstance().format(this)

fun <E> MutableList<E>.removeRange(from: Int, to: Int) {
    subList(from, to).clear()
}

const val APP_VERSION = "21.05.18.10.11"

const val REQ_READ_EXTERNAL_STORAGE = 1234
