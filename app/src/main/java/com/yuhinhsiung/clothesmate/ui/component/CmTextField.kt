package com.yuhinhsiung.clothesmate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yuhinhsiung.clothesmate.ui.theme.Dimens
import com.yuhinhsiung.clothesmate.ui.theme.muted
import com.yuhinhsiung.clothesmate.ui.theme.onMuted

/**
 * 文本输入框 —— UI 文档 §6.7：`muted` 底、radius-s、聚焦 `primary` 2dp 描边；
 * **标签常驻上方**（不用占位符代替标签）；错误 = 就近红字 + `error` 描边。
 *
 * review U-g 的落点：原 OutlinedTextField（描边风）与 §6.7 口径不符，新表单统一用本组件，
 * 既有衣柜弹层一并迁移，全 App 输入框只有一种形态。
 */
@Composable
fun CmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = imeAction),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /** 传入时挂在输入框上，供搜索页自动聚焦等场景 */
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** 右侧附加控件（M2-05 密码框的显示/隐藏按钮）；不传则无 */
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Dimens.Radius.s)
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (isError || focused) 2.dp else 1.dp
    // review UI-05：没传 focusRequester 时也备一个，供「点视觉框任意处即聚焦」使用
    val effectiveFocusRequester = focusRequester ?: remember { FocusRequester() }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onMuted,
                modifier = Modifier.padding(bottom = Dimens.Space.s1),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = borderWidth, color = borderColor, shape = shape)
                .background(color = MaterialTheme.colorScheme.muted, shape = shape)
                // review UI-05：视觉框内边距也要能聚焦，而不是只有文本那一小条可点
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { effectiveFocusRequester.requestFocus() },
                )
                .padding(horizontal = Dimens.Space.s3, vertical = Dimens.Space.s3),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = TextStyle(
                    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
                    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // review UI-05：原先没有 fillMaxWidth，编辑节点只有「文本宽度」——
                // 新建衣柜的名称框可编辑区只有 ~114dp，而视觉框有 ~379dp，
                // 右侧大片空白点了没反应，光标定位与读屏焦点框也都对不上。
                //
                // review UI-06：字段名原先是与 BasicTextField 平行的独立 Text，
                // 输入节点本身没有任何名称——有值之后只靠输入节点分不清在编辑哪个属性。
                // 标签、错误、禁用状态直接挂到**可编辑节点**上（而不是靠父级
                // mergeDescendants 合并）：读屏读得到，UI 树里也能逐节点核对。
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(effectiveFocusRequester)
                    .semantics {
                        label?.let { contentDescription = it }
                        if (!enabled) disabled()
                        if (isError && errorMessage != null) error(errorMessage)
                    },
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onMuted,
                                )
                            }
                            innerTextField()
                        }
                        trailingContent?.invoke()
                    }
                },
            )
        }
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Dimens.Space.s1),
            )
        }
    }
}
