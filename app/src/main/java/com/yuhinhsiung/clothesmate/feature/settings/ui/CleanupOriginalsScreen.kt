package com.yuhinhsiung.clothesmate.feature.settings.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupOriginalsRoute(
    onBack: () -> Unit,
    viewModel: CleanupOriginalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cleanup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, stringResource(R.string.cleanup_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.pagePadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
        ) {
            Text(
                stringResource(R.string.cleanup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Dimens.Space.s4)) {
                    Text(
                        stringResource(R.string.cleanup_estimate_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    val estimate = state.estimate
                    if (state.loading && estimate == null) {
                        CircularProgressIndicator(Modifier.padding(top = Dimens.Space.s2))
                    } else {
                        Text(
                            stringResource(
                                R.string.cleanup_estimate_value,
                                estimate?.count ?: 0,
                                Formatter.formatShortFileSize(context, estimate?.bytes ?: 0L),
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    Spacer(Modifier.height(Dimens.Space.s3))
                    Button(
                        onClick = { confirm = true },
                        enabled = !state.loading && (estimate?.count ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cleanup_action))
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { result ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            R.string.cleanup_result,
                            result.count,
                            Formatter.formatShortFileSize(context, result.bytes),
                        ),
                        modifier = Modifier.padding(Dimens.Space.s3),
                    )
                }
            }
            Text(
                stringResource(R.string.cleanup_safety),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.cleanup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cleanup_confirm_body,
                        state.estimate?.count ?: 0,
                        Formatter.formatShortFileSize(context, state.estimate?.bytes ?: 0L),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        viewModel.cleanup()
                    },
                ) { Text(stringResource(R.string.cleanup_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.cleanup_cancel))
                }
            },
        )
    }
}
