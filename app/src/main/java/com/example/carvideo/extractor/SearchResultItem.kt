package com.example.carvideo.extractor

data class SearchResultItem(
    val title: String,
    val url: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?
)
