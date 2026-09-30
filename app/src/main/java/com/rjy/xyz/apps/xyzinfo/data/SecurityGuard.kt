package com.rjy.xyz.apps.xyzinfo.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import com.rjy.xyz.apps.xyzinfo.BuildConfig
import java.security.MessageDigest

/**
 * 逆向 / 二次打包防护。
 *
 * 这一版做了三件事（都在 release 包生效，debug 包一律放行，免得挡住自己调试）：
 *
 * 1. **调试器检测**：挂上 jdwp 调试器就直接退出，避免有人用断点把关键逻辑一条条读出来；
 * 2. **签名校验**：比对当前安装包的签名证书 SHA-256，只有用我们自己的密钥签的包才允许运行。
 *    别人反编译 → 改代码 → 重新签名，签名一变更应用就起不来；
 * 3. **反调试端口 / 越狱环境的最基本痕迹**：tracerpid 非 0 说明进程被 ptrace 挂着，同样退出。
 *
 * 另外配合构建侧的加固：
 * - release 打开 R8 混淆 + 资源压缩（类名/方法名/字段名全部重命名，`-repackageclasses` 收敛到一个包）；
 * - 去掉行号与源文件名，堆栈不再泄露原始结构；
 * - `debuggable=false`、`allowBackup=false`、禁止明文流量；
 * - 敏感数据（API Key）走 AndroidKeyStore 加密，不进明文。
 *
 * 说明：任何客户端加固都不是"绝对防破解"，它的价值在于把成本抬到不值得：
 * 想真正防住端上逻辑，正解是把关键能力放到服务端。
 */
object SecurityGuard {

    private const val TAG = "SecurityGuard"

    /**
     * 允许的签名证书 SHA-256（大写十六进制，无分隔符）。
     *
     * 第一条是当前发布/调试共用的签名证书指纹（本项目发布包一直用同一把密钥签，
     * 所以这里只放一条；换了新密钥要同步更新这里，否则新包会拒绝启动）。
     */
    private val ALLOWED_CERT_SHA256 = setOf(
        "92E6419B5BC79C679FB107F5B201A03879696658230BA3D475F7C40F137D8CC1"
    )

    /** 在 Application.onCreate 里调用。 */
    fun install(app: Application) {
        // 开发包直接放行：自己调试时不被挡住
        if (BuildConfig.DEBUG) return
        if (isBeingDebugged()) {
            Log.w(TAG, "检测到调试器，结束进程")
            bail()
        }
        if (!isSignatureTrusted(app)) {
            Log.w(TAG, "签名校验不通过，结束进程")
            bail()
        }
    }

    /** jdwp 调试器 / ptrace 挂载检测。 */
    private fun isBeingDebugged(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        // TracerPid != 0 表示当前进程正在被 ptrace（lldb/gdb/frida 之类的常见做法）
        return runCatching {
            java.io.File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { it != 0 } == true
            }
        }.getOrDefault(false)
    }

    /** 比对安装包签名证书指纹。 */
    private fun isSignatureTrusted(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        if (signatures.isNullOrEmpty()) return@runCatching false
        val digest = MessageDigest.getInstance("SHA-256")
        signatures.any { signature ->
            val hash = digest.digest(signature.toByteArray())
                .joinToString("") { byte -> "%02X".format(byte) }
            ALLOWED_CERT_SHA256.contains(hash)
        }
    }.getOrDefault(false)

    private fun bail(): Nothing {
        // 不清空数据、不弹窗——就是安静地退出，避免给逆向者留下可利用的错误信息
        Process.killProcess(Process.myPid())
        kotlin.system.exitProcess(0)
    }
}
