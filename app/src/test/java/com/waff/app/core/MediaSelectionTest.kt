package com.waff.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSelectionTest {
    private val items = listOf(
        MediaItem("v2", 2, MediaKind.VIDEO),
        MediaItem("p1", 1, MediaKind.PHOTO),
        MediaItem("unknown", 3, MediaKind.UNKNOWN),
        MediaItem("p0", 0, MediaKind.PHOTO)
    )

    @Test fun selectionsSurviveFilterSwitchAndDownloadsKeepCarouselOrder() {
        val state = MediaSelection(items)
            .toggle("p1")
            .withFilter(MediaFilter.VIDEOS)
            .toggle("v2")
            .withFilter(MediaFilter.ALL)
        assertEquals(setOf("p1", "v2"), state.selectedIds)
        assertEquals(listOf("p1", "v2"), state.selectedItemsInOriginalOrder.map(MediaItem::id))
    }

    @Test fun selectAllAndInvertAffectOnlyVisibleItems() {
        val selected = MediaSelection(items, MediaFilter.PHOTOS)
            .selectAllVisible()
            .withFilter(MediaFilter.VIDEOS)
            .invertVisible()
        assertEquals(setOf("v2", "p1", "p0"), selected.selectedIds)
        assertEquals(listOf("v2"), selected.visibleItems.filter { it.id in selected.selectedIds }.map(MediaItem::id))
    }

    @Test fun unknownMediaDoesNotAppearInPhotoOrVideoFilter() {
        val state = MediaSelection(items, MediaFilter.PHOTOS)
        assertEquals(listOf("p0", "p1"), state.visibleItems.map(MediaItem::id))
        assertEquals(4, state.withFilter(MediaFilter.ALL).visibleItems.size)
    }
}
