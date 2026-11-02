@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package com.yuhinhsiung.clothesmate.core.platform

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import android.hardware.camera2.CameraCharacteristics

/**
 * M6 真机修复：CameraX 默认的 back camera 只保证“后置”，不保证是主摄。
 *
 * 多摄机型上系统可能把超广角/微距/景深排在前面，或返回一个逻辑多摄；
 * 这里按“有闪光灯 → 有效像素面积大 → cameraId 数值小”挑选主摄，
 * 找不到时退回 CameraX 默认后置，避免直接黑屏。
 */
internal data class CameraCandidate(
    val cameraId: String,
    val lensFacing: Int,
    val flashAvailable: Boolean,
    val activeArrayArea: Int,
)

internal fun selectMainBackCamera(candidates: List<CameraCandidate>): CameraCandidate? =
    candidates
        .asSequence()
        .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        .sortedWith(
            compareByDescending<CameraCandidate> { it.flashAvailable }
                .thenByDescending { it.activeArrayArea }
                .thenBy { it.cameraId.toIntOrNull() ?: Int.MAX_VALUE },
        )
        .firstOrNull()

/**
 * CameraSelector 的 filter：把候选收敛到主摄。Camera2CameraInfo 只在
 * camera-camera2 后端可用；任何一台相机读不到特性时都不应让整个预览失败。
 */
@SuppressLint("UnsafeOptInUsageError")
internal fun mainBackCameraFilter(infos: List<CameraInfo>): List<CameraInfo> {
    val candidates = infos.mapNotNull { info ->
        runCatching {
            val camera2 = Camera2CameraInfo.from(info)
            CameraCandidate(
                cameraId = camera2.cameraId,
                lensFacing = info.lensFacing,
                flashAvailable = info.hasFlashUnit(),
                activeArrayArea = camera2
                    .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?.let { it.width() * it.height() }
                    ?: 0,
            )
        }.getOrNull()
    }
    val best = selectMainBackCamera(candidates) ?: return infos
    return infos.filter { info ->
        runCatching { Camera2CameraInfo.from(info).cameraId == best.cameraId }.getOrDefault(false)
    }.ifEmpty { infos }
}

@SuppressLint("UnsafeOptInUsageError")
internal fun mainBackCameraSelector(): CameraSelector =
    CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .addCameraFilter(::mainBackCameraFilter)
        .build()
