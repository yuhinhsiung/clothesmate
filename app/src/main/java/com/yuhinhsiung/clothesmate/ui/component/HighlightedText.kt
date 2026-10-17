package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow

/**
 * 命中关键词高亮文本（W-07 / UI 文档 §7.2：命中关键词以 `primary` 色高亮）。
 *
 * 多 token（分词 AND）逐个标色；大小写不敏感；不命中时退化为普通文本。
 */
@Composable
fun HighlightedText(
    text: String,
    tokens: List<String>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    /** 基色覆盖（照片蒙层上用 OnPhoto 白字）；默认跟随 style / 主题 */
    baseColor: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, tokens, highlightColor) {
        buildHighlightedString(text, tokens, highlightColor)
    }
    Text(
        text = annotated,
        style = if (baseColor == Color.Unspecified) style else style.copy(color = baseColor),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 纯函数便于直测：把 tokens 在 text 中的命中段包上高亮 span。 */
internal fun buildHighlightedString(
    text: String,
    tokens: List<String>,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    val lowerText = text.lowercase()
    tokens.filter { it.isNotBlank() }.forEach { token ->
        val lowerToken = token.lowercase()
        var startIndex = 0
        while (true) {
            val index = lowerText.indexOf(lowerToken, startIndex)
            if (index < 0) break
            addStyle(SpanStyle(color = color), index, index + token.length)
            startIndex = index + token.length
        }
    }
}
