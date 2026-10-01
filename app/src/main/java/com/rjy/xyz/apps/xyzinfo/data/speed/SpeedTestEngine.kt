package com.rjy.xyz.apps.xyzinfo.data.speed

import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/**
 * 测速内核：延迟（含抖动）/ 下载 / 上传。
 *
 * 几个关键取舍（都是真机测速里最容易出错的地方）：
 *
 * 1. **不压缩**：请求头显式带 `Accept-Encoding: identity`。否则服务器可能回 gzip，
 *    拿到的字节数少于线路上真实跑的数据量，测出来的速度虚低。
 * 2. **多连接并行**：单条 TCP 连接压不满高带宽线路（千兆 / 5G 尤其明显），
 *    并行 N 条连接才接近真实带宽；界面里可以选 1 / 4 / 8 / 16。
 * 3. **掐掉起步阶段**（warm-up）：TCP 慢启动 + TLS 握手的那一两秒不计入结果，
 *    否则连接建立的开销会把结果整体拉低。
 * 4. **文件跑完自动续**：清华那台是 1GB 的测试文件，万兆环境下几秒就读完了，
 *    工作线程会重新开一条连接接着读，保证整个测速窗口里一直有数据在流。
 * 5. **取消要快**：用户点停止或退出页面时，所有连接立刻断开，不留在后台空转。
 *
 * 速度对外统一是**字节/秒**，显示时再由 [SpeedUnit] 换算成 Mbps / MB/s。
 */
object SpeedTestEngine {

    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000
    /**
     * 采样间隔：100ms（每秒 10 次）。
     *
     * 之前是 200ms（每秒 5 次），大数字看起来一顿一顿的；100ms 采样 + 下面的指数平滑
     * 能让读数「爬得顺」，又不会因为窗口太短而乱跳。
     */
    private const val SAMPLE_INTERVAL_MS = 100L

    /** 指数平滑系数：新值占 50%，一帧的抖动被吃掉一半，同时保留对真实速度变化的响应。 */
    private const val SAMPLE_SMOOTHING = 0.5
    private const val READ_BUFFER = 64 * 1024
    private const val UPLOAD_CHUNK = 256 * 1024

    /** 一次测速的取消令牌：Activity 退出 / 用户点停止时调用 [cancel]。 */
    class Session {
        @Volatile private var cancelled = false
        val isCancelled: Boolean get() = cancelled
        fun cancel() {
            cancelled = true
        }
    }

    enum class Phase { IDLE, LATENCY, DOWNLOAD, UPLOAD, FINISHED }

    data class Latency(
        val minMs: Double,
        val averageMs: Double,
        val jitterMs: Double,
        val samples: Int
    )

    data class Report(
        val serverId: String,
        val serverName: String,
        val latency: Latency?,
        val downloadBytesPerSecond: Double,
        val uploadBytesPerSecond: Double?,
        val uploadSkippedReason: String?,
        val connections: Int,
        val durationMs: Long
    )

    /** 已验证可用的下载地址缓存（避免每次测速都重新探测一遍）。 */
    private val resolvedUrls = ConcurrentHashMap<String, String>()

    // ---------- 延迟 ----------

    /**
     * 连通延迟：连续测 [samples] 次「发出请求 → 收到第一个字节」。
     *
     * 第一条样本包含 DNS + TLS 握手，明显偏高，直接丢掉；抖动取相邻两次的差值均值，
     * 这比标准差更贴近「打游戏会不会一卡一卡」的体感。
     */
    fun ping(server: SpeedTestServer, samples: Int = 4, timeoutMs: Int = 3_000): Latency? {
        val url = resolveDownloadUrl(server, timeoutMs) ?: return null
        val times = ArrayList<Double>(samples)
        repeat(samples) {
            val ms = timeToFirstByte(url, timeoutMs) ?: return@repeat
            times += ms
        }
        if (times.isEmpty()) return null
        val measured = if (times.size > 1) times.drop(1) else times
        val jitter = if (measured.size < 2) {
            0.0
        } else {
            measured.zipWithNext { a, b -> abs(b - a) }.average()
        }
        return Latency(
            minMs = measured.min(),
            averageMs = measured.average(),
            jitterMs = jitter,
            samples = measured.size
        )
    }

    /** 并行探测多台节点，返回「节点 id → 平均延迟(ms)」，探测失败的记 null。 */
    fun pingAll(
        servers: List<SpeedTestServer>,
        samples: Int = 3,
        timeoutMs: Int = 2_500
    ): Map<String, Double?> {
        if (servers.isEmpty()) return emptyMap()
        val results = ConcurrentHashMap<String, Double>()
        val threads = servers.map { server ->
            Thread({
                val latency = runCatching { ping(server, samples, timeoutMs) }.getOrNull()
                if (latency != null) results[server.id] = latency.averageMs
            }, "xyzinfo-speed-ping-${server.id}")
        }
        threads.forEach { it.start() }
        threads.forEach { runCatching { it.join((timeoutMs + 1_500).toLong()) } }
        return servers.associate { it.id to results[it.id] }
    }

    // ---------- 下载 / 上传 ----------

    /**
     * 多连接并行下载测速，返回平均速度（字节/秒）。
     *
     * @param onSample 每个采样点回调一次（瞬时速度、进度 0~1），用于界面上的实时数字。
     */
    fun download(
        session: Session,
        server: SpeedTestServer,
        connections: Int,
        durationMs: Long,
        warmUpMs: Long = 1_500L,
        onSample: ((Double, Double) -> Unit)? = null
    ): Double {
        val url = resolveDownloadUrl(server, CONNECT_TIMEOUT_MS) ?: return 0.0
        val total = AtomicLong(0)
        val active = java.util.Collections.synchronizedList(mutableListOf<HttpURLConnection>())
        val count = connections.coerceIn(1, 16)
        val deadline = System.currentTimeMillis() + durationMs
        val workers = (0 until count).map { index ->
            Thread(
                { downloadWorker(session, url, total, active, deadline) },
                "xyzinfo-speed-down-$index"
            )
        }
        return runSampleLoop(session, workers, active, total, durationMs, warmUpMs, onSample)
    }

    /**
     * 上传测速：往支持接收上传的节点 POST 数据（目前只有 Cloudflare）。
     * 节点不支持时返回 null，界面会说明原因而不是显示一个假数字。
     */
    fun upload(
        session: Session,
        server: SpeedTestServer,
        connections: Int,
        durationMs: Long,
        warmUpMs: Long = 1_500L,
        onSample: ((Double, Double) -> Unit)? = null
    ): Double? {
        val url = server.uploadUrls.firstOrNull() ?: return null
        val total = AtomicLong(0)
        val active = java.util.Collections.synchronizedList(mutableListOf<HttpURLConnection>())
        val count = connections.coerceIn(1, 8)
        val deadline = System.currentTimeMillis() + durationMs
        val workers = (0 until count).map { index ->
            Thread(
                { uploadWorker(session, url, total, active, deadline) },
                "xyzinfo-speed-up-$index"
            )
        }
        return runSampleLoop(session, workers, active, total, durationMs, warmUpMs, onSample)
    }

    /**
     * 完整流程：延迟 → 下载 → 上传（节点不支持就跳过）。
     *
     * 顺序固定是「先延迟、后下载、最后上传」：下载会把线路跑满，先测完延迟才不会
     * 被自己制造出来的拥塞污染。
     */
    fun runFullTest(
        session: Session,
        server: SpeedTestServer,
        connections: Int,
        downloadMs: Long,
        uploadMs: Long,
        onPhase: (Phase) -> Unit,
        onLatency: (Latency?) -> Unit,
        onDownload: (Double, Double) -> Unit,
        onUpload: (Double, Double) -> Unit
    ): Report {
        val startedAt = System.currentTimeMillis()

        onPhase(Phase.LATENCY)
        val latency = if (session.isCancelled) null else runCatching { ping(server, 4) }.getOrNull()
        onLatency(latency)

        var downloadBps = 0.0
        if (!session.isCancelled) {
            onPhase(Phase.DOWNLOAD)
            downloadBps = runCatching {
                download(session, server, connections, downloadMs, onSample = onDownload)
            }.getOrDefault(0.0)
        }

        var uploadBps: Double? = null
        var skipped: String? = null
        if (session.isCancelled) {
            skipped = "已取消"
        } else if (!server.supportsUpload) {
            // 校园镜像 / 测速文件服务只提供下载，不接收上传，实话实说
            skipped = "该节点只提供下载测速"
        } else {
            onPhase(Phase.UPLOAD)
            uploadBps = runCatching {
                upload(session, server, connections, uploadMs, onSample = onUpload)
            }.getOrNull()
            if (uploadBps == null) skipped = "上传测速失败（节点未响应）"
        }

        onPhase(Phase.FINISHED)
        return Report(
            serverId = server.id,
            serverName = server.name,
            latency = latency,
            downloadBytesPerSecond = downloadBps,
            uploadBytesPerSecond = uploadBps,
            uploadSkippedReason = skipped,
            connections = connections,
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    // ---------- 内部实现 ----------

    /**
     * 采样循环：启动工作线程，每 200ms 统计一次增量速度，到点收工。
     *
     * 平均值只统计 warm-up 之后的部分（起步慢不算用户网速的问题），
     * 界面上跳动的瞬时数字则用最近一次采样窗口的增量。
     */
    private fun runSampleLoop(
        session: Session,
        workers: List<Thread>,
        active: MutableList<HttpURLConnection>,
        total: AtomicLong,
        durationMs: Long,
        warmUpMs: Long,
        onSample: ((Double, Double) -> Unit)?
    ): Double {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + durationMs
        workers.forEach { it.start() }

        var baselineBytes = -1L
        var baselineAt = startedAt
        var previousBytes = 0L
        var previousAt = startedAt
        var lastRate = 0.0
        var smoothedRate = 0.0
        var hasRate = false

        while (!session.isCancelled && System.currentTimeMillis() < deadline) {
            Thread.sleep(SAMPLE_INTERVAL_MS)
            val now = System.currentTimeMillis()
            val bytes = total.get()
            if (baselineBytes < 0 && now - startedAt >= warmUpMs) {
                baselineBytes = bytes
                baselineAt = now
            }
            val deltaMs = now - previousAt
            if (deltaMs > 0) {
                lastRate = max(0.0, (bytes - previousBytes) * 1000.0 / deltaMs)
            }
            previousBytes = bytes
            previousAt = now

            // 平滑后的瞬时速度：界面上跳动的那个大数字用它，看起来才像「速度表」而不是随机数
            smoothedRate = if (hasRate) {
                smoothedRate + (lastRate - smoothedRate) * SAMPLE_SMOOTHING
            } else {
                lastRate
            }
            hasRate = true

            onSample?.invoke(
                smoothedRate,
                ((now - startedAt).toDouble() / durationMs).coerceIn(0.0, 1.0)
            )
        }

        workers.forEach { runCatching { it.join(2_000) } }
        // 还有没退出来的（多半卡在写 socket），直接断掉，别让线程留在后台
        synchronized(active) { active.forEach { runCatching { it.disconnect() } } }
        workers.forEach { runCatching { it.join(500) } }

        val endedAt = System.currentTimeMillis()
        val totalBytes = total.get()
        if (baselineBytes < 0) {
            baselineBytes = 0
            baselineAt = startedAt
        }
        val seconds = (endedAt - baselineAt) / 1000.0
        return if (seconds <= 0.2) 0.0 else max(0.0, (totalBytes - baselineBytes) / seconds)
    }

    private fun downloadWorker(
        session: Session,
        url: String,
        total: AtomicLong,
        active: MutableList<HttpURLConnection>,
        stopAt: Long
    ) {
        val buffer = ByteArray(READ_BUFFER)
        while (!session.isCancelled && System.currentTimeMillis() < stopAt) {
            val connection = runCatching {
                SpeedHttp.open(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS).apply {
                    // 只要从 0 开始的字节流即可；服务器不支持 Range 时照样能读
                    setRequestProperty("Range", "bytes=0-")
                }
            }.getOrNull() ?: return
            active.add(connection)
            try {
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    return
                }
                connection.inputStream.use { input ->
                    while (!session.isCancelled && System.currentTimeMillis() < stopAt) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total.addAndGet(read.toLong())
                    }
                }
            } catch (_: Throwable) {
                // 到点主动断连 / 被取消都会走到这里，属于正常收工，不当成错误
                if (session.isCancelled) return
                runCatching { Thread.sleep(60) }
            } finally {
                active.remove(connection)
                runCatching { connection.disconnect() }
            }
        }
    }

    private fun uploadWorker(
        session: Session,
        url: String,
        total: AtomicLong,
        active: MutableList<HttpURLConnection>,
        stopAt: Long
    ) {
        val chunk = ByteArray(UPLOAD_CHUNK)
        while (!session.isCancelled && System.currentTimeMillis() < stopAt) {
            val connection = runCatching {
                SpeedHttp.open(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS).apply {
                    requestMethod = "POST"
                    doOutput = true
                    // 分块上传：数据量由测速窗口决定，不预先定长；到点直接断开即可
                    setChunkedStreamingMode(UPLOAD_CHUNK)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
            }.getOrNull() ?: return
            active.add(connection)
            try {
                connection.outputStream.use { output ->
                    while (!session.isCancelled && System.currentTimeMillis() < stopAt) {
                        output.write(chunk)
                        total.addAndGet(chunk.size.toLong())
                    }
                    runCatching { output.flush() }
                }
                runCatching { connection.responseCode }
            } catch (_: Throwable) {
                if (session.isCancelled) return
                runCatching { Thread.sleep(60) }
            } finally {
                active.remove(connection)
                runCatching { connection.disconnect() }
            }
        }
    }

    /**
     * 挑一个真的能用的下载地址。
     *
     * 每次只拉 1KB 就断开——既能确认「这台还活着」，又不会白白跑掉几百兆流量。
     * 结果缓存在内存里，同一次运行里不用反复探测。
     */
    fun resolveDownloadUrl(server: SpeedTestServer, timeoutMs: Int): String? {
        resolvedUrls[server.id]?.let { return it }
        server.downloadUrls.forEach { candidate ->
            if (probe(candidate, timeoutMs)) {
                resolvedUrls[server.id] = candidate
                return candidate
            }
        }
        // 全都没探通（比如公司网络拦了）：仍然返回第一个地址，
        // 让正式测速去暴露真实错误，而不是在这里就判死刑
        return server.downloadUrls.firstOrNull()
    }

    private fun probe(url: String, timeoutMs: Int): Boolean = runCatching {
        val connection = SpeedHttp.open(url, timeoutMs, timeoutMs).apply {
            setRequestProperty("Range", "bytes=0-1023")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                return false
            }
            val buffer = ByteArray(256)
            connection.inputStream.use { it.read(buffer) > 0 }
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrDefault(false)

    /** 连上并读到第一个字节用了多久（毫秒）；失败返回 null。 */
    private fun timeToFirstByte(url: String, timeoutMs: Int): Double? = runCatching {
        val startedAt = System.nanoTime()
        val connection = SpeedHttp.open(url, timeoutMs, timeoutMs).apply {
            setRequestProperty("Range", "bytes=0-1023")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                return null
            }
            val buffer = ByteArray(256)
            val read = connection.inputStream.use { it.read(buffer) }
            if (read <= 0) return null
            (System.nanoTime() - startedAt) / 1_000_000.0
        } finally {
            runCatching { connection.disconnect() }
        }
    }.getOrNull()
}
