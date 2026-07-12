package com.liveisl.app.sign

import android.content.Context

class SignPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("live_isl_settings", Context.MODE_PRIVATE)

    var signOutputMode: SignOutputMode
        get() = SignOutputMode.fromStorage(prefs.getString(KEY_MODE, SignOutputMode.VIDEO.storageKey))
        set(value) {
            prefs.edit().putString(KEY_MODE, value.storageKey).apply()
        }

    var avatarCharacter: AvatarCharacter
        get() = AvatarCharacter.fromStorage(
            prefs.getString(KEY_CHARACTER, AvatarCharacter.ARMATURE.storageKey),
        )
        set(value) {
            prefs.edit().putString(KEY_CHARACTER, value.storageKey).apply()
        }

    var videoSource: VideoSource
        get() = VideoSource.fromStorage(
            prefs.getString(KEY_VIDEO_SOURCE, VideoSource.VIKASOPS.storageKey),
        )
        set(value) {
            prefs.edit().putString(KEY_VIDEO_SOURCE, value.storageKey).apply()
        }

    /** HTTPS zip URL for CISLR pack install. Empty means use [CislrPackDefaults.PACK_ZIP_URL]. */
    var cislrPackUrl: String
        get() = prefs.getString(KEY_CISLR_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CISLR_URL, value.trim()).apply()
        }

    fun resolvedCislrPackUrl(): String =
        cislrPackUrl.ifBlank { CislrPackDefaults.PACK_ZIP_URL }

    companion object {
        private const val KEY_MODE = "sign_output_mode"
        private const val KEY_CHARACTER = "avatar_character"
        private const val KEY_VIDEO_SOURCE = "video_source"
        private const val KEY_CISLR_URL = "cislr_pack_url"
    }
}
