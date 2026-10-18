package com.yuhinhsiung.clothesmate.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全 App 图标集中入口 —— UI 设计文档 §5.1。
 *
 * 设计文档中有 3 个字形在当前图标库中不存在，已按语义最近者替换并在
 * `design-system/clothesmate/pages/icon-mapping-override.md` 落位 override 说明。
 * 全工程统一从这里引用，升级图标库时只改本文件。
 *
 * 风格口径（P0 现状，M1-05 再补充）：所有图标取 **Rounded** 风格；底部导航的
 * 选中/未选中当前**共用同一字形**，仅靠颜色区分（选中 primary / 未选 onMuted）。
 * UI 文档 §5/§6.1 的目标模式是「默认线性 outline、选中 filled」，需引入 Outlined 变体，
 * 已记录在 review C-04，随 M1-05 实装导航时补齐。
 */
object AppIcons {

    // 底部导航（§5.1）
    val Today: ImageVector = Icons.Rounded.WbCloudy
    val Wardrobe: ImageVector = Icons.Rounded.Checkroom
    /** 穿搭：库内无 styler 字形，沿用衣架图形，靠文字标签区分 */
    val Outfit: ImageVector = Icons.Rounded.Checkroom
    val Overview: ImageVector = Icons.Rounded.Monitor

    // 通用操作
    val Search: ImageVector = Icons.Rounded.Search
    val Add: ImageVector = Icons.Rounded.Add
    val PhotoCamera: ImageVector = Icons.Rounded.PhotoCamera
    val Image: ImageVector = Icons.Rounded.Image
    val Calendar: ImageVector = Icons.Rounded.CalendarMonth
    val Location: ImageVector = Icons.Rounded.LocationOn
    val LocationOff: ImageVector = Icons.Rounded.LocationOff
    val ChevronLeft: ImageVector = Icons.Rounded.ChevronLeft
    val ChevronRight: ImageVector = Icons.Rounded.ChevronRight
    val ExpandMore: ImageVector = Icons.Rounded.ExpandMore
    val Check: ImageVector = Icons.Rounded.Check
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val More: ImageVector = Icons.Rounded.MoreVert
    val Edit: ImageVector = Icons.Rounded.Edit
    val Delete: ImageVector = Icons.Rounded.Delete
    val Store: ImageVector = Icons.Rounded.Inventory2 // 收起 / 收纳
    val Filter: ImageVector = Icons.Rounded.FilterList
    val Refresh: ImageVector = Icons.Rounded.Refresh
    val CheckCircle: ImageVector = Icons.Rounded.CheckCircle
    val Ai: ImageVector = Icons.Rounded.AutoAwesome // ✨ AI 标识，配 accent 琥珀金
    val Tag: ImageVector = Icons.Rounded.Sell
    val Settings: ImageVector = Icons.Rounded.Settings
    val Close: ImageVector = Icons.Rounded.Close
    val Flash: ImageVector = Icons.Rounded.FlashOn
    val CameraSwitch: ImageVector = Icons.Rounded.Cameraswitch

    // 设置页密码框显示/隐藏（M2-05）；UI 文档 §5.1 未列，按 Material Symbols Rounded 同风格补齐
    val Visibility: ImageVector = Icons.Rounded.Visibility
    val VisibilityOff: ImageVector = Icons.Rounded.VisibilityOff

    // 天气（卡片与日历共用）
    val WeatherSunny: ImageVector = Icons.Rounded.WbSunny
    val WeatherCloudy: ImageVector = Icons.Rounded.WbCloudy
    val WeatherRainy: ImageVector = Icons.Rounded.Umbrella
    val WeatherSnowy: ImageVector = Icons.Rounded.AcUnit
}
