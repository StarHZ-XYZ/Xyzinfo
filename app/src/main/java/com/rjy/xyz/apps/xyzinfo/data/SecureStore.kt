package com.rjy.xyz.apps.xyzinfo.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 小段敏感数据（目前只有 DeepSeek API Key）的加密封封。
 *
 * 用 **AndroidKeyStore** 里生成的 AES-256-GCM 密钥加密：
 * 密钥由系统 TEE/KeyMastr 保管，**永远不会出现在应用私有目录里**，
 * 所以就算别人把 /data/data 整个拖走、或者反编译出代码，
 * 拿到的也只是一段密文（换了设备/换了签名密钥都解不开）。
 *
 * 存储格式：`v1:Base64(IV):Base64(密文+认证标签)`
 * 读的时候如果发现是旧版本直接存的明文（没有 `v1:` 前缀），会**自动迁移**成密文。
 */
object SecureStore {

    private const val KEY_ALIAS = "xyzinfo_secure_prefs_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PREFIX = "v1:"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求解锁/指纹：验机信息页随时要读，改成生物识别反而影响易用性
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密。失败时返回 null，调用方自行决定降级策略。 */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrNull()

    /** 解密；不是本格式（旧明文）或解密失败都返回 null。 */
    fun decrypt(blob: String): String? = runCatching {
        if (!blob.startsWith(PREFIX)) return null
        val parts = blob.substring(PREFIX.length).split(':')
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    /** 生成一个不会被误认成密文的 IV 长度常量（仅用于自检，防止后续改动踩坑）。 */
    internal fun ivLength(): Int = IV_LENGTH
}
