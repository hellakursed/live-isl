package com.liveisl.app.sign

/**
 * Which ISL video corpus to play in [SignOutputMode.VIDEO].
 */
enum class VideoSource(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    VIKASOPS(
        storageKey = "vikasops",
        label = "Vikasops ISL",
        description = "Bundled offline pack (~287 everyday signs)",
    ),
    CISLR(
        storageKey = "cislr",
        label = "CISLR",
        description = "Large research corpus (~4.7k words) — install pack on device",
    );

    companion object {
        fun fromStorage(key: String?): VideoSource =
            entries.firstOrNull { it.storageKey == key } ?: VIKASOPS
    }
}
