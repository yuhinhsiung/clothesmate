package com.yuhinhsiung.clothesmate.core.image

import android.graphics.Matrix
import android.media.ExifInterface

/**
 * EXIF 方向 → 变换矩阵（**8 种取值逐一显式映射**，review R-15）。
 *
 * 表与 AOSP / Glide 的 `Downsampler` 同源，逐条都能用四角色块样图反推：
 *
 * | 取值 | 含义 | 矩阵 |
 * |---|---|---|
 * | 1 / UNDEFINED | 正常 | 恒等 |
 * | 2 FLIP_HORIZONTAL | 水平镜像 | `scale(-1, 1)` |
 * | 3 ROTATE_180 | 旋转 180° | `rotate(180)` |
 * | 4 FLIP_VERTICAL | 垂直镜像 | `scale(1, -1)` |
 * | 5 TRANSPOSE | 沿主对角线翻转 | `rotate(90)` + `scale(-1, 1)` |
 * | 6 ROTATE_90 | 顺时针 90° | `rotate(90)` |
 * | 7 TRANSVERSE | 沿副对角线翻转 | `rotate(-90)` + `scale(-1, 1)` |
 * | 8 ROTATE_270 | 顺时针 270° | `rotate(-90)` |
 *
 * 压缩（[DefaultImageCompressor]）与 M2 的抠图/AI 取图（[DefaultBitmapLoader]）共用这一份，
 * 避免「同一张竖拍照片，列表是正的、抠图是躺的」这类分叉。
 *
 * ⚠️ 顺序敏感：`setRotate(x)` **先** `postScale(-1,1)`（先缩放后旋转）才是正确组合，
 * 两者对调会得到镜像轴相反的图像。改动此表必须重跑 `ExifOrientationTest` 的像素断言。
 */
internal object ExifOrientation {

    fun matrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            // ORIENTATION_NORMAL / ORIENTATION_UNDEFINED 及未知取值：恒等
            else -> Unit
        }
    }
}
