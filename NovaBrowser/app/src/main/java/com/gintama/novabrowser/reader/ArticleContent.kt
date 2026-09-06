package com.gintama.novabrowser.reader

import java.io.Serializable

data class ArticleContent(
    val title: String,
    val byline: String,
    val siteName: String,
    val originalUrl: String,
    val contentHtml: String,
    val wordCount: Int,
    val readTimeMinutes: Int
) : Serializable
