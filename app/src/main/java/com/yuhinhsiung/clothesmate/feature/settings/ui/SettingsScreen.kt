package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.BuildConfig
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.datastore.AiConsent
import com.yuhinhsiung.clothesmate.core.datastore.AiStatus
import com.yuhinhsiung.clothesmate.core.datastore.ThemeMode
import com.yuhinhsiung.clothesmate.ui.component.AiPrivacyDialog
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.component.privacyEndpointHost
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.StatusOk
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 设置页 —— M2-05 / UI 文档 §7.6、mockup-12。
 *
 * 分组：外观（深色模式三段）/ AI 配置（Base URL / Key / 模型 + 测试连接 + 状态点）/
 * 天气（城市 + 和风天气 Key，M3 使用）/ 备份与存储（M4 占位）/ 关于。
 *
 * 入口唯一：概览 Tab 顶栏右侧 `settings`（UI 文档 §6.2）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCityDialog by rememberSaveable { mutableStateOf(false) }
    var showWeatherKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showConsentDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = Dimens.Space.s6),
        ) {
            SettingsGroup(title = stringResource(R.string.settings_group_appearance)) {
                SettingsCard {
                    ThemeModeRow(
                        current = state.themeMode,
                        onChange = viewModel::onThemeModeChange,
                        modifier = Modifier.padding(Dimens.Space.s4),
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_ai)) {
                AiConfigCard(
                    state = state,
                    onBaseUrlChange = viewModel::onBaseUrlChange,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    onModelChange = viewModel::onModelChange,
                    onTest = viewModel::testConnection,
                    onConsentChange = viewModel::onConsentChange,
                    onRequestConsent = { showConsentDialog = true },
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_weather)) {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_weather_city),
                        value = state.weatherCity.ifBlank {
                            stringResource(R.string.settings_weather_city_unset)
                        },
                        onClick = { showCityDialog = true },
                    )
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_weather_key),
                        value = if (state.weatherApiKey.isBlank()) {
                            stringResource(R.string.settings_weather_unconfigured)
                        } else {
                            stringResource(R.string.settings_weather_configured)
                        },
                        onClick = { showWeatherKeyDialog = true },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_weather_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onMuted,
                    modifier = Modifier.padding(start = Dimens.Space.s2, top = Dimens.Space.s2),
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_backup)) {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_backup_export),
                        value = stringResource(R.string.settings_backup_export_desc),
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_backup_import),
                        value = stringResource(R.string.settings_backup_import_desc),
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    SettingsRow(
                        title = stringResource(R.string.settings_backup_clean),
                        value = stringResource(R.string.settings_backup_clean_desc),
                        enabled = false,
                        onClick = {},
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_about)) {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(
                            R.string.settings_about_version,
                            BuildConfig.VERSION_NAME,
                        ),
                        value = null,
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider(
                        thickness = Dimens.dividerThickness,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    InfoRow(text = stringResource(R.string.settings_about_local))
                    InfoRow(text = stringResource(R.string.settings_about_license))
                }
            }
        }
    }

    if (showCityDialog) {
        SettingsTextDialog(
            title = stringResource(R.string.settings_city_dialog),
            label = stringResource(R.string.settings_weather_city),
            initial = state.weatherCity,
            isPassword = false,
            onDismiss = { showCityDialog = false },
            onConfirm = { value ->
                showCityDialog = false
                viewModel.onWeatherCityChange(value)
            },
        )
    }
    if (showConsentDialog) {
        AiPrivacyDialog(
            endpointHost = privacyEndpointHost(state.baseUrl),
            onAccept = {
                showConsentDialog = false
                viewModel.onConsentChange(true)
            },
            onDecline = {
                showConsentDialog = false
                viewModel.onConsentChange(false)
            },
        )
    }
    if (showWeatherKeyDialog) {
        SettingsTextDialog(
            title = stringResource(R.string.settings_weather_key_dialog),
            label = stringResource(R.string.settings_weather_key),
            initial = state.weatherApiKey,
            isPassword = true,
            onDismiss = { showWeatherKeyDialog = false },
            onConfirm = { value ->
                showWeatherKeyDialog = false
                viewModel.onWeatherKeyChange(value)
            },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Dimens.pagePadding)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onMuted,
            modifier = Modifier.padding(start = Dimens.Space.s1, top = Dimens.Space.s5, bottom = Dimens.Space.s2),
        )
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** 深色模式三段（跟随系统 / 浅色 / 深色）—— 立即生效由 MainActivity 根主题负责。 */
@Composable
private fun ThemeModeRow(
    current: ThemeMode,
    onChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_dark_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .padding(top = Dimens.Space.s3)
                .fillMaxWidth()
                .height(Dimens.settingsSegmentHeight)
                .background(
                    color = MaterialTheme.colorScheme.muted,
                    shape = RoundedCornerShape(Dimens.Radius.s + Dimens.Space.s1),
                )
                .padding(Dimens.Space.s1),
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == current
                val label = when (mode) {
                    ThemeMode.System -> stringResource(R.string.settings_theme_system)
                    ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                    ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.settingsSegmentHeight - Dimens.Space.s2)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(Dimens.Radius.s),
                        )
                        .clickable { onChange(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onMuted
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiConfigCard(
    state: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onTest: () -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onRequestConsent: () -> Unit,
) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    val statusColor = when (state.aiStatus) {
        AiStatus.NotConfigured, AiStatus.Unverified -> MaterialTheme.colorScheme.onMuted
        AiStatus.Verified -> StatusOk
        AiStatus.Failed -> MaterialTheme.colorScheme.error
    }
    val statusText = when (state.aiStatus) {
        AiStatus.NotConfigured -> stringResource(R.string.settings_ai_status_not_configured)
        AiStatus.Unverified -> stringResource(R.string.settings_ai_status_unverified)
        AiStatus.Verified -> stringResource(R.string.settings_ai_status_verified)
        AiStatus.Failed -> stringResource(R.string.settings_ai_status_failed)
    }

    SettingsCard {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Ai,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(Dimens.Icon.s),
                )
                Text(
                    text = stringResource(R.string.settings_ai_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.Space.s2),
                )
                Box(
                    modifier = Modifier
                        .size(Dimens.statusDotSize)
                        .background(statusColor, CircleShape),
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(start = Dimens.Space.s1),
                )
            }

            CmTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                label = stringResource(R.string.settings_ai_base_url),
                placeholder = stringResource(R.string.settings_ai_base_url_hint),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.padding(top = Dimens.Space.s4),
            )
            CmTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                label = stringResource(R.string.settings_ai_key),
                placeholder = stringResource(R.string.settings_ai_key_hint),
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingContent = {
                    IconButton(
                        onClick = { showKey = !showKey },
                        modifier = Modifier.size(Dimens.minTouchTarget),
                    ) {
                        Icon(
                            imageVector = if (showKey) AppIcons.VisibilityOff else AppIcons.Visibility,
                            contentDescription = stringResource(
                                if (showKey) R.string.settings_ai_key_hide else R.string.settings_ai_key_show,
                            ),
                            tint = MaterialTheme.colorScheme.onMuted,
                            modifier = Modifier.size(Dimens.Icon.s),
                        )
                    }
                },
                modifier = Modifier.padding(top = Dimens.Space.s3),
            )
            CmTextField(
                value = state.model,
                onValueChange = onModelChange,
                label = stringResource(R.string.settings_ai_model),
                placeholder = stringResource(R.string.settings_ai_model_hint),
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = Dimens.Space.s3),
            )

            Button(
                onClick = onTest,
                enabled = !state.testing &&
                    state.baseUrl.isNotBlank() &&
                    state.apiKey.isNotBlank() &&
                    state.model.isNotBlank(),
                shape = RoundedCornerShape(Dimens.Radius.m),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = Dimens.DisabledAlpha),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        .copy(alpha = Dimens.DisabledAlpha),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.Space.s4)
                    .height(Dimens.settingsTestButtonHeight),
            ) {
                Text(
                    text = stringResource(
                        if (state.testing) R.string.settings_ai_testing else R.string.settings_ai_test,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Text(
                text = stringResource(R.string.settings_ai_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(top = Dimens.Space.s2),
            )
            if (state.aiStatus == AiStatus.Failed && !state.aiStatusDetail.isNullOrBlank()) {
                Text(
                    text = state.aiStatusDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Dimens.Space.s1),
                )
            }
            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            SettingsError.Incomplete -> R.string.settings_ai_incomplete
                            SettingsError.InvalidUrl -> R.string.settings_ai_invalid_url
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Dimens.Space.s1),
                )
            }

            HorizontalDivider(
                thickness = Dimens.dividerThickness,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = Dimens.Space.s4),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_ai_consent),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (state.aiConsent) {
                            AiConsent.Granted -> stringResource(R.string.settings_ai_consent_granted)
                            AiConsent.Declined -> stringResource(R.string.settings_ai_consent_declined)
                            AiConsent.Unasked -> stringResource(R.string.settings_ai_consent_unasked)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
                Switch(
                    checked = state.aiConsent == AiConsent.Granted,
                    // 开启必须经过一次隐私告知（M2-06）；关闭 = 明确拒绝本次授权
                    onCheckedChange = { checked ->
                        if (checked) onRequestConsent() else onConsentChange(false)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.settings_ai_consent_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }
}

/** 设置行（标题 + 右侧值 + 点击）；禁用时 38% 透明且不可点（UI 文档 §6.3）。 */
@Composable
private fun SettingsRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else Dimens.DisabledAlpha)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.Space.s4, vertical = Dimens.Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(start = Dimens.Space.s2),
            )
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onMuted,
        modifier = Modifier.padding(horizontal = Dimens.Space.s4, vertical = Dimens.Space.s3),
    )
}

@Composable
private fun SettingsTextDialog(
    title: String,
    label: String,
    initial: String,
    isPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.Radius.l),
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            CmTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                singleLine = true,
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text(
                    text = stringResource(R.string.settings_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
