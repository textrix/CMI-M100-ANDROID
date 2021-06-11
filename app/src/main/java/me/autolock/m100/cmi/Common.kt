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

val crc16Table = (0 until 256).map {
    crc16(it.toUByte(), 0x1021.toUShort())
}

fun crc16(input: UByte, polynomial: UShort): UShort {
    val bigEndianInput = input.toUShort() shl 8
    return (0 until 8).fold(bigEndianInput) { result, _ ->
        val isMostSignificantBitOne =
            result and 0x8000.toUShort() != 0.toUShort()
        val shiftedResult = result shl 1
        when (isMostSignificantBitOne) {
            true -> shiftedResult xor polynomial
            false -> shiftedResult
        }
    }
}

fun crc16(inputs: UByteArray, initialValue: UShort = 0.toUShort()): UShort {
    return inputs.fold(initialValue) { remainder, byte ->
        val bigEndianInput = byte.toUShort() shl 8
        val index = (bigEndianInput xor remainder) shr 8
        crc16Table[index.toInt()] xor (remainder shl 8)
    }
}

fun crc16(inputs: ByteArray, initialValue: Short = 0): UShort =
    crc16(inputs.map(Byte::toUByte).toUByteArray(), initialValue.toUShort())

infix fun UShort.shl(bitCount: Int): UShort =
    (this.toUInt() shl bitCount).toUShort()

infix fun UShort.shr(bitCount: Int): UShort =
    (this.toUInt() shr bitCount).toUShort()

const val APP_VERSION = "21.05.18.10.11"

const val REQ_READ_EXTERNAL_STORAGE = 1234
