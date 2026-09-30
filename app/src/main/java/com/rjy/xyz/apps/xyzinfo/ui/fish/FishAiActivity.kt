package com.rjy.xyz.apps.xyzinfo.ui.fish

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.data.DeviceInspector
import com.rjy.xyz.apps.xyzinfo.data.SettingsRepository
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityFishAiBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import java.net.HttpURLConnection
import java.net.URL

/**
 * 大肥鱼：把本机验机报告交给 DeepSeek 解读。
 *
 * 两种模式（设置里选）：
 * - `api`：用你自己填的 DeepSeek API Key 直接调用官方接口，结果直接显示在下面；
 * - `web`：跳官方免费版网页（需要登录）——适合没有 Key 的情况。
 *
 * 隐私：只有点「让大肥鱼解读」时才会把**验机报告摘要**发出去，本地验机页不联网。
 */
class FishAiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFishAiBinding
    private var reportContext: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFishAiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_FISH)

        binding.btnAskDeepSeek.setOnClickListener {
            Anim.pressFeedback(it)
            ask()
        }
        binding.btnOpenDeepSeekWeb.setOnClickListener {
            Anim.pressFeedback(it)
            openWeb()
        }
        loadReport()
    }

    private fun loadReport() {
        binding.tvFishStatus.setInfoRow("正在整理验机报告…")
        Thread({
            val report = runCatching { DeviceInspector.inspect(this) }.getOrNull()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (report == null) {
                    binding.tvFishStatus.setInfoRow("验机报告生成失败")
                    return@runOnUiThread
                }
                reportContext = buildString {
                    append("设备验机报告（本地规则引擎）：\n")
                    append("评分：${report.score}/100\n")
                    append("结论：${report.verdict}\n")
                    report.summaries.take(8).forEach { append("· $it\n") }
                    append("逐项检查：\n")
                    report.findings.forEach { append("- [${it.level}] ${it.title}：${it.detail}\n") }
                }
                val mode = if (SettingsRepository.aiMode(this) == "api") "API Key 直连" else "官方免费版网页"
                val keyState = if (SettingsRepository.deepSeekApiKey(this).isBlank()) "未填写" else "已填写"
                binding.tvFishStatus.setInfoRow(
                    "评分：${report.score}/100 ｜ 风险 ${report.riskCount} 项 ｜ 注意 ${report.noticeCount} 项"
                        + "\n当前模式：$mode（API Key：$keyState）"
                )
            }
        }, "xyzinfo-fish-report").start()
    }

    private fun ask() {
        val mode = SettingsRepository.aiMode(this)
        val key = SettingsRepository.deepSeekApiKey(this)
        if (mode != "api" || key.isBlank()) {
            binding.tvFishAnswer.text =
                "还没有可用的 DeepSeek API Key。\n\n" +
                    "两种用法：\n" +
                    "1) 到「设置 → 大肥鱼助手」里填入 API Key，就能在这里直接得到解读；\n" +
                    "2) 直接用下面的「打开 DeepSeek 官方免费版」，登录后把报告粘过去问。"
            return
        }
        val question = binding.etFishQuestion.text?.toString().orEmpty().ifBlank {
            "请用中文点评这份验机报告：这台设备有没有可疑之处？如果有，按严重程度排序，并给出普通用户能执行的核查步骤。"
        }
        binding.tvFishAnswer.text = "大肥鱼正在思考…"
        Thread({
            val answer = runCatching { callDeepSeek(key, reportContext, question) }
                .getOrElse { "调用失败：${it.message}" }
            runOnUiThread {
                if (!isFinishing) binding.tvFishAnswer.text = answer
            }
        }, "xyzinfo-fish-ask").start()
    }

    /** 调用 DeepSeek 官方 chat/completions 接口（OpenAI 兼容格式）。 */
    private fun callDeepSeek(key: String, context: String, question: String): String {
        val payload = org.json.JSONObject().apply {
            put("model", "deepseek-chat")
            put("stream", false)
            put(
                "messages",
                org.json.JSONArray()
                    .put(
                        org.json.JSONObject().put("role", "system")
                            .put("content", "你是一条叫大肥鱼的中文数码助手，负责帮用户解读设备验机报告；语气亲切但结论严谨，不夸张、不吓人。")
                    )
                    .put(
                        org.json.JSONObject().put("role", "user")
                            .put("content", context + "\n\n用户的问题：" + question)
                    )
            )
        }
        val connection = (URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $key")
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) return "DeepSeek 返回 $code：$body"
        return runCatching {
            org.json.JSONObject(body)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }.getOrElse { "返回格式异常：$body" }
    }

    private fun openWeb() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chat.deepseek.com/")))
        }
    }
}
