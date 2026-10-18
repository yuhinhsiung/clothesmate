package com.yuhinhsiung.clothesmate.feature.today.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.Outfit
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.feature.today.ui.NoteDialog
import com.yuhinhsiung.clothesmate.feature.today.ui.messageRes
import com.yuhinhsiung.clothesmate.feature.today.ui.metaLine
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearCalendarRoute(
    onBack: () -> Unit,
    onOpenOutfit: (Long) -> Unit,
    viewModel: WearCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showOutfitPicker by remember { mutableStateOf(false) }
    var noteRecord by remember { mutableStateOf<WearRecord?>(null) }

    val backfilledText = stringResource(R.string.calendar_backfilled)
    val wearErrorText = stringResource(R.string.completion_wear_error)
    val wearOutfitDeletedText = stringResource(R.string.completion_outfit_deleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WearCalendarEvent.Backfilled -> scope.launch {
                    snackbarHostState.showSnackbar(backfilledText)
                }
                is WearCalendarEvent.BackfillFailed -> scope.launch {
                    val message = if (event.failure == WearFailure.OUTFIT_NOT_FOUND) {
                        wearOutfitDeletedText
                    } else {
                        wearErrorText
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = stringResource(R.string.calendar_close),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.pagePadding),
        ) {
            MonthHeader(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
            WeekdayHeader()
            MonthGrid(
                month = state.month,
                today = state.today,
                recordsByDate = state.recordsByDate,
                onSelect = viewModel::selectDate,
            )
            if (state.recordsByDate.isEmpty()) {
                EmptyCalendar()
            }
        }
    }

    state.selectedDate?.let { date ->
        if (showOutfitPicker) {
            OutfitPickerSheet(
                outfits = state.outfits,
                backfilling = state.backfilling,
                onDismiss = { showOutfitPicker = false },
                onPick = { outfitId ->
                    viewModel.backfill(outfitId)
                    showOutfitPicker = false
                },
            )
        } else {
            DaySheet(
                date = date,
                records = state.selectedRecords,
                onDismiss = viewModel::dismissDate,
                onBackfill = { showOutfitPicker = true },
                onOpenOutfit = onOpenOutfit,
                onEditNote = { noteRecord = it },
            )
        }
    }

    noteRecord?.let { record ->
        NoteDialog(
            record = record,
            onDismiss = { noteRecord = null },
            onSave = { note ->
                viewModel.updateNote(record.id, note)
                noteRecord = null
            },
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(AppIcons.ChevronLeft, stringResource(R.string.calendar_prev))
        }
        Text(
            text = stringResource(R.string.calendar_month_format, month.year, month.monthValue),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(AppIcons.ChevronRight, stringResource(R.string.calendar_next))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.Space.s2)) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    recordsByDate: Map<LocalDate, List<WearRecord>>,
    onSelect: (LocalDate) -> Unit,
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val dayCount = month.lengthOfMonth()
    val cellCount = firstOffset + dayCount
    val rows = (cellCount + 6) / 7
    Column(modifier = Modifier.padding(top = Dimens.Space.s1)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val day = index - firstOffset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..dayCount) {
                            DayCell(
                                date = month.atDay(day),
                                today = today,
                                record = recordsByDate[month.atDay(day)]?.firstOrNull(),
                                onClick = { onSelect(month.atDay(day)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    today: LocalDate,
    record: WearRecord?,
    onClick: () -> Unit,
) {
    val isToday = date == today
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(Dimens.Radius.s))
            .clickable(onClick = onClick)
            .semantics { contentDescription = date.toString() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onMuted,
        )
        Spacer(Modifier.height(Dimens.Space.s1))
        Box(
            modifier = Modifier
                .size(34.dp)
                .then(
                    if (isToday) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                record?.coverPath != null -> CmAsyncImage(
                    model = record.coverPath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                )
                record != null -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                else -> Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(Color.Transparent, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun EmptyCalendar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.calendar_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = stringResource(R.string.calendar_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    records: List<WearRecord>,
    onDismiss: () -> Unit,
    onBackfill: () -> Unit,
    onOpenOutfit: (Long) -> Unit,
    onEditNote: (WearRecord) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s8),
        ) {
            Text(
                text = stringResource(R.string.calendar_day_format, date.monthValue, date.dayOfMonth),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                LazyColumn {
                    items(records, key = { it.id }) { record ->
                        DayRecord(
                            record = record,
                            onOpenOutfit = onOpenOutfit,
                            onEditNote = onEditNote,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.muted)
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Space.s3))
            if (date.isAfter(LocalDate.now())) {
                Text(
                    text = stringResource(R.string.calendar_future),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                Button(onClick = onBackfill, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calendar_backfill))
                }
            }
        }
    }
}

@Composable
private fun DayRecord(
    record: WearRecord,
    onOpenOutfit: (Long) -> Unit,
    onEditNote: (WearRecord) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Dimens.Radius.s))
                .background(MaterialTheme.colorScheme.muted),
        ) {
            CmAsyncImage(
                model = record.coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(Dimens.Space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.outfitName ?: stringResource(R.string.calendar_no_outfit),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = record.metaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            record.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                record.outfitId?.let { outfitId ->
                    TextButton(onClick = { onOpenOutfit(outfitId) }) {
                        Text(stringResource(R.string.calendar_view_outfit))
                    }
                }
                TextButton(onClick = { onEditNote(record) }) {
                    Text(stringResource(R.string.completion_note))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutfitPickerSheet(
    outfits: List<Outfit>,
    backfilling: Boolean,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.Space.s8),
        ) {
            Text(
                text = stringResource(R.string.calendar_pick_outfit),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Dimens.Space.s1))
            Text(
                text = stringResource(R.string.calendar_pick_outfit_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            if (backfilling) {
                CircularProgressIndicator()
            } else if (outfits.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(outfits, key = { it.id }) { outfit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable { onPick(outfit.id) }
                                .padding(vertical = Dimens.Space.s1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(Dimens.Radius.s))
                                    .background(MaterialTheme.colorScheme.muted),
                            ) {
                                CmAsyncImage(
                                    model = outfit.coverPath,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(Dimens.Space.s3))
                            Column {
                                Text(
                                    text = outfit.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.recommend_items, outfit.itemCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
