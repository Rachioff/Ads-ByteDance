package com.bytedance.ads_bytedance.feed.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bytedance.ads_bytedance.data.model.Tag
import com.bytedance.ads_bytedance.ui.theme.Blue100
import com.bytedance.ads_bytedance.ui.theme.Blue600

/**
 * 标签 Chips 行
 *
 * 使用 FlowRow 自动换行排列标签 Chip。
 * 每个 Chip 为蓝色浅底 + 蓝色文字的圆角矩形，点击触发过滤。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(
    tags: List<Tag>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            TagChip(
                text = tag.name,
                onClick = { onTagClick(tag.name) }
            )
        }
    }
}

/**
 * 单个标签 Chip
 */
@Composable
fun TagChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Blue600,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Blue100)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
