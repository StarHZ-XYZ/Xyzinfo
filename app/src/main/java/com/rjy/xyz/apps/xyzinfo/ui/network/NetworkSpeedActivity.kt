package com.rjy.xyz.apps.xyzinfo.ui.network

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.data.speed.GeoLocator
import com.rjy.xyz.apps.xyzinfo.data.speed.Geodesy
import com.rjy.xyz.apps.xyzinfo.data.speed.SpeedTestCatalog
import com.rjy.xyz.apps.xyzinfo.data.speed.SpeedTestEngine
import com.rjy.xyz.apps.xyzinfo.data.speed.SpeedTestServer
import com.rjy.xyz.apps.xyzinfo.data.speed.SpeedUnit
import com.rjy.xyz.apps.xyzinfo.data.speed.formatMillis
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityNetworkSpeedBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.ThemeColors
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 网络测速页。
 *
 * 流程：
 * 1. 进页面先按**出口 IP 归属地**定位（不申请定位权限），按大圆距离筛出最近的若干台节点；
 * 2. 对这几十上百台里最近的几台**并行实测延迟**，选延迟最低的那台（也可以手动切换）；
 * 3. 开始测速：延迟 → 下载 → 上传；单位可切 Mbps / MB/s，连接数可切 1 / 4 / 8 / 16；
 * 4. 另外提供「多节点并行对比」：同时跑最近的 3 台，看哪台对当前网络最快。
 */
class NetworkSpeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNetworkSpeedBinding
    private val handler = Handler(Looper.getMainLooper())

    private var unit = SpeedUnit.MBPS
    private var connections = DEFAULT_CONNECTIONS
    private var useLocation = true

    private var geo: GeoLocator.UserGeo? = null
    private var nearby: List<SpeedTestServer> = emptyList()
    /** 上行阶段用的节点：下载节点不支持上传时会换到这台（记下来给界面显示）。 */
    private var uploadNode: SpeedTestServer? = null
    private val pingCache = LinkedHashMap<String, Double?>()
    private var selected: SpeedTestServer? = null

    private var nodeListShown = false
    private var discovering = false
    private var running = false
    private var comparing = false
    private var session: SpeedTestEngine.Session? = null

    private var hasResult = false
    private var lastDownloadBps = 0.0
    private var lastUploadBps: Double? = null
    private var lastCompare: Pair<List<SpeedTestServer>, Map<String, Double>>? = null

    /** 高频回调（每 200ms 一次）限流，别让 UI 线程被刷屏。 */
    @Volatile private var lastPushAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkSpeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        unit = SpeedUnit.fromName(SettingsRepository.speedUnit(this))
        connections = SettingsRepository.speedConnections(this)
        useLocation = SettingsRepository.speedUseLocation(this)

        buildUnitChips()
        buildParallelChips()
        buildLocationChips()
        showLastResult()
        renderHistory()
        applyUnitToReadout()

        Anim.pressFeedback(
            binding.btnStart, binding.btnRelocate, binding.btnSwitchNode,
            binding.btnProbeNodes, binding.btnCompare
        )

        binding.btnStart.setOnClickListener { toggleTest() }
        binding.btnRelocate.setOnClickListener { startDiscovery() }
        binding.btnSwitchNode.setOnClickListener { toggleNodeList() }
        binding.btnProbeNodes.setOnClickListener { probeNodes() }
        binding.btnCompare.setOnClickListener { startCompare() }
        binding.btnClearHistory.setOnClickListener {
            SettingsRepository.clearSpeedRecords(this)
            renderHistory()
            toast("测速记录已清空")
        }

        startDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 页面没了就别再占着连接跑测速：立刻让所有工作线程收工
        session?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    // ---------- 定位 + 选节点 ----------

    private fun startDiscovery() {
        if (discovering) return
        if (running) {
            toast("测速进行中，先停下来再换节点")
            return
        }
        discovering = true
        if (useLocation) {
            binding.tvGeoStatus.setInfoRow("正在检测所在位置…")
        } else {
            binding.tvGeoStatus.setInfoRow(
                "定位测速：已关闭（不出网定位，直接用通用候选节点，可手动切换）"
            )
        }
        binding.tvNodeName.text = "正在挑选就近节点…"
        binding.tvNodeDetail.text = "按地理位置筛出最近的若干台，再实测延迟决定用哪一台"

        Thread({
            // 关掉定位测速时**根本不发定位请求**，省一次出网、也彻底不碰位置信息
            val detected = if (useLocation) runCatching { GeoLocator.detect() }.getOrNull() else null
            val candidates = runCatching {
                val coordinates = detected?.coordinates
                if (coordinates != null) {
                    SpeedTestCatalog.nearest(coordinates.first, coordinates.second, NEARBY_COUNT)
                } else {
                    SpeedTestCatalog.defaultOrder(NEARBY_COUNT)
                }
            }.getOrDefault(emptyList())
            // 只对最近几台做延迟探测：太少容易挑到"近但慢"的，太多又开始比距离还慢
            val pings = runCatching {
                SpeedTestEngine.pingAll(candidates.take(PROBE_COUNT), samples = 3, timeoutMs = 2_500)
            }.getOrDefault(emptyMap())

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                discovering = false
                geo = detected
                nearby = candidates
                pingCache.clear()
                pingCache.putAll(pings)
                renderGeo()
                selectBestNode()
                // 上行专用节点：按同一套定位挑一台支持上传的（Cloudflare / Linode 系列）
                uploadNode = SpeedTestCatalog.nearestUploadCapable(
                    detected?.latitude, detected?.longitude
                )
                renderNodeList()
            }
        }, "xyzinfo-speed-discovery").start()
    }

    /** 把探测到的延迟重新测一遍（用户怀疑换网络了 / 结果不对劲时用）。 */
    private fun probeNodes() {
        if (discovering || running || nearby.isEmpty()) {
            if (nearby.isEmpty()) toast("还没找到可用节点，先点「重新定位」")
            return
        }
        discovering = true
        binding.tvNodeName.text = "正在重新检测各节点延迟…"
        Thread({
            val pings = runCatching {
                SpeedTestEngine.pingAll(nearby.take(PROBE_COUNT), samples = 3, timeoutMs = 2_500)
            }.getOrDefault(emptyMap())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                discovering = false
                pingCache.putAll(pings)
                selectBestNode()
                renderNodeList()
            }
        }, "xyzinfo-speed-probe").start()
    }

    private fun renderGeo() {
        val detected = geo
        if (!useLocation) {
            binding.tvGeoStatus.setInfoRow(
                "定位测速：已关闭 ｜ 使用通用候选节点（可在「测速设置」里打开定位）"
            )
            return
        }
        if (detected == null) {
            binding.tvGeoStatus.setInfoRow("位置：未识别（定位接口没连上，已改用通用候选节点）")
            return
        }
        val place = detected.placeText.ifBlank { "未知地区" }
        val ip = detected.ip.ifBlank { "未知" }
        val isp = detected.isp.ifBlank { "未知" }
        binding.tvGeoStatus.setInfoRow(
            "位置：$place ｜ IP：$ip\n运营商：$isp（按出口 IP 判断，不申请定位权限）"
        )
    }

    /** 就近选择：探测到延迟的取最低，全都探不到就退回「地理上最近的那台」。 */
    private fun selectBestNode() {
        if (nearby.isEmpty()) {
            selected = null
            renderSelectedNode()
            return
        }
        val measured = nearby.take(PROBE_COUNT).filter { pingCache[it.id] != null }
        selected = measured.minByOrNull { pingCache[it.id] ?: Double.MAX_VALUE } ?: nearby.first()
        renderSelectedNode()
    }

    private fun renderSelectedNode() {
        val server = selected
        if (server == null) {
            binding.tvNodeName.text = "没有可用节点"
            binding.tvNodeDetail.text = "检查一下网络连接，然后点「重新定位」重试"
            return
        }
        binding.tvNodeName.text = server.name
        binding.tvNodeDetail.text = nodeDetailText(server) + " ｜ " +
            if (server.supportsUpload) "支持上传测速" else "仅下载测速"
    }

    private fun nodeDetailText(server: SpeedTestServer): String {
        val parts = mutableListOf(server.placeText)
        val coordinates = geo?.coordinates
        when {
            server.anycast -> parts += "任播节点（自动就近接入）"
            coordinates != null -> parts += Geodesy.distanceText(
                server.distanceKm(coordinates.first, coordinates.second)
            )
        }
        val ping = pingCache[server.id]
        parts += if (ping == null) "延迟未测出" else "延迟 ${formatMillis(ping)}"
        return parts.joinToString(" ｜ ")
    }

    private fun toggleNodeList() {
        if (nearby.isEmpty()) {
            toast("还没找到可用节点，先点「重新定位」")
            return
        }
        nodeListShown = !nodeListShown
        binding.layoutNodeList.visibility = if (nodeListShown) View.VISIBLE else View.GONE
        binding.btnSwitchNode.text = if (nodeListShown) "收起列表" else "切换节点"
        if (nodeListShown) renderNodeList()
    }

    private fun renderNodeList() {
        binding.layoutNodeList.removeAllViews()
        if (!nodeListShown) return
        nearby.forEach { server -> binding.layoutNodeList.addView(createNodeRow(server)) }
    }

    private fun createNodeRow(server: SpeedTestServer): View {
        val isSelected = server.id == selected?.id
        val title = TextView(this).apply {
            text = server.name
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                if (isSelected) {
                    ThemeColors.accent(this@NetworkSpeedActivity)
                } else {
                    ContextCompat.getColor(this@NetworkSpeedActivity, R.color.text_primary)
                }
            )
        }
        val detail = TextView(this).apply {
            text = nodeDetailText(server)
            textSize = 11.5f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(detail)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = ContextCompat.getDrawable(this@NetworkSpeedActivity, R.drawable.bg_row_panel)
            addView(
                texts,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (isSelected) {
                addView(
                    TextView(this@NetworkSpeedActivity).apply {
                        text = "当前"
                        textSize = 11f
                        setTextColor(ThemeColors.accent(this@NetworkSpeedActivity))
                        background = ContextCompat.getDrawable(
                            this@NetworkSpeedActivity, R.drawable.bg_chip
                        )
                        setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                    }
                )
            }
            isClickable = true
            setOnClickListener {
                Anim.pressFeedback(this)
                selectNode(server)
            }
        }
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6f) }
        return row
    }

    private fun selectNode(server: SpeedTestServer) {
        selected = server
        renderSelectedNode()
        renderNodeList()
        nodeListShown = false
        binding.layoutNodeList.visibility = View.GONE
        binding.btnSwitchNode.text = "切换节点"
    }

    // ---------- 测速 ----------

    private fun toggleTest() {
        if (running) {
            session?.cancel()
            binding.tvPhase.text = "正在停止…"
            return
        }
        val server = selected
        if (server == null) {
            toast("还没有可用节点，先点「重新定位」")
            return
        }

        val token = SpeedTestEngine.Session()
        session = token
        running = true
        hasResult = false
        lastDownloadBps = 0.0
        lastUploadBps = null
        lastPushAt = 0L

        binding.btnStart.text = "停止测速"
        binding.tvSpeedValue.text = unit.valueText(0.0)
        binding.tvPhase.text = "正在测延迟…"
        binding.progressSpeed.progress = 0
        binding.speedGraph.reset()
        binding.tvDownload.text = "—"
        binding.tvUpload.text = "—"
        binding.tvPing.text = "—"
        binding.tvJitter.text = "—"

        Thread({
            val report = runCatching {
                SpeedTestEngine.runFullTest(
                    session = token,
                    server = server,
                    connections = connections,
                    downloadMs = DOWNLOAD_MS,
                    uploadMs = UPLOAD_MS,
                    onPhase = { phase -> pushToUi { onPhaseChanged(phase) } },
                    onLatency = { latency -> pushToUi { onLatencyChanged(latency) } },
                    onDownload = { bps, fraction ->
                        pushToUi(THROTTLE_MS) { onSpeedSample(SpeedTestEngine.Phase.DOWNLOAD, bps, fraction) }
                    },
                    onUpload = { bps, fraction ->
                        pushToUi(THROTTLE_MS) { onSpeedSample(SpeedTestEngine.Phase.UPLOAD, bps, fraction) }
                    },
                    uploadServer = uploadNode
                )
            }.getOrNull()

            pushToUi {
                running = false
                binding.btnStart.text = "开始测速"
                when {
                    token.isCancelled -> {
                        binding.tvPhase.text = "已停止测速"
                        binding.progressSpeed.progress = 0
                    }

                    report == null -> {
                        binding.tvPhase.text = "测速失败：连不上这台节点，换个网络或换个节点再试"
                        binding.progressSpeed.progress = 0
                    }

                    else -> finishReport(report)
                }
            }
        }, "xyzinfo-speed-test").start()
    }

    private fun onPhaseChanged(phase: SpeedTestEngine.Phase) {
        binding.tvPhase.text = when (phase) {
            SpeedTestEngine.Phase.LATENCY -> "正在测延迟…"
            SpeedTestEngine.Phase.DOWNLOAD -> "正在测下载…"
            SpeedTestEngine.Phase.UPLOAD -> "正在测上传…"
            SpeedTestEngine.Phase.FINISHED -> "测速完成"
            SpeedTestEngine.Phase.IDLE -> ""
        }
        // 延迟阶段固定占前 10% 进度，下载 / 上传各占 45%
        binding.progressSpeed.progress = when (phase) {
            SpeedTestEngine.Phase.LATENCY -> 50
            SpeedTestEngine.Phase.DOWNLOAD -> 100
            SpeedTestEngine.Phase.UPLOAD -> 550
            SpeedTestEngine.Phase.FINISHED -> 1_000
            SpeedTestEngine.Phase.IDLE -> 0
        }
    }

    private fun onLatencyChanged(latency: SpeedTestEngine.Latency?) {
        binding.tvPing.text = latency?.let { formatMillis(it.averageMs) } ?: "—"
        binding.tvJitter.text = latency?.let { formatMillis(it.jitterMs) } ?: "—"
    }

    private fun onSpeedSample(
        phase: SpeedTestEngine.Phase,
        bytesPerSecond: Double,
        fraction: Double
    ) {
        binding.tvSpeedValue.text = unit.valueText(bytesPerSecond)
        // 曲线只画下载 / 上传阶段的速度；延迟阶段没有带宽数据，不掺进去
        binding.speedGraph.addSample(bytesPerSecond)
        val progress = when (phase) {
            SpeedTestEngine.Phase.DOWNLOAD -> 100 + fraction * 450
            SpeedTestEngine.Phase.UPLOAD -> 550 + fraction * 450
            else -> 1_000.0
        }
        binding.progressSpeed.progress = progress.roundToInt().coerceIn(0, 1_000)
        if (phase == SpeedTestEngine.Phase.DOWNLOAD) {
            lastDownloadBps = bytesPerSecond
            binding.tvDownload.text = unit.text(bytesPerSecond)
        } else {
            lastUploadBps = bytesPerSecond
            binding.tvUpload.text = unit.text(bytesPerSecond)
        }
    }

    private fun finishReport(report: SpeedTestEngine.Report) {
        hasResult = true
        lastDownloadBps = report.downloadBytesPerSecond
        lastUploadBps = report.uploadBytesPerSecond
        binding.progressSpeed.progress = 1_000
        binding.tvSpeedValue.text = unit.valueText(report.downloadBytesPerSecond)
        binding.tvDownload.text = unit.text(report.downloadBytesPerSecond)
        binding.tvUpload.text = report.uploadBytesPerSecond?.let { unit.text(it) } ?: "—"
        binding.tvPing.text = report.latency?.let { formatMillis(it.averageMs) } ?: "—"
        binding.tvJitter.text = report.latency?.let { formatMillis(it.jitterMs) } ?: "—"

        // 顺手把这次实测出来的延迟记进节点列表，省得下次还要重新探一遍
        report.latency?.let { pingCache[report.serverId] = it.averageMs }
        renderSelectedNode()

        val summary = StringBuilder("测速完成")
        summary.append(" ｜ ${report.serverName} ｜ ${report.connections} 连接")
        summary.append(" ｜ 用时 ${(report.durationMs / 1000).coerceAtLeast(1)} 秒")
        report.uploadSkippedReason?.let { summary.append("\n上传：$it") }
        report.uploadServerName?.let {
            summary.append("\n上行节点：$it（下载节点不支持上传，自动切换）")
        }
        binding.tvPhase.text = summary

        SettingsRepository.saveSpeedResult(
            context = this,
            downloadBytesPerSecond = report.downloadBytesPerSecond,
            uploadBytesPerSecond = report.uploadBytesPerSecond,
            pingMs = report.latency?.averageMs,
            jitterMs = report.latency?.jitterMs,
            serverName = report.serverName
        )
        SettingsRepository.addSpeedRecord(
            this,
            SettingsRepository.SpeedRecord(
                timestamp = System.currentTimeMillis(),
                serverName = report.serverName,
                downloadBytesPerSecond = report.downloadBytesPerSecond,
                uploadBytesPerSecond = report.uploadBytesPerSecond,
                pingMs = report.latency?.averageMs,
                jitterMs = report.latency?.jitterMs,
                connections = report.connections
            )
        )
        renderHistory()
    }

    /** 测速记录：时间 + 节点 + 四项成绩，按单位实时换算。 */
    private fun renderHistory() {
        val records = SettingsRepository.speedRecords(this)
        binding.layoutSpeedHistory.removeAllViews()
        binding.tvSpeedHistoryEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        binding.btnClearHistory.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE

        records.forEach { record ->
            val moment = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(record.timestamp))
            val head = "$moment ｜ ${record.serverName} ｜ ${record.connections} 连接"
            val detail = buildString {
                append("下载 ").append(unit.text(record.downloadBytesPerSecond))
                record.uploadBytesPerSecond?.let { append(" ｜ 上传 ").append(unit.text(it)) }
                record.pingMs?.let { append(" ｜ 延迟 ").append(formatMillis(it)) }
                record.jitterMs?.let { append("（抖动 ").append(formatMillis(it)).append("）") }
            }
            val row = TextView(this).apply {
                text = "$head\n$detail"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setLineSpacing(dp(3f).toFloat(), 1f)
                background = ContextCompat.getDrawable(this@NetworkSpeedActivity, R.drawable.bg_row_panel)
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
            binding.layoutSpeedHistory.addView(row)
        }
    }

    /** 进页面时先把上次的成绩摆出来，避免一片空白。 */
    private fun showLastResult() {
        val saved = SettingsRepository.lastSpeedResult(this) ?: return
        hasResult = true
        lastDownloadBps = saved.downloadBytesPerSecond
        lastUploadBps = saved.uploadBytesPerSecond
        binding.tvSpeedValue.text = unit.valueText(saved.downloadBytesPerSecond)
        binding.tvPing.text = saved.pingMs?.let { formatMillis(it) } ?: "—"
        binding.tvJitter.text = saved.jitterMs?.let { formatMillis(it) } ?: "—"
        val moment = if (saved.timestamp > 0) {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(saved.timestamp))
        } else {
            "上次"
        }
        binding.tvPhase.text = buildString {
            append("$moment ｜ ${saved.serverName}")
            append("：下载 ${unit.text(saved.downloadBytesPerSecond)}")
            saved.uploadBytesPerSecond?.let { append("，上传 ${unit.text(it)}") }
        }
    }

    // ---------- 多节点并行对比 ----------

    private fun startCompare() {
        if (comparing) {
            toast("对比还在跑，稍等一下")
            return
        }
        if (running) {
            toast("单节点测速进行中，先停下来")
            return
        }
        val targets = nearby.take(COMPARE_COUNT)
        if (targets.isEmpty()) {
            toast("还没找到可用节点，先点「重新定位」")
            return
        }
        comparing = true
        binding.layoutCompareResult.removeAllViews()
        binding.btnCompare.text = "正在对比…"
        binding.tvCompareHint.text = "正在同时测 ${targets.size} 台节点（各 2 条连接、${COMPARE_MS / 1000} 秒）…"

        Thread({
            val results = ConcurrentHashMap<String, Double>()
            val threads = targets.map { server ->
                Thread({
                    // 每台各开一个独立的会话：对比中途不需要取消能力，
                    // 但也必须是各自的连接，才能同时跑、互不干扰
                    val bps = runCatching {
                        SpeedTestEngine.download(
                            session = SpeedTestEngine.Session(),
                            server = server,
                            connections = COMPARE_CONNECTIONS,
                            durationMs = COMPARE_MS,
                            warmUpMs = 1_200
                        )
                    }.getOrDefault(0.0)
                    results[server.id] = bps
                }, "xyzinfo-speed-compare-${server.id}")
            }
            threads.forEach { it.start() }
            threads.forEach { runCatching { it.join(COMPARE_MS + 6_000) } }

            pushToUi {
                comparing = false
                binding.btnCompare.text = "同时对比最近 3 台节点"
                binding.tvCompareHint.text = buildCompareHint()
                lastCompare = targets to results
                renderCompare()
            }
        }, "xyzinfo-speed-compare").start()
    }

    private fun buildCompareHint(): String =
        "同时跑最近的几台节点（各自 2 条连接、${COMPARE_MS / 1000} 秒），看看哪台对当前网络最快"

    private fun renderCompare() {
        binding.layoutCompareResult.removeAllViews()
        val (targets, results) = lastCompare ?: return
        targets.sortedByDescending { results[it.id] ?: 0.0 }.forEach { server ->
            val bps = results[server.id] ?: 0.0
            val row = TextView(this).apply {
                text = "${server.name}：${unit.text(bps)}"
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                background = ContextCompat.getDrawable(this@NetworkSpeedActivity, R.drawable.bg_row_panel)
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            }
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
            binding.layoutCompareResult.addView(row)
        }
    }

    // ---------- 选项 chip ----------

    private fun buildUnitChips() {
        binding.layoutUnitChips.removeAllViews()
        SpeedUnit.entries.forEach { item ->
            binding.layoutUnitChips.addView(
                createChip(item.label, item == unit) {
                    if (unit != item) {
                        unit = item
                        SettingsRepository.setSpeedUnit(this, item.name)
                        buildUnitChips()
                        applyUnitToReadout()
                    }
                }
            )
        }
    }

    private fun buildParallelChips() {
        binding.layoutParallelChips.removeAllViews()
        PARALLEL_OPTIONS.forEach { count ->
            binding.layoutParallelChips.addView(
                createChip(if (count == 1) "单线程" else "$count 连接", count == connections) {
                    if (connections != count) {
                        connections = count
                        SettingsRepository.setSpeedConnections(this, count)
                        buildParallelChips()
                    }
                }
            )
        }
    }

    /** 定位测速开关（默认开启）。关掉后重新挑一次节点，并且不再发定位请求。 */
    private fun buildLocationChips() {
        binding.layoutLocationChips.removeAllViews()
        LOCATION_OPTIONS.forEach { (label, enabled) ->
            binding.layoutLocationChips.addView(
                createChip(label, enabled == useLocation) {
                    if (useLocation != enabled) {
                        useLocation = enabled
                        SettingsRepository.setSpeedUseLocation(this, enabled)
                        buildLocationChips()
                        if (running) {
                            toast("测速进行中，改动会在下一次测速生效")
                        } else {
                            startDiscovery()
                        }
                    }
                }
            )
        }
    }

    /** 切换单位：所有已经算出来的数字都要按新单位重画一遍（不能只改标题）。 */
    private fun applyUnitToReadout() {
        binding.tvSpeedUnit.text = unit.suffix
        binding.speedGraph.unit = unit
        if (hasResult) {
            binding.tvSpeedValue.text = unit.valueText(lastDownloadBps)
            binding.tvDownload.text = unit.text(lastDownloadBps)
            binding.tvUpload.text = lastUploadBps?.let { unit.text(it) } ?: "—"
        }
        renderCompare()
    }

    private fun createChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        val chip = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            /*
             * 一行行的 chip 必须**保证不换行**：并行连接数那排有 4 个（单线程 / 4 / 8 / 16 连接），
             * 一旦最后一个被挤成两行，整排会突然高一截，看起来像布局坏了。
             * 所以这里既把横向内边距收窄留出余量，也显式限制单行。
             */
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(11f), dp(7f), dp(11f), dp(7f))
            setTextColor(
                if (selected) {
                    ThemeColors.accent(this@NetworkSpeedActivity)
                } else {
                    ContextCompat.getColor(this@NetworkSpeedActivity, R.color.text_secondary)
                }
            )
            background = ContextCompat.getDrawable(this@NetworkSpeedActivity, R.drawable.bg_chip_filter)
            isSelected = selected
            isClickable = true
            setOnClickListener {
                Anim.pressFeedback(this)
                onClick()
            }
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8f) }
        return chip
    }

    // ---------- 小工具 ----------

    private fun pushToUi(throttleMs: Long = 0L, block: () -> Unit) {
        if (throttleMs > 0) {
            val now = System.currentTimeMillis()
            if (now - lastPushAt < throttleMs) return
            lastPushAt = now
        }
        handler.post { if (!isFinishing && !isDestroyed) block() }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 候选节点数量：地理上最近的 8 台都列出来，供手动切换。 */
        const val NEARBY_COUNT = 8

        /** 只对最近的 4 台做延迟探测（并行跑，约 3 秒出结果）。 */
        const val PROBE_COUNT = 4

        const val DEFAULT_CONNECTIONS = 4
        val PARALLEL_OPTIONS = listOf(1, 4, 8, 16)

        /** 定位测速开关（默认开启）。关掉就完全不出网定位。 */
        val LOCATION_OPTIONS = listOf("定位测速：开" to true, "定位测速：关" to false)

        /** 下载 / 上传各跑 10 秒：再短容易被慢启动带偏，再长用户等得不耐烦。 */
        const val DOWNLOAD_MS = 10_000L
        const val UPLOAD_MS = 10_000L

        const val COMPARE_COUNT = 3
        const val COMPARE_CONNECTIONS = 2
        const val COMPARE_MS = 6_000L

        /**
         * 界面刷新节流：内核 100ms 采一次，这里 60ms 放一次 ——
         * 等于每个采样点都会推到界面（每秒 10 次），大数字和曲线才是连贯的。
         */
        const val THROTTLE_MS = 60L
    }
}
