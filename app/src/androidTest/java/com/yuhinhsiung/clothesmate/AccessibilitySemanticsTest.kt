package com.yuhinhsiung.clothesmate

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuhinhsiung.clothesmate.core.model.WearRecord
import com.yuhinhsiung.clothesmate.feature.overview.ui.CategorySlice
import com.yuhinhsiung.clothesmate.feature.overview.ui.DonutChart
import com.yuhinhsiung.clothesmate.feature.today.ui.calendar.MonthGrid
import com.yuhinhsiung.clothesmate.ui.theme.ClothesMateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** M5-05：为原本只有 uiautomator 证据的日历 / 概览图补 Compose 语义断言。 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun dayCellAnnouncesFullDateTodayAndWearRecord() {
        val today = LocalDate.of(2026, 10, 26)
        val record = WearRecord(
            id = 1,
            date = today,
            outfitId = null,
            scheduleTags = emptyList(),
            weatherJson = null,
            note = null,
            createdAt = 0,
            outfit = null,
            items = emptyList(),
        )
        rule.setContent {
            ClothesMateTheme(darkTheme = false) {
                MonthGrid(
                    month = YearMonth.of(2026, 10),
                    today = today,
                    recordsByDate = mapOf(today to listOf(record)),
                    onSelect = {},
                )
            }
        }

        rule.onNodeWithContentDescription("今天，2026年10月26日，有穿着记录")
            .assertHasClickAction()
    }

    @Test
    fun dayCellKeepsPlainDateForEmptyDay() {
        val today = LocalDate.of(2026, 10, 26)
        rule.setContent {
            ClothesMateTheme(darkTheme = false) {
                MonthGrid(
                    month = YearMonth.of(2026, 10),
                    today = today,
                    recordsByDate = emptyMap(),
                    onSelect = {},
                )
            }
        }

        rule.onNodeWithContentDescription("2026年10月25日")
            .assertHasClickAction()
    }

    @Test
    fun donutChartHasAggregateDescription() {
        rule.setContent {
            ClothesMateTheme(darkTheme = false) {
                DonutChart(
                    slices = listOf(CategorySlice(topKey = "top", topName = "上衣", count = 3)),
                    totalItems = 4,
                    onSliceClick = {},
                    modifier = Modifier.size(168.dp),
                )
            }
        }

        rule.onNodeWithContentDescription("类别分布图：上衣 3 件，共 4 件").assertExists()
    }
}
