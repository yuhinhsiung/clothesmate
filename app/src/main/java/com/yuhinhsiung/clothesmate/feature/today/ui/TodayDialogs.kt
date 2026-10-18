package com.yuhinhsiung.clothesmate.feature.today.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CitySheet(
    currentCity: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onUseLocation: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable(currentCity) { mutableStateOf(currentCity) }
    val cities = listOf("杭州", "上海", "北京", "深圳", "广州", "成都", "南京", "武汉")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s8),
        ) {
            Text(
                text = stringResource(R.string.weather_city_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                cities.forEach { city ->
                    FilterChip(
                        selected = city == currentCity,
                        onClick = { query = city },
                        label = { Text(city) },
                        modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space.s3))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.weather_city_sheet_title)) },
                placeholder = { Text(stringResource(R.string.weather_city_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (query.isNotBlank()) onSelect(query)
                }),
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.weather_city_cancel))
                }
                Button(
                    onClick = { onSelect(query) },
                    modifier = Modifier.weight(1f),
                    enabled = query.isNotBlank(),
                ) {
                    Text(stringResource(R.string.weather_city_confirm))
                }
            }
            TextButton(onClick = onUseLocation) {
                Text(stringResource(R.string.weather_use_location))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s8),
        ) {
            Text(
                text = stringResource(R.string.schedule_custom_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.schedule_custom_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) onSave(text)
                }),
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.schedule_cancel))
                }
                Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                    Text(stringResource(R.string.schedule_save))
                }
            }
        }
    }
}

@Composable
internal fun NoteDialog(
    record: WearRecord,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(record.id) { mutableStateOf(record.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.completion_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.completion_note_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.completion_note_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.completion_note_cancel))
            }
        },
    )
}

@Composable
internal fun WearCelebration(onFinished: () -> Unit, simple: Boolean = false) {
    val reducedMotion = LocalReducedMotion.current
    if (reducedMotion) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(600))
        onFinished()
    }
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!simple) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                repeat(28) { index ->
                    val angle = Math.toRadians((index * 13).toDouble())
                    val distance = (90 + (index % 7) * 26) * progress.value
                    val point = Offset(
                        x = center.x + cos(angle).toFloat() * distance,
                        y = center.y + sin(angle).toFloat() * distance,
                    )
                    drawCircle(
                        color = if (index % 2 == 0) primary else accent,
                        radius = (2 + index % 3) * density,
                        center = point,
                        alpha = (1f - progress.value).coerceIn(0f, 1f),
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(Dimens.Radius.l),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.Space.s5, vertical = Dimens.Space.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.Icon.l),
                )
                Spacer(Modifier.width(Dimens.Space.s3))
                Text(
                    text = stringResource(R.string.wear_celebrated),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
