package com.liveisl.app.sign

enum class SignOutputMode(val storageKey: String, val label: String, val description: String) {
    VIDEO(
        storageKey = "video",
        label = "Video signs",
        description = "Play real ISL video clips when available",
    ),
    AVATAR_3D(
        storageKey = "avatar_3d",
        label = "3D avatar",
        description = "Animate a 3D signer model on-device",
    );

    companion object {
        fun fromStorage(key: String?): SignOutputMode =
            entries.firstOrNull { it.storageKey == key } ?: VIDEO
    }
}
