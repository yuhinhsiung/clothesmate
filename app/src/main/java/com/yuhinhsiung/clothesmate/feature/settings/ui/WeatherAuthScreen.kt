package com.yuhinhsiung.clothesmate.feature.settings.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.component.CmTextField
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 和风天气 JWT 认证配置页（M3 修订）。
 *
 * 2026 起 QWeather 的公共 API Host 不再提供服务，认证也从 Key 改成 Ed25519 JWT。
 * 这个页面把「控制台复制哪些值」讲清楚，并在保存前校验私钥能否完成签名。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherAuthRoute(
    onBack: () -> Unit,
    viewModel: WeatherAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                // 没有浏览器时保持页面可编辑，不打断配置流程
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.weather_auth_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, stringResource(R.string.action_back))
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
                .navigationBarsPadding()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
        ) {
            Text(
                text = stringResource(R.string.weather_auth_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.muted,
                ),
                shape = RoundedCornerShape(Dimens.Radius.m),
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.Space.s4),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                ) {
                    Text(
                        text = stringResource(R.string.weather_auth_steps_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    AuthStep(1, stringResource(R.string.weather_auth_step_1))
                    AuthStep(2, stringResource(R.string.weather_auth_step_2))
                    AuthStep(3, stringResource(R.string.weather_auth_step_3))
                    AuthStep(4, stringResource(R.string.weather_auth_step_4))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                Button(
                    onClick = { openUrl("https://console.qweather.com") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.weather_auth_open_console))
                }
                TextButton(
                    onClick = { openUrl("https://dev.qweather.com/docs/configuration/authentication/") },
                ) {
                    Text(stringResource(R.string.weather_auth_open_docs))
                }
            }

            Spacer(Modifier.height(Dimens.Space.s1))
            CmTextField(
                value = state.apiHost,
                onValueChange = viewModel::onApiHostChange,
                label = stringResource(R.string.weather_auth_api_host),
                placeholder = stringResource(R.string.weather_auth_api_host_hint),
            )
            CmTextField(
                value = state.developerId,
                onValueChange = viewModel::onDeveloperIdChange,
                label = stringResource(R.string.weather_auth_developer_id),
                placeholder = "Q1234567890",
            )
            CmTextField(
                value = state.projectId,
                onValueChange = viewModel::onProjectIdChange,
                label = stringResource(R.string.weather_auth_project_id),
            )
            CmTextField(
                value = state.keyId,
                onValueChange = viewModel::onKeyIdChange,
                label = stringResource(R.string.weather_auth_key_id),
            )
            CmTextField(
                value = state.privateKeyPem,
                onValueChange = viewModel::onPrivateKeyChange,
                label = stringResource(R.string.weather_auth_private_key),
                placeholder = stringResource(
                    if (state.hasStoredKey) {
                        R.string.weather_auth_private_key_saved
                    } else {
                        R.string.weather_auth_private_key_placeholder
                    },
                ),
                singleLine = false,
                minLines = 5,
            )
            Text(
                text = stringResource(R.string.weather_auth_private_key_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
            state.error?.let { error ->
                Text(
                    text = stringResource(
                        when (error) {
                            WeatherAuthError.Incomplete -> R.string.weather_auth_error_incomplete
                            WeatherAuthError.InvalidHost -> R.string.weather_auth_error_host
                            WeatherAuthError.InvalidPrivateKey -> R.string.weather_auth_error_key
                            WeatherAuthError.SaveFailed -> R.string.weather_auth_error_save
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { viewModel.save(onBack) },
                enabled = state.loaded && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.Icon.s),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.weather_auth_save))
                }
            }
            Spacer(Modifier.height(Dimens.Space.s2))
            Text(
                text = stringResource(R.string.weather_auth_cost_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }
}

@Composable
private fun AuthStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
