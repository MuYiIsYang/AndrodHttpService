package com.saoma.fuwuqi

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataStorage {

    private val aData = ConcurrentHashMap<String, JSONObject>()
    private val bData = ConcurrentHashMap<String, JSONObject>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun saveA(deviceId: String, msg: String): JSONObject {
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("message", msg)
            put("timestamp", timeFmt.format(Date()))
        }
        aData[deviceId] = json
        return json
    }

    fun saveB(deviceId: String, msg: String): JSONObject {
        val json = JSONObject().apply {
            put("device_id", deviceId)
            put("message", msg)
            put("timestamp", timeFmt.format(Date()))
        }
        bData[deviceId] = json
        return json
    }

    fun getA(deviceId: String?): Any {
        return deviceId?.let { aData[it] ?: JSONObject() } ?: aData
    }

    fun getB(deviceId: String?): Any {
        return deviceId?.let { bData[it] ?: JSONObject() } ?: bData
    }
}
