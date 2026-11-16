package com.example.pardadns.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A layout that collapses a header from [expandedHeight] to [collapsedHeight]
 * based on nested scroll events from the [content].
 *
 * - Scrolling the list down collapses the header first, then scrolls the list.
 * - Scrolling back up scrolls the list first; the header only expands once the
 *   list is back at the top. Any scroll left after full expansion is passed to
 *   the parent, so a pull-to-refresh wrapping this layout only triggers then.
 * - Snaps to expanded or collapsed state when the gesture ends (threshold = 40%).
 *
 * @param collapseProgress Emits 0f..1f indicating how collapsed the header is.
 */
@Composable
fun CollapsibleShieldLayout(
    expandedHeight: Dp,
    collapsedHeight: Dp,
    modifier: Modifier = Modifier,
    header: @Composable (collapseProgress: Float) -> Unit,
    content: @Composable BoxScope.(topPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { (expandedHeight - collapsedHeight).toPx() }
    // Updated synchronously from scroll callbacks so consecutive events in the
    // same frame always see the latest value.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember(maxOffsetPx) {
        object : NestedScrollConnection {
            private var snapJob: Job? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // A new gesture interrupts any running snap animation
                snapJob?.cancel()
                val delta = available.y
                if (delta >= 0f) return Offset.Zero
                // Scrolling the list down → collapse header before the list moves
                val newOffset = (offsetPx - delta).coerceAtMost(maxOffsetPx)
                val consumed = newOffset - offsetPx
                offsetPx = newOffset
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta <= 0f) return Offset.Zero
                // Scrolling up with the list already at the top → expand header.
                // Whatever is left over goes to the parent (pull-to-refresh).
                val newOffset = (offsetPx - delta).coerceAtLeast(0f)
                val consumedY = offsetPx - newOffset
                offsetPx = newOffset
                return Offset(0f, consumedY)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Snap to nearest state if partially collapsed
                if (offsetPx > 0f && offsetPx < maxOffsetPx) {
                    val target = if (offsetPx / maxOffsetPx > 0.4f) maxOffsetPx else 0f
                    snapJob = scope.launch {
                        animate(
                            initialValue = offsetPx,
                            targetValue = target,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> offsetPx = value }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val collapseProgress = if (maxOffsetPx > 0f) (offsetPx / maxOffsetPx).coerceIn(0f, 1f) else 0f
    val currentHeaderHeight = expandedHeight - with(density) { offsetPx.toDp() }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        // Header — pinned at top, shrinks
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeaderHeight),
        ) {
            header(collapseProgress)
        }
        // Content — fills the space below the header so the list's bottom isn't clipped
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = currentHeaderHeight),
        ) {
            content(currentHeaderHeight)
        }
    }
}
