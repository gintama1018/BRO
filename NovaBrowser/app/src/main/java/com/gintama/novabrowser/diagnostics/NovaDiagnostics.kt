package com.gintama.novabrowser.diagnostics

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic event categories for NovaBrowser runtime observability.
 */
enum class DiagnosticType {
    RENDERER_CRASH,
    SECURITY_BLOCK,
    DOWNLOAD_FAILURE,
    FEED_UPDATE_FAILURE,
    SSL_ERROR,
    DATABASE_MIGRATION
}

/**
 * Privacy-preserving, in-memory diagnostic telemetry entry.
 * Strictly forbids storing full URLs, query parameters, tokens, or personal identifiers.
 */
data class DiagnosticEvent(
    val id: Long,
    val timestamp: Long,
    val type: DiagnosticType,
    val domainOrScheme: String,
    val details: String
)

/**
 * NovaDiagnostics: In-memory ring buffer (capacity 100) for operational observability.
 * No data is persisted to disk, ensuring zero private browsing leaks.
 */
object NovaDiagnostics {

    private const val MAX_CAPACITY = 100
    private val eventQueue = ConcurrentLinkedDeque<DiagnosticEvent>()
    private val counter = AtomicLong(0)

    /**
     * Records a diagnostic event into the bounded ring-buffer.
     * Extracts and normalizes only the host domain or protocol scheme.
     */
    fun log(type: DiagnosticType, rawTarget: String, details: String) {
        val sanitizedOrigin = sanitizeToOriginOrScheme(rawTarget)
        val event = DiagnosticEvent(
            id = counter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            type = type,
            domainOrScheme = sanitizedOrigin,
            details = details.take(256)
        )

        eventQueue.addLast(event)
        while (eventQueue.size > MAX_CAPACITY) {
            eventQueue.pollFirst()
        }
    }

    fun getRecentEvents(): List<DiagnosticEvent> {
        return eventQueue.toList()
    }

    fun clear() {
        eventQueue.clear()
    }

    private fun sanitizeToOriginOrScheme(input: String): String {
        val trimmed = input.trim()
        return try {
            if (trimmed.contains("://")) {
                val uri = java.net.URI(trimmed)
                val scheme = uri.scheme ?: "unknown"
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    "$scheme://$host"
                } else {
                    "$scheme://"
                }
            } else {
                trimmed.substringBefore("/").substringBefore("?").take(64)
            }
        } catch (e: Exception) {
            trimmed.substringBefore(":").take(32)
        }
    }
}
