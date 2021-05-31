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

fun getRealPathFromURI(uri: Uri): String? {
    if (uri.path?.startsWith("/storage") == true) {
        return uri.path
    }

    val id = DocumentsContract.getDocumentId(uri).split(":")[1]
    val columns = arrayOf( MediaStore.Files.FileColumns.DATA )
    val selection = MediaStore.Files.FileColumns._ID + " = " + id;
    val cursor = Application().contentResolver.query(MediaStore.Files.getContentUri("external"), columns, selection, null, null)
    cursor?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(columns[0])
        if (cursor.moveToFirst()) {
            return columnIndex?.let { cursor?.getString(it) }
        }
    }

    return null
}

const val APP_VERSION = "21.05.18.10.11"

const val REQ_READ_EXTERNAL_STORAGE = 1234
