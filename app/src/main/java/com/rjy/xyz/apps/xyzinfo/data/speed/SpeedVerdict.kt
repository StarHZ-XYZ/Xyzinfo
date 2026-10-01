package com.rjy.xyz.apps.xyzinfo.data.speed

import kotlin.math.roundToInt

/**
 * 测速之后的「网络评价」。
 *
 * 设计原则：**下行和上行分开评**。家里的宽带经常是「下行 500M、上行 30M」这种不对称配置，
 * 用一个总分盖住会看不出问题 —— 下载 4K 毫无压力、上传视频却卡成幻灯片，正是上行不行。
 * 所以这里给三块：
 *
 * 1. [LineVerdict] × 2：下行、上行各自的分数 / 等级 / 一句人话 / 能干什么 / 干什么吃力；
 * 2. 总评：四项加权（下载 40%、上传 25%、延迟 20%、抖动 15%）算出的分数与等级；
 * 3. 延迟与抖动单独一句：游戏和视频会议卡不卡，看的其实是这两个，而不是带宽。
 *
 * 阈值都取公开的实际要求，不是拍脑袋：4K 流媒体 ≈ 25Mbps、1080p ≈ 8Mbps、
 * 视频通话 ≥ 2Mbps、1080p 直播推流 ≥ 10Mbps、云游戏 ≥ 35Mbps 且延迟要低。
 */
object SpeedVerdict {

    data class LineVerdict(
        /** 「下行」或「上行」。 */
        val direction: String,
        val speedMbps: Double,
        val score: Int,
        val grade: String,
        /** 一句人话，例如「看 4K、下游戏都够快」。 */
        val summary: String,
        /** 这个方向**现在能干**的事。 */
        val capabilities: List<String>,
        /** 这个方向**会吃力**的事。 */
        val limits: List<String>
    )

    data class Verdict(
        val score: Int,
        val grade: String,
        val headline: String,
        val download: LineVerdict,
        val upload: LineVerdict?,
        /** 延迟 / 抖动的一句话（游戏、会议体感）。 */
        val latencyNote: String,
        /** 下行大致相当于什么宽带档位，例如「相当于 200M 宽带」。 */
        val tierNote: String?,
        /** 上行缺失时的说明（节点不支持上传之类）。 */
        val uploadUnavailableReason: String?
    )

    private const val WEIGHT_DOWNLOAD = 0.40
    private const val WEIGHT_UPLOAD = 0.25
    private const val WEIGHT_PING = 0.20
    private const val WEIGHT_JITTER = 0.15

    /** 把字节/秒换算成 Mbps。 */
    private fun mbps(bytesPerSecond: Double): Double = bytesPerSecond * 8 / 1e6

    fun evaluate(
        downloadBytesPerSecond: Double,
        uploadBytesPerSecond: Double?,
        pingMs: Double?,
        jitterMs: Double?,
        uploadUnavailableReason: String? = null,
        /**
         * 评价文案里的速度单位。
         *
         * 设置里切到 MB/s 之后，评价里就不能再写 Mbps —— 同一屏两套单位最容易让人以为测错了。
         */
        unit: SpeedUnit = SpeedUnit.MBPS
    ): Verdict {
        val download = evaluateDownload(mbps(downloadBytesPerSecond))
        val upload = uploadBytesPerSecond?.let { evaluateUpload(mbps(it)) }

        val pingScore = if (pingMs == null) 60.0 else scoreOf(pingMs, listOf(180.0, 90.0, 40.0, 15.0), false)
        val jitterScore = if (jitterMs == null) 70.0
        else scoreOf(jitterMs, listOf(50.0, 25.0, 10.0, 4.0), false)

        val score = (
            download.score * WEIGHT_DOWNLOAD +
                (upload?.score ?: 60) * WEIGHT_UPLOAD +
                pingScore * WEIGHT_PING +
                jitterScore * WEIGHT_JITTER
            ).roundToInt().coerceIn(0, 100)

        return Verdict(
            score = score,
            grade = gradeOf(score),
            headline = headlineOf(score, download, upload, unit),
            download = download,
            upload = upload,
            latencyNote = latencyNote(pingMs, jitterMs),
            tierNote = tierOf(download.speedMbps),
            uploadUnavailableReason = if (upload == null) {
                uploadUnavailableReason ?: "本次没测到上行（节点不支持上传）"
            } else {
                null
            }
        )
    }

    // ---------- 下行 ----------

    private fun evaluateDownload(speed: Double): LineVerdict {
        val score = scoreOf(speed, listOf(3.0, 8.0, 25.0, 100.0)).roundToInt()
        val capabilities = ArrayList<String>(4)
        if (speed >= 25) capabilities += "4K 视频"
        if (speed >= 8) capabilities += "1080p 视频"
        if (speed >= 50) capabilities += "全家多设备同时用"
        if (speed >= 100) capabilities += "秒下大文件 / 游戏"
        if (speed >= 35) capabilities += "云游戏串流"
        val limits = ArrayList<String>(3)
        if (speed < 25) limits += "4K 视频（可能降清晰度）"
        if (speed < 8) limits += "高清视频"
        if (speed < 50) limits += "多人同时看视频"
        if (speed < 3) limits += "基本只能刷刷文字"

        val summary = when {
            speed >= 200 -> "下载极快，千兆级体验，随便造"
            speed >= 100 -> "下载很快，等于跑满百兆以上宽带"
            speed >= 50 -> "下载够快，高清视频和游戏更新都不等"
            speed >= 25 -> "下载够看 4K，大文件稍慢一点"
            speed >= 8 -> "下载够日常用，1080p 没问题"
            speed >= 3 -> "下载偏慢，标清还行、高清会转圈"
            else -> "下载很慢，视频要不停缓冲"
        }
        return LineVerdict("下行", speed, score, gradeOf(score), summary, capabilities, limits)
    }

    // ---------- 上行 ----------

    private fun evaluateUpload(speed: Double): LineVerdict {
        val score = scoreOf(speed, listOf(0.5, 2.0, 10.0, 50.0)).roundToInt()
        val capabilities = ArrayList<String>(4)
        if (speed >= 2) capabilities += "视频通话"
        if (speed >= 5) capabilities += "发照片 / 小文件"
        if (speed >= 10) capabilities += "1080p 直播推流"
        if (speed >= 20) capabilities += "云盘备份 / 远程桌面"
        if (speed >= 50) capabilities += "4K 推流 / 大文件上云"
        val limits = ArrayList<String>(3)
        if (speed < 10) limits += "高清直播推流"
        if (speed < 5) limits += "上传大文件 / 云盘备份"
        if (speed < 2) limits += "视频通话（对方会看到你卡）"

        val summary = when {
            speed >= 100 -> "上行极快，直播、上传都是秒级"
            speed >= 50 -> "上行很快，4K 推流和大文件上云都压得住"
            speed >= 20 -> "上行够用，云盘备份、远程桌面都不卡"
            speed >= 10 -> "上行还行，1080p 直播推流没问题"
            speed >= 5 -> "上行一般，视频通话够，传大文件会慢"
            speed >= 2 -> "上行偏慢，只能勉强视频通话"
            else -> "上行很慢，视频通话和上传都会卡"
        }
        return LineVerdict("上行", speed, score, gradeOf(score), summary, capabilities, limits)
    }

    // ---------- 延迟 / 档位 / 总评 ----------

    private fun latencyNote(pingMs: Double?, jitterMs: Double?): String {
        if (pingMs == null) return "延迟没测到，游戏和视频会议的体感不好判断"
        val pingPart = when {
            pingMs <= 15 -> "延迟很低（${pingMs.roundToInt()} ms），本地服务器级别的响应"
            pingMs <= 40 -> "延迟不错（${pingMs.roundToInt()} ms），在线游戏基本感觉不到"
            pingMs <= 90 -> "延迟一般（${pingMs.roundToInt()} ms），竞技类游戏会有点吃亏"
            pingMs <= 180 -> "延迟偏高（${pingMs.roundToInt()} ms），游戏里会有明显滞后"
            else -> "延迟很高（${pingMs.roundToInt()} ms），只适合看视频这类不要求实时性的场景"
        }
        val jitterPart = when {
            jitterMs == null -> null
            jitterMs <= 5 -> "抖动很小，连线稳定"
            jitterMs <= 20 -> "抖动不大，偶尔一格波动"
            jitterMs <= 50 -> "抖动偏大，视频通话可能偶尔卡一下"
            else -> "抖动很大，网络时快时慢"
        }
        return if (jitterPart == null) pingPart else "$pingPart；$jitterPart"
    }

    private fun tierOf(downloadMbps: Double): String? = when {
        downloadMbps >= 900 -> "相当于 1000M（千兆）宽带"
        downloadMbps >= 450 -> "相当于 500M 宽带"
        downloadMbps >= 180 -> "相当于 200M 宽带"
        downloadMbps >= 90 -> "相当于 100M 宽带"
        downloadMbps >= 45 -> "相当于 50M 宽带"
        downloadMbps >= 18 -> "相当于 20M 宽带"
        else -> null
    }

    private fun gradeOf(score: Int): String = when {
        score >= 85 -> "优秀"
        score >= 70 -> "良好"
        score >= 50 -> "一般"
        else -> "较差"
    }

    private fun headlineOf(
        score: Int,
        download: LineVerdict,
        upload: LineVerdict?,
        unit: SpeedUnit
    ): String {
        /*
         * 上行下行等级不一致时，先把这件事点出来 —— 比"整体不错"有用得多：
         * 用户的真实困扰往往就是"看视频很流畅，一开直播 / 发大文件就崩"。
         */
        if (upload != null) {
            val downloadRank = rank(download.grade)
            val uploadRank = rank(upload.grade)
            if (downloadRank - uploadRank >= 1) {
                return "下行 ${speedText(download.speedMbps, unit)}（${download.grade}）明显快于" +
                    "上行 ${speedText(upload.speedMbps, unit)}（${upload.grade}）—— " +
                    "看视频、下东西没问题，上传 / 直播会明显吃力"
            }
            if (uploadRank - downloadRank >= 1) {
                return "上行 ${speedText(upload.speedMbps, unit)}（${upload.grade}）反而比" +
                    "下行 ${speedText(download.speedMbps, unit)}（${download.grade}）好 —— " +
                    "常见于限速策略或 Wi-Fi 干扰"
            }
        }
        return when {
            score >= 85 -> "这个网络很能打：下载、上传、延迟都在线"
            score >= 70 -> "日常使用很流畅，重度场景也够用"
            score >= 50 -> "刷视频、聊天没问题，重活会有点吃力"
            else -> "网络偏弱：卡顿、掉线大概率来自这条线路"
        }
    }

    private fun rank(grade: String): Int = when (grade) {
        "优秀" -> 3
        "良好" -> 2
        "一般" -> 1
        else -> 0
    }

    /**
     * 速度文案：**跟着设置里的单位走**。
     *
     * 内部统计一律用 Mbps，显示时换算成用户选的单位（MB/s = Mbps ÷ 8）。
     */
    private fun speedText(mbps: Double, unit: SpeedUnit): String =
        unit.text(mbps * 1_000_000.0 / 8.0)

    /**
     * 把实测值折算成 0~100 分：低于第一个分界点 = 0 分，超过最后一个 = 100 分，
     * 中间线性插值、每段 25 分。越低越好的指标（延迟、抖动）取负号即可共用。
     */
    private fun scoreOf(value: Double, steps: List<Double>, higherIsBetter: Boolean = true): Double {
        fun axis(v: Double): Double = if (higherIsBetter) v else -v
        val d = axis(value)
        val points = steps.map { axis(it) }
        if (d <= points[0]) return 0.0
        if (d >= points[points.size - 1]) return 100.0
        for (index in 0 until points.size - 1) {
            val low = points[index]
            val high = points[index + 1]
            if (d in low..high) {
                val fraction = if (high - low <= 1e-9) 1.0 else (d - low) / (high - low)
                return 25.0 * (index + 1) + fraction * 25.0
            }
        }
        return 100.0
    }
}
