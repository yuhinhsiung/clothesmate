package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import java.time.LocalDate

/**
 * 穿着二次确认（M3 修订，用户 2026-10-19 反馈）。
 *
 * 今日推荐、穿搭详情、日历补记共用。确认前不写 `wear_record`、不加计数，
 * 避免误触把「看过」变成「穿过」。备注随本次记录保存，可留空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearConfirmDialog(
    outfit: Outfit,
    date: LocalDate,
    isBackfill: Boolean,
    scheduleTags: List<String>,
    weather: WeatherSnapshot?,
    submitting: Boolean,
    onConfirm: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(outfit.id, date.toString()) { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s6),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
        ) {
            Text(
                text = stringResource(
                    if (isBackfill) R.string.wear_confirm_backfill_title else R.string.wear_confirm_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
            ) {
                CmAsyncImage(
                    model = outfit.coverPath,
                    contentDescription = outfit.displayName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(Dimens.Radius.s)),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = outfit.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.wear_confirm_date,
                            date.monthValue,
                            date.dayOfMonth,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                    Text(
                        text = stringResource(R.string.wear_confirm_items, outfit.itemCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onMuted,
                    )
                }
            }
            if (scheduleTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.wear_confirm_schedule, scheduleTags.joinToString(" · ")),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (weather != null) {
                Text(
                    text = stringResource(
                        R.string.wear_confirm_weather,
                        weather.text,
                        weather.minTemp,
                        weather.maxTemp,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
            if (isBackfill) {
                Text(
                    text = stringResource(R.string.wear_confirm_backfill_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
            Spacer(Modifier.height(Dimens.Space.s1))
            CmTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = stringResource(R.string.wear_confirm_note),
                placeholder = stringResource(R.string.wear_confirm_note_hint),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                enabled = !submitting,
                // review R08：备注最多 500 字，原先无高度上限会把取消/确认挤出屏幕；
                // 限制在 4 行内，超出部分在输入框内部滚动。
                modifier = Modifier.heightIn(max = 120.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text(stringResource(R.string.wear_confirm_cancel))
                }
                Button(
                    onClick = { onConfirm(note) },
                    enabled = !submitting,
                    modifier = Modifier.height(Dimens.buttonHeight),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.Icon.s),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.wear_confirm_action))
                    }
                }
            }
        }
    }
}
