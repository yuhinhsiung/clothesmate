package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * M2-06 一次性 AI 隐私告知弹窗（UI 文档 §7.6 / mockup-21）。
 *
 * 两处共用：录入确认页「首次触发识别前」与设置页「重新开启授权」。
 * 标题/三条要点/按钮文案全部走 strings，确保两处口径一致。
 */
@Composable
fun AiPrivacyDialog(
    endpointHost: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Ai,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(Dimens.Icon.m),
                )
                Text(
                    text = stringResource(R.string.ai_privacy_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Dimens.Space.s2),
                )
            }
        },
        text = {
            Column {
                PrivacyPoint(
                    icon = AppIcons.Image,
                    text = stringResource(R.string.ai_privacy_point_upload),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (endpointHost != null) {
                    Text(
                        text = stringResource(R.string.ai_privacy_endpoint, endpointHost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onMuted,
                        modifier = Modifier.padding(start = Dimens.Space.s5, top = Dimens.Space.s1),
                    )
                }
                PrivacyPoint(
                    icon = AppIcons.Location,
                    text = stringResource(R.string.ai_privacy_point_local),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Dimens.Space.s3),
                )
                PrivacyPoint(
                    icon = AppIcons.Ai,
                    text = stringResource(R.string.ai_privacy_point_prefill),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = Dimens.Space.s3),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(
                    text = stringResource(R.string.ai_privacy_accept),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(
                    text = stringResource(R.string.ai_privacy_decline),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun PrivacyPoint(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(Dimens.Icon.s)
                .padding(top = Dimens.Space.s1),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Dimens.Space.s2),
        )
    }
}

/** 设置页/确认页共用的「当前端点」展示值 */
fun privacyEndpointHost(baseUrl: String): String? =
    com.yuhinhsiung.clothesmate.core.ai.EndpointNormalizer.hostOf(baseUrl)
