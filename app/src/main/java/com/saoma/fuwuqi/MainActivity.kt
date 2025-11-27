package com.saoma.fuwuqi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private var server: SimpleHttpServer? = null
    private lateinit var logger: FileLogger
    private val dataStorage = DataStorage()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger = FileLogger(this)

        setContent {
            ServerUI()
        }
    }

    @Composable
    fun ServerUI() {
        val logItems = remember { mutableStateListOf<Pair<String, androidx.compose.ui.graphics.Color>>() }
        var serverRunning by remember { mutableStateOf(false) }
        var inputIp by remember { mutableStateOf(getLocalIp()) }   // 默认 LAN IP
        var inputPort by remember { mutableStateOf("12123") }   // 默认端口
        val currentLanIp by remember { mutableStateOf(getLocalIp()) }
        var currentWanIp by remember { mutableStateOf("获取中...") }
        
        // 异步获取公网IP，支持重试
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                var attempt = 0
                val maxAttempts = 3
                var success = false
                
                while (attempt < maxAttempts && !success) {
                    attempt++
                    val ip = getPublicIp()
                    if (ip != null) {
                        currentWanIp = ip
                        success = true
                    } else {
                        // 重试前短暂延迟
                        if (attempt < maxAttempts) {
                            Thread.sleep(1000)
                        }
                    }
                }
                
                if (!success) {
                    currentWanIp = "获取失败，请检查网络连接"
                }
            }
        }
        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        val red = androidx.compose.ui.graphics.Color.Red
        val green = androidx.compose.ui.graphics.Color.Green
        val black = androidx.compose.ui.graphics.Color.Black

        fun appendLog(msg: String) {
            val timestamp = "[${timeFmt.format(Date())}]"
            val coloredMsg = "$timestamp $msg"
            
            // 根据日志内容设置颜色
            val color = when {
                // 进入服务器的数据（A1 ->服务器 或 B1 -> 服务器）
                msg.contains("->服务器") || msg.contains("-> 服务器") -> red
                // 发出去的数据（服务器->）
                msg.startsWith("服务器->") -> green
                // 其他日志（如服务器启动/停止）
                else -> black
            }
            
            logItems.add(0, Pair(coloredMsg, color))
            // 限制日志数量，避免内存占用过大
            if (logItems.size > 1000) {
                val newList = logItems.take(1000).toList()
                logItems.clear()
                logItems.addAll(newList)
            }
        }

        MaterialTheme {
            Column(
                Modifier.fillMaxSize().padding(16.dp)
            ) {

                Text("滴滴扫码服务器", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // 输入 IP 和端口
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = inputIp,
                        onValueChange = { inputIp = it },
                        label = { Text("绑定的IP（可填内网或公网）") },
                        modifier = Modifier.weight(3f)
                    )

                    OutlinedTextField(
                        value = inputPort,
                        onValueChange = { inputPort = it },
                        label = { Text("端口") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (!serverRunning) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val port = inputPort.toIntOrNull() ?: 12123
                                        
                                        // 验证端口范围
                                        if (port !in 1..65535) {
                                            throw IllegalArgumentException("端口号必须在1-65535之间")
                                        }
                                        
                                        // 验证IP地址格式
                                        if (!isValidIpAddress(inputIp)) {
                                            throw IllegalArgumentException("IP地址格式不正确")
                                        }
                                        
                                        // 对于公网IP地址，绑定到0.0.0.0（所有网络接口）
                                        // 这样服务器可以接受来自公网的请求（需要路由器端口转发）
                                        val bindIp = if (inputIp == "0.0.0.0") "0.0.0.0" else inputIp
                                        
                                        server = SimpleHttpServer(
                                            bindIp,
                                            port,
                                            dataStorage,
                                            logger
                                        ) { msg ->
                                            appendLog(msg)
                                        }

                                        server!!.start()

                                        withContext(Dispatchers.Main) {
                                            serverRunning = true
                                            appendLog("服务器已启动: http://$inputIp:$port")
                                            appendLog("注意: 如果使用公网IP，需要在路由器上设置端口转发")
                                        }

                                    } catch (e: IllegalArgumentException) {
                                        withContext(Dispatchers.Main) {
                                            appendLog("启动失败: ${e.message}")
                                        }
                                    } catch (e: java.net.BindException) {
                                        withContext(Dispatchers.Main) {
                                            if (e.message?.contains("Address already in use") == true) {
                                                appendLog("启动失败: 端口 $inputPort 已被占用")
                                            } else if (e.message?.contains("Cannot assign requested address") == true) {
                                                // 如果是因为IP地址不是本地接口导致的绑定失败
                                                // 尝试绑定到0.0.0.0并重新启动
                                                try {
                                                    val port = inputPort.toIntOrNull() ?: 12123
                                                    server = SimpleHttpServer(
                                                        "0.0.0.0", // 绑定到所有网络接口
                                                        port,
                                                        dataStorage,
                                                        logger
                                                    ) { msg ->
                                                        appendLog(msg)
                                                    }
                                                    server!!.start()
                                                    serverRunning = true
                                                    appendLog("已自动切换到绑定所有网络接口")
                                                    appendLog("服务器已启动: http://$inputIp:$port")
                                                    appendLog("注意: 如果使用公网IP，需要在路由器上设置端口转发")
                                                } catch (retryEx: Exception) {
                                                    appendLog("启动失败: 尝试绑定到所有网络接口也失败了: ${retryEx.message}")
                                                }
                                            } else {
                                                appendLog("启动失败: 无法绑定到指定的IP地址和端口: ${e.message}")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            appendLog("启动失败: ${e.message}")
                                            appendLog("错误详情: ${e.stackTraceToString()}")
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !serverRunning
                    ) {
                        Text("启动服务器")
                    }

                    Spacer(Modifier.width(16.dp))

                    Button(
                        onClick = {
                            server?.stop()
                            server = null
                            serverRunning = false
                            appendLog("服务器已停止")
                        },
                        enabled = serverRunning
                    ) {
                        Text("停止服务器")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 服务器信息
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "内网IP: $currentLanIp",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "外网IP: $currentWanIp",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Text(
                            text = "当前连接的A设备: ${(dataStorage.getA(null) as? java.util.concurrent.ConcurrentHashMap<*, *>)?.size ?: 0}",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "当前连接的B设备: ${(dataStorage.getB(null) as? java.util.concurrent.ConcurrentHashMap<*, *>)?.size ?: 0}",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("日志：")

                Box(
                    Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) {
                        logItems.forEach { (text, color) ->
                            Text(
                                text = text,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }


    // 获取局域网IP
    private fun getLocalIp(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (inf in interfaces) {
            val addrs = inf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is InetAddress) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains(".")) return ip
                }
            }
        }
        return "0.0.0.0" // 绑定到所有网络接口
    }
    
    /**
     * 获取设备的公网IP地址
     */
    private fun getPublicIp(): String? {
        // 定义多个公网IP获取服务，提高成功率
        val ipServices = listOf(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://icanhazip.com",
            "https://ipecho.net/plain"
        )
        
        for (serviceUrl in ipServices) {
            try {
                val url = java.net.URL(serviceUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                
                // 禁用重定向（某些服务可能会重定向）
                connection.instanceFollowRedirects = true
                
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {  // 接受所有2xx状态码
                    val inputStream = connection.inputStream
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val response = reader.use { it.readText() }  // 使用use自动关闭资源
                    inputStream.close()
                    
                    // 处理响应内容
                    var ip = response.trim()
                    
                    // 简单的IP地址提取逻辑，处理可能的额外内容
                    val ipMatch = Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").find(ip)
                    if (ipMatch != null) {
                        ip = ipMatch.value
                        
                        // 验证IP地址格式是否有效
                        if (isValidIpAddress(ip)) {
                            return ip
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略单个服务的错误，尝试下一个
                System.err.println("获取公网IP失败（${serviceUrl}）: ${e.message}")
            }
        }
        return null
    }
    
    /**
     * 验证IP地址是否有效
     */
    private fun isValidIpAddress(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255 && part == num.toString()  // 确保没有前导零
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
}
