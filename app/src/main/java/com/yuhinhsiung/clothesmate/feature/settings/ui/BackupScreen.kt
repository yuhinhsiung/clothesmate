package com.yuhinhsiung.clothesmate.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.backup.BackupSummary
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            importUri = it
            viewModel.inspect(it)
        }
    }

    val exported = stringResource(R.string.backup_export_success)
    val imported = stringResource(R.string.backup_import_success)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.Exported -> snackbar.showSnackbar(exported)
                is BackupEvent.Imported -> snackbar.showSnackbar(imported)
                is BackupEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            AppIcons.Back,
                            stringResource(R.string.backup_close),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Text(
                stringResource(R.string.backup_sensitive_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            BackupCard(title = stringResource(R.string.backup_export_title)) {
                Text(
                    stringResource(R.string.backup_export_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
                Spacer(Modifier.height(Dimens.Space.s3))
                Button(
                    onClick = {
                        val name = "clothesmate-${LocalDate.now()}.zip"
                        exportLauncher.launch(name)
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_export_action))
                }
            }

            BackupCard(title = stringResource(R.string.backup_import_title)) {
                Text(
                    stringResource(R.string.backup_import_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
                Spacer(Modifier.height(Dimens.Space.s3))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_import_pick))
                }
                if (state.inspecting) {
                    Row(
                        Modifier.padding(top = Dimens.Space.s3),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.backup_import_reading),
                            modifier = Modifier.padding(start = Dimens.Space.s2),
                        )
                    }
                }
                state.summary?.let { summary ->
                    SummaryCard(
                        summary = summary,
                        onImport = { showConfirm = true },
                        enabled = state.canImport,
                    )
                }
            }

            state.importResult?.let { result ->
                BackupCard(title = stringResource(R.string.backup_result_title)) {
                    Text(
                        stringResource(
                            R.string.backup_result_line,
                            result.wardrobes,
                            result.clothing,
                            result.outfits,
                            result.wearRecords,
                            result.images,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result.skipped > 0) {
                        Text(
                            stringResource(R.string.backup_result_skipped, result.skipped),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (showConfirm && importUri != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        importUri?.let(viewModel::import)
                    },
                ) { Text(stringResource(R.string.backup_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.backup_cancel))
                }
            },
        )
    }
}

@Composable
private fun BackupCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.Space.s4)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimens.Space.s2))
            content()
        }
    }
}

@Composable
private fun SummaryCard(summary: BackupSummary, onImport: () -> Unit, enabled: Boolean) {
    val date = remember(summary.exportedAt) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(summary.exportedAt))
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.Space.s3),
    ) {
        Column(Modifier.padding(Dimens.Space.s3)) {
            Text(stringResource(R.string.backup_summary_title), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    R.string.backup_summary_line,
                    summary.wardrobes,
                    summary.clothing,
                    summary.outfits,
                    summary.wearRecords,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.backup_exported_at, date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Spacer(Modifier.height(Dimens.Space.s2))
            Button(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_import_action))
            }
        }
    }
}
