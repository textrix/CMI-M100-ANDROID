package me.autolock.m100.cmi

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ProgressBar
import android.widget.TextView

class FotaDialog constructor(context: Context) : Dialog(context) {
    private var length = 0

    fun setProgress(progress: Int) {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val progressCurrent = findViewById<TextView>(R.id.progress_current)
        val progressPercent = findViewById<TextView>(R.id.progress_percent)
        val percent = progress * 100 / length
        progressCurrent.text = "$progress / $length"
        progressPercent.text = "$percent%"
        progressBar.progress = percent.toInt()
    }

    fun setLength(fileLength: Int) {
        length = fileLength
    }

    init {
        setCanceledOnTouchOutside(false)
        setCancelable(false)
        //window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_fota)
    }
}
