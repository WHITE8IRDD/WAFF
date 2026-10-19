package com.waff.app.core

enum class MediaKind { PHOTO, VIDEO, UNKNOWN }
enum class MediaFilter { ALL, PHOTOS, VIDEOS }

data class MediaItem(
    val id: String,
    val originalPosition: Int,
    val kind: MediaKind,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

/** Selection state shared by platform media pickers. Unknown evidence is never silently called a photo or video. */
data class MediaSelection(
    val items: List<MediaItem>,
    val filter: MediaFilter = MediaFilter.ALL,
    val selectedIds: Set<String> = emptySet()
) {
    init {
        require(items.map(MediaItem::id).distinct().size == items.size) { "Media item IDs must be unique" }
    }

    val visibleItems: List<MediaItem>
        get() = items.sortedBy(MediaItem::originalPosition).filter { item ->
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> item.kind == MediaKind.PHOTO
                MediaFilter.VIDEOS -> item.kind == MediaKind.VIDEO
            }
        }

    val selectedItemsInOriginalOrder: List<MediaItem>
        get() = items.sortedBy(MediaItem::originalPosition).filter { it.id in selectedIds }

    fun withFilter(value: MediaFilter) = copy(filter = value)

    fun toggle(id: String): MediaSelection {
        if (items.none { it.id == id }) return this
        val next = selectedIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        return copy(selectedIds = next)
    }

    fun selectAllVisible(): MediaSelection = copy(selectedIds = selectedIds + visibleItems.map(MediaItem::id))

    fun clearSelection(): MediaSelection = copy(selectedIds = emptySet())

    fun invertVisible(): MediaSelection {
        val next = selectedIds.toMutableSet()
        visibleItems.forEach { if (!next.add(it.id)) next.remove(it.id) }
        return copy(selectedIds = next)
    }
}
