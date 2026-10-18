package com.yuhinhsiung.clothesmate.feature.addclothing.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import com.yuhinhsiung.clothesmate.ui.theme.OnPhoto
import com.yuhinhsiung.clothesmate.ui.theme.PhotoScrim
import java.io.File

/**
 * 拍照页 —— 开发计划 M1-14 / UI 文档 §7.3、mockup-13。
 *
 * 全屏取景；顶部左关闭（返回 = 直接放弃，M1-15）、右闪光灯；中央圆角虚线引导框 +
 * 引导文案胶囊「平铺在浅色背景上效果更好」；底部约 120dp 深色半透明操作区：
 * 相册（转 Photo Picker）/ 快门（72+56dp，100ms 缩放反馈）/ 翻转镜头。
 *
 * 相机权限被拒绝或相机初始化失败时降级相册（M1-14 验收）：给出说明与 [从相册导入]
 * 入口，不阻断流程。拍照产出为应用缓存目录的 file:// Uri，由确认页统一压缩落盘。
 *
 * @param onBack 返回（直接放弃）
 * @param onCaptured 拍到照片 / 相册选定，参数为图片 Uri 字符串；去向由导航层按路由参数决定
 */
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = LocalReducedMotion.current
    // ── 相机权限（拒绝 → 降级相册，不崩） ──────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 相册兜底：权限拒绝 / 拍照失败时仍可导入（M1-14 验收）
    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { onCaptured(it.toString()) }
    }
    fun pickFromAlbum() {
        albumLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ── CameraX ────────────────────────────────────────────────
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    // 旧会话遗留的拍照缓存清理（review B-16：主线程 IO → IO 线程；且只清「10 分钟前」
    // 的文件——上一张刚拍完、表单暂存压缩仍在读时快速再进拍照页，删新文件会造成
    // 「图片处理失败」的窄竞态）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, CAPTURE_DIR)
            val cutoff = System.currentTimeMillis() - STALE_CAPTURE_MILLIS
            dir.listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    LaunchedEffect(hasCameraPermission, lensFacing) {
        if (!hasCameraPermission) return@LaunchedEffect
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        }.onFailure { cameraError = true }
    }

    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, CAPTURE_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptured(Uri.fromFile(file).toString())
                }

                override fun onError(exception: ImageCaptureException) {
                    cameraError = true
                }
            },
        )
    }

    val cameraReady = hasCameraPermission && !cameraError

    // ── UI ─────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 取景；权限未给 / 相机初始化失败时这层只是黑底，由下方降级 UI 接管
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // 顶部：左关闭（返回 = 直接放弃，M1-15）· 右闪光灯
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop + Dimens.Space.s2)
                .padding(horizontal = Dimens.Space.s2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleControlButton(
                icon = AppIcons.Close,
                contentDescription = stringResource(R.string.capture_close),
                onClick = onBack,
            )
            if (cameraReady) {
                CircleControlButton(
                    icon = AppIcons.Flash,
                    contentDescription = stringResource(
                        if (flashOn) R.string.capture_flash_on else R.string.capture_flash_off,
                    ),
                    tint = if (flashOn) MaterialTheme.colorScheme.tertiary else OnPhoto,
                    onClick = { flashOn = !flashOn },
                )
            }
        }

        if (cameraReady) {
            // 中央圆角虚线引导框（§7.3「提示衣物摆放范围」）+ 引导文案胶囊
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = Dimens.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val guideColor = OnPhoto.copy(alpha = 0.7f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.captureGuideHeight)
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            val dash = 10.dp.toPx()
                            drawRoundRect(
                                color = guideColor,
                                cornerRadius = CornerRadius(Dimens.Radius.m.toPx()),
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(dash, dash),
                                    ),
                                ),
                            )
                        },
                )
                Spacer(modifier = Modifier.height(Dimens.Space.s4))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PhotoScrim,
                ) {
                    Text(
                        text = stringResource(R.string.capture_guide),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnPhoto,
                        modifier = Modifier.padding(
                            horizontal = Dimens.Space.s4,
                            vertical = Dimens.Space.s2,
                        ),
                    )
                }
            }
        } else {
            // 权限拒绝 / 相机不可用：降级说明 + 相册入口（M1-14 验收「拒绝时降级相册」）
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Dimens.pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = AppIcons.PhotoCamera,
                    contentDescription = null,
                    tint = OnPhoto,
                    modifier = Modifier.size(Dimens.Icon.l + Dimens.Space.s4),
                )
                Text(
                    text = stringResource(R.string.capture_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnPhoto,
                    modifier = Modifier.padding(top = Dimens.Space.s3),
                )
                TextButton(onClick = ::pickFromAlbum) {
                    Text(
                        text = stringResource(R.string.capture_permission_fallback),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // 底部操作区：约 120dp 深色半透明（§7.3）
        // review UI-04：相机是全屏页，根 Scaffold 不再代它消费 insets，
        // 这里自己避开系统导航栏——外层 Box 承担背景，让蒙层一直铺到屏幕底边
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PhotoScrim)
                .navigationBarsPadding(),
        ) {
            val shutterLabel = stringResource(R.string.capture_shutter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.cameraBottomBarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左：相册（M1 用图标占位——mockup 的「最近一张缩略图」需媒体库查询，随 M4 连拍一起评估）
                CircleControlButton(
                    icon = AppIcons.Image,
                    contentDescription = stringResource(R.string.capture_album),
                    iconSize = Dimens.Icon.l,
                    onClick = ::pickFromAlbum,
                )

                // 中：快门（外圈 72dp 白环 + 内 56dp 实心，按压 100ms 缩放）
                val shutterInteraction = remember { MutableInteractionSource() }
                val pressed by shutterInteraction.collectIsPressedAsState()
                val shutterScale by animateFloatAsState(
                    targetValue = if (pressed) 0.9f else 1f,
                    animationSpec = tween(durationMillis = Dimens.Duration.Instant),
                    label = "shutter-scale",
                )
                Box(
                    modifier = Modifier
                        .size(Dimens.shutterOuter)
                        .scale(if (reducedMotion) 1f else shutterScale)
                        .border(3.dp, OnPhoto, CircleShape)
                        .padding(Dimens.Space.s2)
                        .clip(CircleShape)
                        .background(OnPhoto)
                        // review UI-06：快门是无文本 Box，原先既没有可读的操作名称也没有
                        // 按钮角色，读屏树里这个可点节点是「无名称」的；旁边的相册 / 翻转
                        // 控件却有名称。这里补 contentDescription + Button 角色 + 点击动作名。
                        .semantics { contentDescription = shutterLabel }
                        .clickable(
                            interactionSource = shutterInteraction,
                            indication = null,
                            onClickLabel = shutterLabel,
                            role = Role.Button,
                            onClick = ::takePhoto,
                        ),
                )

                // 右：翻转镜头
                CircleControlButton(
                    icon = AppIcons.CameraSwitch,
                    contentDescription = stringResource(R.string.capture_flip),
                    iconSize = Dimens.Icon.l,
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                )
            }
        }
    }
}

/** 中央引导框高度（衣物平铺区，约屏高 1/3，§7.3 示意框） */

private const val CAPTURE_DIR = "captured"

/** 拍照缓存「算陈旧」的阈值（B-16）：刚拍完的文件在会话流中仍被消费，不删 */
private const val STALE_CAPTURE_MILLIS = 10 * 60 * 1000L

/** 相机页圆形控制按钮（关闭 / 闪光灯 / 相册 / 翻转），触控区 48dp。 */
@Composable
private fun CircleControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp = Dimens.Icon.m,
    tint: Color = OnPhoto,
) {
    Box(
        modifier = Modifier
            .size(Dimens.minTouchTarget)
            .clip(CircleShape)
            .background(PhotoScrim)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
