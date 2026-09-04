package com.gintama.novabrowser.core.model

/**
 * Data models matching the SQLite schema specified in DESIGN.md
 */

data class HistoryItem(
    val id: Long = 0,
    val url: String,
    val title: String?,
    val domain: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val summary: String? = null,
    val embedding: ByteArray? = null,
    val extractedTextMeta: String? = null
)

data class BookmarkItem(
    val id: Long = 0,
    val url: String,
    val title: String?,
    val folder: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class TabSession(
    val id: Long = 0,
    val tabId: String,
    val url: String?,
    val title: String?,
    val isPrivate: Boolean = false,
    val lastActiveAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING,
    SAFE,
    QUARANTINED,
    BLOCKED,
    COMPLETED
}

data class DownloadItem(
    val id: Long = 0,
    val url: String,
    val filename: String?,
    val mimeType: String?,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val riskReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RuleType {
    DOMAIN,
    URL,
    TRACKER,
    MALWARE
}

enum class RuleSeverity {
    BLOCK,
    WARN,
    INFO
}

data class SecurityRule(
    val id: Long = 0,
    val ruleType: RuleType,
    val pattern: String,
    val source: String, // 'URLHAUS', 'EASYLIST', 'EASYPRIVACY', 'LOCAL_HEURISTIC'
    val severity: RuleSeverity = RuleSeverity.BLOCK,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SnapshotMeta(
    val feedSource: String,
    val lastUpdatedAt: Long,
    val ruleCount: Int
)
