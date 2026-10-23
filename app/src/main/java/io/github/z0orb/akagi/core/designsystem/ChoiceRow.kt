package io.github.z0orb.akagi.core.designsystem

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * A single-choice segmented row. Labels stay on one line, shrinking a little at large font sizes instead of
 * wrapping (which made the selected segment taller than the rest). The selected segment shows in the primary
 * container colour with a primary border rather than with a check mark, which took a third of a narrow segment's
 * width; the border keeps the selection visible at 7:1 against the background, the fill alone is only 2:1 (P7.4).
 */
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = scheme.primaryContainer,
        activeContentColor = scheme.onPrimaryContainer,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = colors,
                border = SegmentedButtonDefaults.borderStroke(if (isSelected) scheme.primary else scheme.outline),
                icon = {},
            ) {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                )
            }
        }
    }
}
