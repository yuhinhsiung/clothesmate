package com.yuhinhsiung.clothesmate.feature.today.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuhinhsiung.clothesmate.R
import com.yuhinhsiung.clothesmate.core.model.WearFailure
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.core.weather.WeatherSnapshot
import com.yuhinhsiung.clothesmate.ui.component.CmAsyncImage
import com.yuhinhsiung.clothesmate.ui.component.WearConfirmDialog
import com.yuhinhsiung.clothesmate.ui.icon.AppIcons
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.statusOkText
import com.yuhinhsiung.clothesmate.ui.theme.onMuted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayRoute(
    onOpenCalendar: () -> Unit,
    onOpenOutfit: (Long) -> Unit,
    onCreateOutfit: (List<Long>) -> Unit,
    onAddClothing: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCitySheet by rememberSaveable { mutableStateOf(false) }
    var showScheduleSheet by rememberSaveable { mutableStateOf(false) }
    var noteRecord by remember { mutableStateOf<WearRecord?>(null) }
    var celebrate by remember { mutableStateOf(false) }
    var celebrateRepeated by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }

    fun askForLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.reloadWeather()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val allRecommendationsSeenText = stringResource(R.string.recommend_all_seen)
    val wearErrorText = stringResource(R.string.completion_wear_error)
    val wearOutfitDeletedText = stringResource(R.string.completion_outfit_deleted)

    LaunchedEffect(viewModel) {
        viewModel.refreshWeatherIfStale()
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.WearConfirmed -> {
                    celebrateRepeated = event.repeated
                    celebrate = true
                }
                TodayEvent.AllRecommendationsSeen -> scope.launch {
                    snackbarHostState.showSnackbar(allRecommendationsSeenText)
                }
                is TodayEvent.WearFailed -> scope.launch {
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
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(
                            imageVector = AppIcons.Calendar,
                            contentDescription = stringResource(R.string.today_calendar),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Dimens.Space.s8),
        ) {
            item {
                TodayGreeting(
                    today = state.today,
                    modifier = Modifier.padding(
                        start = Dimens.pagePadding,
                        end = Dimens.pagePadding,
                        top = Dimens.Space.s3,
                        bottom = Dimens.Space.s4,
                    ),
                )
            }
            item {
                WeatherCard(
                    state = state.weather,
                    city = state.cityLabel,
                    onChooseCity = { showCitySheet = true },
                    onOpenSettings = onOpenSettings,
                    onRetry = viewModel::reloadWeather,
                    onAskLocation = ::askForLocation,
                    modifier = Modifier.padding(horizontal = Dimens.pagePadding),
                )
            }
            item {
                ScheduleSection(
                    options = state.scheduleOptions,
                    selected = state.selectedSchedule,
                    onToggle = viewModel::toggleSchedule,
                    onCustom = { showScheduleSheet = true },
                    modifier = Modifier.padding(top = Dimens.Space.s5),
                )
            }
            if (state.candidates.isNotEmpty()) {
                item {
                    RecommendationsHeader(
                        count = state.candidates.size,
                        onShuffle = viewModel::shuffleRecommendations,
                        modifier = Modifier.padding(
                            start = Dimens.pagePadding,
                            end = Dimens.pagePadding,
                            top = Dimens.Space.s5,
                            bottom = Dimens.Space.s3,
                        ),
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
                    ) {
                        items(state.visibleRecommendations, key = { it.outfit.outfit.id }) { item ->
                            RecommendationCard(
                                recommended = item,
                                weather = state.weatherSnapshotOrNull,
                                confirming = state.confirmingOutfitId == item.outfit.outfit.id,
                                onOpen = { onOpenOutfit(item.outfit.outfit.id) },
                                onWear = { viewModel.requestWear(item) },
                            )
                        }
                    }
                }
            } else {
                state.fallback?.let { fallback ->
                    item {
                        FallbackCard(
                            fallback = fallback,
                            onShuffle = viewModel::shuffleFallback,
                            onSaveAsOutfit = { onCreateOutfit(fallback.items.map { it.id }) },
                            onCreateOutfit = { onCreateOutfit(emptyList()) },
                            modifier = Modifier.padding(
                                start = Dimens.pagePadding,
                                end = Dimens.pagePadding,
                                top = Dimens.Space.s5,
                            ),
                        )
                    }
                }
            }
            if (state.records.isNotEmpty()) {
                item {
                    CompletionSection(
                        records = state.records,
                        onViewOutfit = onOpenOutfit,
                        onEditNote = { noteRecord = it },
                        modifier = Modifier.padding(top = Dimens.Space.s6),
                    )
                }
            }
            if (state.candidates.isEmpty() && state.fallback == null && state.records.isEmpty()) {
                item {
                    TodayEmptyState(
                        onAddClothing = onAddClothing,
                        modifier = Modifier.padding(
                            start = Dimens.pagePadding,
                            end = Dimens.pagePadding,
                            top = Dimens.Space.s6,
                        ),
                    )
                }
            }
        }
    }

    if (showCitySheet) {
        CitySheet(
            currentCity = state.cityLabel,
            onDismiss = { showCitySheet = false },
            onSelect = {
                viewModel.selectCity(it)
                showCitySheet = false
            },
            onUseLocation = {
                viewModel.useCurrentLocation()
                askForLocation()
                showCitySheet = false
            },
        )
    }

    if (showScheduleSheet) {
        ScheduleSheet(
            onDismiss = { showScheduleSheet = false },
            onSave = {
                viewModel.addCustomSchedule(it)
                showScheduleSheet = false
            },
        )
    }

    noteRecord?.let { record ->
        NoteDialog(
            record = record,
            onDismiss = { noteRecord = null },
            onSave = { note ->
                viewModel.updateRecordNote(record.id, note)
                noteRecord = null
            },
        )
    }

    if (celebrate) {
        WearCelebration(
            simple = celebrateRepeated,
            onFinished = { celebrate = false },
        )
    }

    state.pendingWear?.let { pending ->
        WearConfirmDialog(
            outfit = pending.outfit.outfit,
            date = state.today,
            isBackfill = false,
            scheduleTags = state.selectedSchedule.sorted(),
            weather = state.weatherSnapshotOrNull,
            submitting = state.confirmingOutfitId != null,
            onConfirm = viewModel::confirmPendingWear,
            onDismiss = viewModel::dismissWearConfirm,
        )
    }
}

@Composable
private fun TodayGreeting(today: LocalDate, modifier: Modifier = Modifier) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> R.string.today_greeting_morning
        in 12..13 -> R.string.today_greeting_noon
        in 14..17 -> R.string.today_greeting_afternoon
        else -> R.string.today_greeting_evening
    }
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val weekday = weekdays.getOrElse(today.dayOfWeek.value - 1) { "一" }
    Column(modifier = modifier) {
        Text(
            text = stringResource(greeting),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = stringResource(
                R.string.today_date_format,
                today.monthValue,
                today.dayOfMonth,
            ) + " · " + stringResource(R.string.today_weekday_format, weekday),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

@Composable
private fun WeatherCard(
    state: WeatherUiState,
    city: String,
    onChooseCity: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onAskLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChooseCity)
                    .padding(vertical = Dimens.Space.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    imageVector = AppIcons.Location,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.Icon.s),
                    tint = MaterialTheme.colorScheme.onMuted,
                )
                Spacer(Modifier.width(Dimens.Space.s1))
                Text(
                    text = city.ifBlank { stringResource(R.string.weather_city_choose) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.Icon.s),
                    tint = MaterialTheme.colorScheme.onMuted,
                )
            }
            Spacer(Modifier.height(Dimens.Space.s2))
            when (state) {
                WeatherUiState.Loading -> WeatherLoading()
                WeatherUiState.NoKey -> WeatherNoKey(onOpenSettings)
                is WeatherUiState.NeedCity -> WeatherNeedCity(
                    locationDenied = state.locationDenied,
                    onAskLocation = onAskLocation,
                    onChooseCity = onChooseCity,
                )
                is WeatherUiState.Ready -> WeatherReady(state.snapshot, state.refreshing, onRetry)
                is WeatherUiState.Failed -> WeatherFailed(state, onRetry)
            }
        }
    }
}

@Composable
private fun WeatherLoading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(Dimens.Icon.m), strokeWidth = 2.dp)
        Spacer(Modifier.width(Dimens.Space.s3))
        Text(
            text = stringResource(R.string.weather_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
    }
}

@Composable
private fun WeatherNoKey(onOpenSettings: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.weather_no_key_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = stringResource(R.string.weather_no_key_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
        Spacer(Modifier.height(Dimens.Space.s3))
        Button(onClick = onOpenSettings) {
            Text(stringResource(R.string.weather_go_settings))
        }
    }
}

@Composable
private fun WeatherNeedCity(
    locationDenied: Boolean,
    onAskLocation: () -> Unit,
    onChooseCity: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (locationDenied) AppIcons.LocationOff else AppIcons.Location,
                contentDescription = null,
                modifier = Modifier.size(Dimens.Icon.m),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Dimens.Space.s2))
            Text(
                text = stringResource(R.string.weather_need_city_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(Dimens.Space.s1))
        Text(
            text = stringResource(
                if (locationDenied) {
                    R.string.weather_need_location_desc
                } else {
                    R.string.weather_need_city_desc
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onMuted,
        )
        Spacer(Modifier.height(Dimens.Space.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2)) {
            Button(onClick = onAskLocation) {
                Text(stringResource(R.string.weather_allow_location))
            }
            TextButton(onClick = onChooseCity) {
                Text(stringResource(R.string.weather_choose_city))
            }
        }
    }
}

@Composable
private fun WeatherReady(
    snapshot: WeatherSnapshot,
    refreshing: Boolean,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${snapshot.temp}°",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                )
                Spacer(Modifier.width(Dimens.Space.s2))
                Text(
                    text = snapshot.text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Dimens.Space.s2),
                )
            }
            Spacer(Modifier.height(Dimens.Space.s1))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3)) {
                Text(
                    text = stringResource(R.string.weather_feels_like, snapshot.feelsLike),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
                Text(
                    text = stringResource(
                        R.string.weather_range,
                        snapshot.minTemp,
                        snapshot.maxTemp,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
            if (refreshing) {
                Spacer(Modifier.height(Dimens.Space.s2))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Icon(
            imageVector = snapshot.condition.icon(),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(Dimens.Space.s2))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.weather_retry))
        }
    }
}

@Composable
private fun WeatherFailed(state: WeatherUiState.Failed, onRetry: () -> Unit) {
    val cached = state.cached
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (cached != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${cached.temp}°",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 44.sp,
                            lineHeight = 52.sp,
                        ),
                    )
                    Spacer(Modifier.width(Dimens.Space.s2))
                    Text(cached.text, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(
                    text = "--°",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 44.sp,
                        lineHeight = 52.sp,
                    ),
                )
            }
            Spacer(Modifier.height(Dimens.Space.s1))
            Text(
                text = stringResource(R.string.weather_refresh_failed) + " · " +
                    stringResource(state.failure.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onMuted,
            )
        }
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.weather_retry))
        }
    }
}

@Composable
private fun ScheduleSection(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.pagePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.width(Dimens.Space.s2))
            if (selected.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Dimens.Space.s2))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s2),
        ) {
            items(options, key = { it }) { tag ->
                FilterChip(
                    selected = tag in selected,
                    onClick = { onToggle(tag) },
                    label = { Text(tag) },
                    modifier = Modifier
                        .heightIn(min = Dimens.minTouchTarget)
                        .semantics {
                            role = Role.Checkbox
                            contentDescription = tag
                        },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onCustom,
                    label = { Text("＋ " + stringResource(R.string.schedule_custom)) },
                    modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun RecommendationsHeader(
    count: Int,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.recommend_title, count),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (count > 3) {
            TextButton(onClick = onShuffle) {
                Text(stringResource(R.string.recommend_shuffle))
            }
        }
    }
}

@Composable
private fun CompletionSection(
    records: List<WearRecord>,
    onViewOutfit: (Long) -> Unit,
    onEditNote: (WearRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.completion_title, records.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Dimens.pagePadding),
        )
        Spacer(Modifier.height(Dimens.Space.s2))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space.s3),
        ) {
            items(records, key = { it.id }) { record ->
                WearCompletionCard(
                    record = record,
                    onViewOutfit = onViewOutfit,
                    onEditNote = onEditNote,
                )
            }
        }
    }
}

@Composable
private fun WearCompletionCard(
    record: WearRecord,
    onViewOutfit: (Long) -> Unit,
    onEditNote: (WearRecord) -> Unit,
) {
    var checked by remember(record.id) { mutableStateOf(emptySet<Long>()) }
    val takenCount = record.items.count { it.itemId in checked }
    Card(
        modifier = Modifier.width(330.dp),
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
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
                        modifier = Modifier.clickable(enabled = record.outfitId != null) {
                            record.outfitId?.let(onViewOutfit)
                        },
                    )
                    Text(
                        text = record.metaLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.completion_worn_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.statusOkText,
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Space.s3))
            HorizontalDivider(color = MaterialTheme.colorScheme.muted)
            record.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.itemId in checked,
                        onCheckedChange = { value ->
                            checked = if (value) checked + item.itemId else checked - item.itemId
                        },
                        modifier = Modifier.semantics {
                            contentDescription = item.code + " " + item.displayName
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${item.code} · ${item.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.wardrobeName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Space.s1))
            Text(
                text = stringResource(R.string.completion_taken, takenCount, record.items.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            record.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Dimens.Space.s2))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onMuted,
                )
            }
            TextButton(onClick = { onEditNote(record) }) {
                Text(
                    stringResource(
                        if (record.note.isNullOrBlank()) R.string.completion_note
                        else R.string.completion_note_edit,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TodayEmptyState(onAddClothing: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Dimens.Space.s5)) {
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Dimens.Space.s1))
            Text(
                text = stringResource(R.string.today_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
            )
            Spacer(Modifier.height(Dimens.Space.s3))
            Button(onClick = onAddClothing) {
                Text(stringResource(R.string.today_empty_action))
            }
        }
    }
}
