package com.rjy.xyz.apps.xyzinfo.model

/**
 * 系统 UI / ROM 识别结果。
 *
 * [source] 记录判定依据（读了哪个属性），方便用户核对与反馈适配。
 */
data class RomInfo(
    val name: String,
    val version: String?,
    val source: String?,
    val displayId: String?,
    val buildType: String?
)
