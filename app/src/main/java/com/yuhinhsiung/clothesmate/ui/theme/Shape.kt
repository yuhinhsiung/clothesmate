package com.yuhinhsiung.clothesmate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角体系 —— UI 设计文档 §4.1「柔和是本案形状语言」。
 */
val ClothesMateShapes = Shapes(
    // radius-s 8dp —— 小标签 chip、输入框
    extraSmall = RoundedCornerShape(Dimens.Radius.s),
    // radius-m 16dp —— 按钮、小卡片（衣物网格卡）
    small = RoundedCornerShape(Dimens.Radius.m),
    // radius-l 20dp —— 大卡片（穿搭卡、衣柜卡、弹层）
    medium = RoundedCornerShape(Dimens.Radius.l),
    // radius-xl 28dp —— 底部弹窗、大容器
    large = RoundedCornerShape(Dimens.Radius.xl),
    extraLarge = RoundedCornerShape(Dimens.Radius.xl),
)

/** 50% 全圆角 —— FAB、头像、胶囊筛选条 */
val FullCornerShape = RoundedCornerShape(percent = 50)
