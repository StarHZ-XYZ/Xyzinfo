package com.rjy.xyz.apps.xyzinfo.ui.fish

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.rjy.xyz.apps.xyzinfo.R
import com.rjy.xyz.apps.xyzinfo.data.DeviceInspector
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityDeepseekWebBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding

/**
 * 大肥鱼 · DeepSeek 免费版（软件内登录）。
 *
 * 用系统自带的 **WebView** 打开 DeepSeek 官方网页版，并把登录态（Cookie）持久化在
 * 本应用里，所以：
 * 1. 不用跳到外部浏览器，登录一次之后回到这里就是登录状态；
 * 2. 免费版（网页版）可以直接在软件里用；
 * 3. 验机报告可以一键复制，进页面长按输入框粘贴即可提问。
 *
 * 说明：这里用的是官方网页，本应用不存储用户的账号密码，也不做任何中间人抓取。
 */
class DeepSeekWebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepseekWebBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepseekWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(binding.webDeepSeek, true)

        with(binding.webDeepSeek.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            // 有些网页版会按 UA 判断环境，这里明确告诉它"这是一个手机浏览器"
            userAgentString = userAgentString + " XyzInfoApp/1.0"
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = false
        }

        binding.webDeepSeek.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressDeepSeek.progress = newProgress
                binding.progressDeepSeek.visibility =
                    if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
        binding.webDeepSeek.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                updateHint()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateHint()
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false // 一律留在软件内的 WebView 里，登录跳转才不会中断
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webDeepSeek.canGoBack()) {
                    binding.webDeepSeek.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val autoPaste = intent.getBooleanExtra(EXTRA_COPY_REPORT, false)
        if (autoPaste) copyReport()
        binding.tvWebHint.setOnClickListener {
            copyReport()
            Toast.makeText(this, "验机报告已复制，长按输入框粘贴即可", Toast.LENGTH_SHORT).show()
        }
        binding.btnWebExternal.setOnClickListener {
            // 网页版偶尔会做环境校验，留一个"用系统浏览器打开"的出口
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(URL_DEEPSEEK)
                    )
                )
            }
        }
        binding.btnWebRelogin.setOnClickListener {
            // 退出登录：清掉本应用 WebView 里保存的登录态
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                binding.webDeepSeek.clearCache(true)
                binding.webDeepSeek.clearHistory()
            }
            binding.webDeepSeek.loadUrl(URL_DEEPSEEK)
            Toast.makeText(this, "已清除登录状态", Toast.LENGTH_SHORT).show()
        }
        binding.webDeepSeek.loadUrl(URL_DEEPSEEK)
    }

    private fun copyReport() {
        val text = runCatching {
            val report = DeviceInspector.inspect(this)
            buildString {
                append("请帮我看看这台设备（验机报告）：\n")
                append("评分：${report.score}/100\n")
                report.summaries.take(8).forEach { append("· $it\n") }
            }
        }.getOrElse { "请帮我分析这台设备的验机报告。" }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("验机报告", text))
    }

    /** 提示栏：顺便把「是否已经登录过」告诉用户。 */
    private fun updateHint() {
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(URL_DEEPSEEK).orEmpty()
        }.getOrDefault("")
        val loggedIn = cookie.contains("token", ignoreCase = true) ||
            cookie.contains("userToken", ignoreCase = true)
        binding.tvWebHint.text = if (loggedIn) {
            "已登录（登录态保存在本应用内）· 点这条提示可复制验机报告，长按输入框粘贴即可提问 · 返回键可回上一页"
        } else {
            "首次使用请在下面完成登录（登录态会保存在本应用内）· 点这条提示可复制验机报告 · 返回键可回上一页"
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    companion object {
        const val URL_DEEPSEEK = "https://chat.deepseek.com/"
        /** 打开时是否顺手把验机报告复制到剪贴板。 */
        const val EXTRA_COPY_REPORT = "copy_report"
    }
}
