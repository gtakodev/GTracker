package com.devtrack.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * State holder for drag-and-drop list reordering (P4.1).
 *
 * @param onMove Called when two items swap position during drag.
 * @param onDragEnd Called when the drag gesture completes with the final ordered list.
 */
class DragDropListState<T>(
    private val lazyListState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDragEnd: () -> Unit,
) {
    /** Index of the item currently being dragged (null if not dragging). */
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    /** Current vertical offset of the dragged item from its original position. */
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** Index the dragged item is currently hovering over (for drop-zone highlighting). */
    var targetIndex by mutableStateOf<Int?>(null)
        private set

    private var initialDragIndex: Int? = null
    private var overscrollJob: Job? = null

    /**
     * Begin dragging at the item that contains the given vertical offset.
     */
    fun onDragStart(offsetY: Float) {
        val info = findItemAtOffset(offsetY) ?: return
        initialDragIndex = info.index
        draggedIndex = info.index
        targetIndex = info.index
        dragOffset = 0f
    }

    /**
     * Update drag position.
     */
    fun onDrag(change: Float, scope: kotlinx.coroutines.CoroutineScope) {
        dragOffset += change
        val dragged = draggedIndex ?: return

        // Determine where the center of the dragged item is now
        val draggedInfo = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == dragged } ?: return

        val draggedCenter = draggedInfo.offset + draggedInfo.size / 2 + dragOffset.toInt()

        // Find which item the center overlaps with
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                draggedCenter in item.offset..(item.offset + item.size)
            }

        if (targetItem != null && targetItem.index != dragged) {
            targetIndex = targetItem.index
            onMove(dragged, targetItem.index)
            draggedIndex = targetItem.index
            // Reset offset partially to keep it smooth
            dragOffset += (draggedInfo.offset - targetItem.offset)
        } else {
            targetIndex = dragged
        }

        // Auto-scroll when near edges
        handleAutoScroll(draggedCenter, scope)
    }

    /**
     * End the drag gesture.
     */
    fun onDragEnd() {
        overscrollJob?.cancel()
        overscrollJob = null
        draggedIndex = null
        dragOffset = 0f
        targetIndex = null
        initialDragIndex = null
        onDragEnd.invoke()
    }

    /**
     * Cancel drag (e.g., on gesture cancel).
     */
    fun onDragCancel() {
        onDragEnd()
    }

    private fun findItemAtOffset(offsetY: Float): LazyListItemInfo? {
        val viewportOffset = offsetY.toInt() + lazyListState.layoutInfo.viewportStartOffset
        return lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                viewportOffset in item.offset..(item.offset + item.size)
            }
    }

    private fun handleAutoScroll(draggedCenter: Int, scope: kotlinx.coroutines.CoroutineScope) {
        val viewportStart = lazyListState.layoutInfo.viewportStartOffset
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset
        val scrollThreshold = 80

        overscrollJob?.cancel()
        overscrollJob = when {
            draggedCenter < viewportStart + scrollThreshold -> {
                scope.launch {
                    lazyListState.scrollToItem(
                        (lazyListState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                    )
                }
            }
            draggedCenter > viewportEnd - scrollThreshold -> {
                scope.launch {
                    lazyListState.scrollToItem(
                        (lazyListState.firstVisibleItemIndex + 1)
                            .coerceAtMost(lazyListState.layoutInfo.totalItemsCount - 1)
                    )
                }
            }
            else -> null
        }
    }
}

/**
 * Remember a [DragDropListState] scoped to the composition.
 */
@Composable
fun <T> rememberDragDropListState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
): Pair<DragDropListState<T>, LazyListState> {
    val state = remember(lazyListState) {
        DragDropListState<T>(
            lazyListState = lazyListState,
            onMove = onMove,
            onDragEnd = onDragEnd,
        )
    }
    return state to lazyListState
}

/**
 * A LazyColumn with drag-and-drop reordering support (P4.1.2, P4.1.3).
 *
 * Items can be long-pressed and dragged to reorder. Visual feedback includes:
 * - Elevated shadow on the dragged item (ghost element)
 * - Drop zone highlighting on the target position
 * - Smooth animation for repositioning
 *
 * @param items The list of items to display.
 * @param key Unique key for each item.
 * @param onMove Called when items swap positions during drag.
 * @param onDragEnd Called after the drag completes.
 * @param modifier Modifier for the LazyColumn.
 * @param headerContent Optional content to display before the draggable items.
 * @param footerContent Optional content to display after the draggable items.
 * @param itemContent Composable for each item, receives item, index, and isDragging flag.
 */
@Composable
fun <T> DragDropLazyColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    headerContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (item: T, index: Int, isDragging: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val (dragState, _) = rememberDragDropListState<T>(
        lazyListState = lazyListState,
        onMove = onMove,
        onDragEnd = onDragEnd,
    )

    // The header offset for item index mapping
    val headerOffset = if (headerContent != null) 1 else 0

    LazyColumn(
        modifier = modifier
            .pointerInput(items.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // Adjust for header item offset
                        dragState.onDragStart(offset.y)
                        // Compensate index for header
                        val dragged = dragState.draggedIndex
                        if (dragged != null && dragged < headerOffset) {
                            dragState.onDragCancel()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragState.onDrag(dragAmount.y, scope)
                    },
                    onDragEnd = {
                        dragState.onDragEnd()
                    },
                    onDragCancel = {
                        dragState.onDragCancel()
                    },
                )
            },
        state = lazyListState,
        verticalArrangement = verticalArrangement,
    ) {
        // Header
        if (headerContent != null) {
            item { headerContent() }
        }

        // Draggable items
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val actualIndex = index + headerOffset
            val isDragging = dragState.draggedIndex == actualIndex
            val isTarget = dragState.targetIndex == actualIndex && !isDragging && dragState.draggedIndex != null

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 0.dp,
                label = "dragElevation",
            )

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragState.dragOffset else 0f
                    }
                    .then(
                        if (isDragging) {
                            Modifier.shadow(elevation, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (isTarget) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp),
                            )
                        } else {
                            Modifier
                        }
                    ),
            ) {
                itemContent(item, index, isDragging)
            }
        }

        // Footer
        if (footerContent != null) {
            item { footerContent() }
        }
    }
}
