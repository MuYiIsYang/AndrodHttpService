package com.saoma.fuwuqi

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class SimpleHttpServer(
    private val bindIp: String,
    port: Int,
    private val dataStorage: DataStorage,
    private val logger: FileLogger,
    private val uiLog: (String) -> Unit
) : NanoHTTPD(bindIp, port) {

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri
        val method = session.method

        val response: Response = when (path) {
            "/api/a_send" -> {
                val json = session.json()
                val id = json.optString("device_id")
                val msg = json.optString("message")
                if (id.isEmpty() || msg.isEmpty()) return jsonError("缺少 device_id 或 message")

                val result = dataStorage.saveA(id, msg)
                val logMsg = "A设备[$id] ->服务器 :$msg"
                logger.log(logMsg)
                uiLog(logMsg)

                jsonOk("A设备 $id 数据已保存", result)
            }
            "/api/b_send" -> {
                val json = session.json()
                val id = json.optString("device_id")
                val msg = json.optString("message")
                if (id.isEmpty() || msg.isEmpty()) return jsonError("缺少 device_id 或 message")

                val result = dataStorage.saveB(id, msg)
                val logMsg = "B设备[$id] -> 服务器:$msg"
                logger.log(logMsg)
                uiLog(logMsg)

                jsonOk("B设备 $id 数据已保存", result)
            }
            "/api/a_data" -> {
                val id = session.param("device_id")
                val data = dataStorage.getA(id)
                val logMsg = "服务器->B设备[$id]: $data"
                logger.log(logMsg)
                uiLog(logMsg)
                jsonOk("OK", data)
            }
            "/api/b_data" -> {
                val id = session.param("device_id")
                val data = dataStorage.getB(id)
                val logMsg = "服务器->A设备[$id]: $data"
                logger.log(logMsg)
                uiLog(logMsg)
                jsonOk("OK", data)
            }
            else -> jsonError("接口不存在")
        }

        return response
    }

    private fun jsonOk(msg: String, data: Any?) =
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            JSONObject().put("success", true).put("msg", msg).put("data", data).toString()
        )

    private fun jsonError(msg: String) =
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            JSONObject().put("success", false).put("error", msg).toString()
        )
}

// 安全解析 POST Body
fun NanoHTTPD.IHTTPSession.parseBodySafe(): String {
    return try {
        val body = HashMap<String, String>()
        this.parseBody(body)
        body["postData"] ?: ""
    } catch (e: Exception) {
        ""
    }
}

// 解析 JSON Body
fun NanoHTTPD.IHTTPSession.json(): JSONObject {
    val body = parseBodySafe()
    return if (body.isNotEmpty()) JSONObject(body) else JSONObject()
}

// GET/POST 参数
fun NanoHTTPD.IHTTPSession.param(key: String): String? {
    return this.parms[key] // parms 是 Map<String, String>
}
