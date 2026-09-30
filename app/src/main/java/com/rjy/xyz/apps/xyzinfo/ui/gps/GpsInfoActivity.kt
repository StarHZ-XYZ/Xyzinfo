package com.rjy.xyz.apps.xyzinfo.ui.gps

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rjy.xyz.apps.xyzinfo.databinding.ActivityGpsBinding
import com.rjy.xyz.apps.xyzinfo.ui.common.Anim
import com.rjy.xyz.apps.xyzinfo.ui.common.GlassScaffold
import com.rjy.xyz.apps.xyzinfo.ui.common.applySystemBarPadding
import com.rjy.xyz.apps.xyzinfo.ui.common.setInfoRow
import com.rjy.xyz.apps.xyzinfo.ui.common.setRawBlock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * GPS 定位页：实时经纬度 / 海拔 / 精度 / 速度 / 卫星状态 + 内置离线地图 + 收音机探测。
 */
class GpsInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpsBinding
    private lateinit var locationManager: LocationManager

    private var listening = false
    private var requestAtMillis = 0L
    private var ttffMillis: Long? = null
    private var lastLocation: Location? = null
    private var satellitesVisible = 0
    private var satellitesUsed = 0
    private var bestSnr = 0f
    private var fmApps: List<RadioProbe.FmApp> = emptyList()
    private var hasCenteredOnFix = false
    private var sensorManager: SensorManager? = null
    private var headingDegrees: Float? = null

    /** 指南针：用旋转矢量传感器算方位角，再做低通滤波防止抖动。 */
    private val headingListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
            // 低通滤波：新值占 20%，避免数字乱跳
            val previous = headingDegrees
            val smoothed = if (previous == null) {
                degrees
            } else {
                previous + shortestDelta(previous, degrees) * 0.2f
            }
            headingDegrees = (smoothed + 360f) % 360f
            binding.skyView.setHeading(headingDegrees)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** 处理 359° → 1° 这类跨 0 点的差值，避免指针绕一大圈。 */
    private fun shortestDelta(from: Float, to: Float): Float {
        var delta = to - from
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                startLocation()
            } else {
                binding.tvGpsStatus.setInfoRow("状态：定位权限被拒绝，去系统设置里允许「位置信息」后重试")
            }
        }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = applyLocation(location)

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            if (listening) {
                binding.tvGpsStatus.setInfoRow("状态：$provider 已被关闭")
            }
        }

        @Deprecated("兼容 API 29 以下")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var visible = 0
            var used = 0
            var snr = 0f
            val list = ArrayList<SatelliteSkyView.Satellite>(status.satelliteCount)
            for (index in 0 until status.satelliteCount) {
                val usedInFix = status.usedInFix(index)
                if (usedInFix) visible++
                if (usedInFix) used++
                // getCn0DbHz 是 API 24 起的公开字段（载噪比，单位 dB-Hz）
                val cn0 = status.getCn0DbHz(index)
                if (usedInFix) snr = maxOf(snr, cn0)
                list += SatelliteSkyView.Satellite(
                    id = status.getSvid(index),
                    constellation = status.getConstellationType(index),
                    azimuth = status.getAzimuthDegrees(index),
                    elevation = status.getElevationDegrees(index),
                    snr = cn0,
                    usedInFix = usedInFix
                )
            }
            satellitesVisible = status.satelliteCount
            satellitesUsed = used
            bestSnr = snr
            val snrText = String.format(Locale.US, "%.1f dB-Hz", bestSnr)
            binding.tvSatellites.setInfoRow(
                "卫星：可见 $satellitesVisible 颗 ｜ 参与定位 $satellitesUsed 颗 ｜ 最强信号 $snrText"
            )
            binding.skyView.update(list)
            binding.tvConstellations.setInfoRow("星座：${binding.skyView.constellationSummary()}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGpsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding()
        GlassScaffold.attach(this, binding.root, GlassScaffold.TAB_NONE)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        binding.btnStartGps.setOnClickListener {
            Anim.pressFeedback(it)
            ensurePermissionAndStart()
        }
        binding.btnCopyCoords.setOnClickListener {
            Anim.pressFeedback(it)
            copyCoordinates()
        }
        binding.btnZoomIn.setOnClickListener { binding.mapView.zoomIn() }
        binding.btnZoomOut.setOnClickListener { binding.mapView.zoomOut() }
        binding.btnMapReset.setOnClickListener { binding.mapView.resetView() }
        binding.tvMapNote.setInfoRow("地图：内置离线世界地图（等距圆柱投影）")
        binding.tvMapNote.append("\n经纬网 30° 一格，标记点带呼吸光晕与精度圈；缩放档位 4× / 8× / 16× / 32×")

        setupRadio()
        updateStatus("状态：点击「开始定位」获取当前位置")
    }

    override fun onStop() {
        super.onStop()
        stopLocation()
    }

    override fun onResume() {
        super.onResume()
        // 指南针：有旋转矢量传感器就接上，天顶图会跟着手机朝向转
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) {
            sensorManager?.registerListener(headingListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(headingListener)
    }

    // ---------- 定位 ----------

    private fun ensurePermissionAndStart() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocation() {
        if (listening) return
        listening = true
        hasCenteredOnFix = false
        requestAtMillis = System.currentTimeMillis()
        ttffMillis = null
        updateStatus("状态：正在搜星…")

        // 先用最后一次已知位置把界面填上，再等实时回调
        runCatching {
            lastKnownLocation()?.let { applyLocation(it) }
        }
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
        }
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 2000L, 0f, locationListener, Looper.getMainLooper()
            )
        }
        runCatching {
            locationManager.registerGnssStatusCallback(gnssCallback, android.os.Handler(Looper.getMainLooper()))
        }
    }

    private fun stopLocation() {
        if (!listening) return
        listening = false
        runCatching { locationManager.removeUpdates(locationListener) }
        runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun lastKnownLocation(): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun applyLocation(location: Location) {
        if (!isPlausible(location)) {
            // 没真正定位时，很多机型会返回 (0, 0)（几内亚湾）——直接丢掉，免得地图跑到非洲去
            updateStatus("状态：收到无效坐标（${location.latitude}, ${location.longitude}），继续搜星…")
            return
        }
        if (ttffMillis == null && location.provider == LocationManager.GPS_PROVIDER) {
            ttffMillis = System.currentTimeMillis() - requestAtMillis
        }
        lastLocation = location
        val hasAltitude = location.hasAltitude()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f

        binding.tvCoordinate.setInfoRow(
            String.format(
                Locale.US,
                "经纬度：%.6f, %.6f",
                location.latitude,
                location.longitude
            )
        )
        binding.tvCoordinateDms.setInfoRow(
            "度分秒：${toDms(location.latitude, true)} ${toDms(location.longitude, false)}"
        )
        binding.tvProvider.setInfoRow(
            "数据来源：${providerLabel(location.provider)} ｜ 精度 ±${accuracy.roundToInt()} 米"
        )
        updateStatus(
            if (location.provider == LocationManager.GPS_PROVIDER) "状态：GPS 已定位" else "状态：网络定位"
        )

        binding.tvGpsDetail.setRawBlock(
            buildString {
                appendLine("定位时间：${timeFormat.format(Date(location.time))}")
                appendLine("海拔：${if (hasAltitude) "${location.altitude.roundToInt()} 米" else "未提供"}")
                appendLine("水平精度：${accuracy.roundToInt()} 米")
                appendLine(
                    "速度：${
                        if (location.hasSpeed()) String.format(Locale.US, "%.2f 米/秒（%.1f km/h）", location.speed, location.speed * 3.6)
                        else "未提供"
                    }"
                )
                appendLine(
                    "方位：${
                        if (location.hasBearing()) String.format(Locale.US, "%.1f°", location.bearing) else "未提供"
                    }"
                )
                appendLine(
                    "垂直精度：${
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                            "${location.verticalAccuracyMeters.roundToInt()} 米"
                        } else {
                            "未提供"
                        }
                    }"
                )
                append("首次定位耗时：${ttffMillis?.let { "$it 毫秒" } ?: "计时中…"}")
            }
        )

        binding.mapView.updatePosition(
            location.latitude,
            location.longitude,
            accuracy,
            recenter = !hasCenteredOnFix
        )
        hasCenteredOnFix = true
    }

    /**
     * 有效性校验：(0,0) 空坐标、越界坐标、精度离谱的值都不采信。
     *
     * 这几条是定位页最容易翻车的地方——尤其 (0,0)，它在等距圆柱投影上正好落在
     * 几内亚湾（非洲西岸），所以「定位跑到非洲」基本都是它。
     */
    private fun isPlausible(location: Location): Boolean {
        if (location.latitude == 0.0 && location.longitude == 0.0) return false
        if (kotlin.math.abs(location.latitude) > 90.0 || kotlin.math.abs(location.longitude) > 180.0) return false
        if (location.hasAccuracy() && location.accuracy > 100_000f) return false
        return true
    }

    private fun updateStatus(text: String) {
        binding.tvGpsStatus.setInfoRow(text)
    }

    private fun providerLabel(provider: String?): String = when (provider) {
        LocationManager.GPS_PROVIDER -> "GPS 卫星"
        LocationManager.NETWORK_PROVIDER -> "网络 / 基站"
        LocationManager.PASSIVE_PROVIDER -> "被动（其它应用共享）"
        else -> provider ?: "未知"
    }

    /** 十进制度 → 度分秒，例如 39°54′26.1″N。 */
    private fun toDms(value: Double, isLatitude: Boolean): String {
        val hemisphere = when {
            isLatitude && value >= 0 -> "N"
            isLatitude -> "S"
            value >= 0 -> "E"
            else -> "W"
        }
        val absolute = kotlin.math.abs(value)
        val degrees = absolute.toInt()
        val minutesFull = (absolute - degrees) * 60
        val minutes = minutesFull.toInt()
        val seconds = (minutesFull - minutes) * 60
        return String.format(Locale.US, "%d°%02d′%04.1f″%s", degrees, minutes, seconds, hemisphere)
    }

    private fun copyCoordinates() {
        val location = lastLocation
        if (location == null) {
            Toast.makeText(this, "还没有定位结果", Toast.LENGTH_SHORT).show()
            return
        }
        val text = String.format(
            Locale.US,
            "%.6f,%.6f",
            location.latitude,
            location.longitude
        )
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("坐标", text))
        Toast.makeText(this, "已复制：$text", Toast.LENGTH_SHORT).show()
    }

    // ---------- 收音机 ----------

    private fun setupRadio() {
        val result = RadioProbe.probe(this)
        fmApps = result.apps
        val hardware = if (result.hardwareSupported) "系统声明支持" else "系统未声明支持"
        binding.tvRadioStatus.setInfoRow(
            "FM 硬件：$hardware\n" +
                if (result.apps.isEmpty()) {
                    "未发现厂商收音机应用"
                } else {
                    "发现 ${result.apps.size} 个收音机应用：" +
                        result.apps.joinToString("、") { "${it.label}（${it.packageName}）" }
                }
        )
        val first = result.apps.firstOrNull()
        if (first != null) {
            binding.btnOpenRadio.visibility = android.view.View.VISIBLE
            binding.btnOpenRadio.text = "打开「${first.label}」"
            binding.btnOpenRadio.setOnClickListener {
                Anim.pressFeedback(it)
                val intent = packageManager.getLaunchIntentForPackage(first.packageName)
                if (intent != null) {
                    runCatching { startActivity(intent) }
                        .onFailure { Toast.makeText(this, "打不开 ${first.label}", Toast.LENGTH_SHORT).show() }
                } else {
                    Toast.makeText(this, "该系统应用没有可启动的界面", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.tvRadioNote.append(
            "\n\n检测方式：查询系统特性 android.hardware.fmradio + 扫描常见厂商收音机包名。" +
                "小米 Civi 这类没有独立 FM 调谐器的机型，两者都会是空的。"
        )
    }
}
