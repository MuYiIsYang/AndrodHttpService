package com.saoma.fuwuqi

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileLogger(private val context: Context) {

    private val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val TAG = "SaoMaFuWuQi"

    private fun logFile(): File {
        val dir = File(context.getExternalFilesDir(null), "logs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "server_${dateFmt.format(Date())}.log")
    }

    fun log(msg: String) {
        val timestamp = "[${timeFmt.format(Date())}]"
        val logMsg = "$timestamp $msg"
        
        // 写入文件
        val file = logFile()
        file.appendText("$logMsg\n")
        
        // 输出到logcat
        Log.d(TAG, msg)
    }
}
