package com.liveisl.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.liveisl.app.sign.VideoSource
import java.io.File
import java.io.InputStreamReader

data class GlossEntry(
    @SerializedName("glossId") val glossId: String,
    @SerializedName("lemmas") val lemmas: List<String>,
    @SerializedName("videoPath") val videoPath: String?,
    @SerializedName("durationMs") val durationMs: Long = 800,
    @SerializedName("hindi") val hindi: List<String> = emptyList(),
)

data class Gloss(
    val glossId: String,
    val displayLabel: String,
    val videoAssetPath: String?,
    val durationMs: Long,
    val isFingerspell: Boolean = false,
    val sourceLemma: String = "",
)

class GlossDictionary(private val context: Context) {
    private val lemmaToEntry = LinkedHashMap<String, GlossEntry>()
    private val glossById = LinkedHashMap<String, GlossEntry>()
    private val gson = Gson()

    @Volatile
    var loaded: Boolean = false
        private set

    @Volatile
    var activeSource: VideoSource = VideoSource.VIKASOPS
        private set

    fun load(source: VideoSource = VideoSource.VIKASOPS) {
        val json = readDictionaryJson(source) ?: run {
            // CISLR not installed → empty dictionary (UI shows pack status)
            lemmaToEntry.clear()
            glossById.clear()
            activeSource = source
            loaded = true
            return
        }
        val type = object : TypeToken<List<GlossEntry>>() {}.type
        val entries: List<GlossEntry> = gson.fromJson(json, type) ?: emptyList()
        lemmaToEntry.clear()
        glossById.clear()
        for (entry in entries) {
            val normalized = normalizeEntry(entry, source)
            glossById[normalized.glossId.lowercase()] = normalized
            for (lemma in normalized.lemmas) {
                lemmaToEntry[lemma.lowercase()] = normalized
            }
            for (h in normalized.hindi) {
                lemmaToEntry[h.lowercase()] = normalized
            }
        }
        activeSource = source
        loaded = true
    }

    fun reload(source: VideoSource) {
        loaded = false
        load(source)
    }

    private fun readDictionaryJson(source: VideoSource): String? {
        when (source) {
            VideoSource.VIKASOPS -> {
                val asset = SignClipPaths.assetDictionaryPath(source) ?: return null
                return context.assets.open(asset).use { InputStreamReader(it).readText() }
            }
            VideoSource.CISLR -> {
                val onDevice = SignClipPaths.dictionaryFile(context, source)
                if (onDevice.exists() && onDevice.length() > 10) {
                    return onDevice.readText()
                }
                // Optional bundled seed (usually empty / stub)
                val asset = SignClipPaths.assetDictionaryPath(source) ?: return null
                return try {
                    context.assets.open(asset).use { InputStreamReader(it).readText() }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Ensure CISLR videoPath points at filesDir-relative names the renderer understands.
     * Entries may use `videos/FOO.mp4` (same shape as vikasops) or bare `FOO.mp4`.
     */
    private fun normalizeEntry(entry: GlossEntry, source: VideoSource): GlossEntry {
        if (source != VideoSource.CISLR) return entry
        val raw = entry.videoPath ?: return entry
        val fileName = File(raw).name
        return entry.copy(videoPath = "cislr/videos/$fileName")
    }

    fun lookup(lemma: String): GlossEntry? = lemmaToEntry[lemma.lowercase()]

    fun getByGlossId(glossId: String): GlossEntry? = glossById[glossId.lowercase()]

    fun size(): Int = glossById.size

    fun allGlossIds(): List<String> = glossById.keys.toList()

    fun suggestedSpokenWords(): List<String> {
        val preferred = listOf(
            "hello", "how", "you", "I", "help", "need", "love", "friend", "home",
            "drink", "food", "go", "see", "school", "work", "money", "India",
            "sorry", "yes", "no", "good", "happy", "time", "today", "phone",
        )
        val fromPreferred = preferred.filter { lookup(it.lowercase())?.videoPath != null }
        if (fromPreferred.isNotEmpty()) return fromPreferred
        // CISLR / custom packs often lack the English demo lexicon — surface real lemmas.
        return lemmaToEntry.entries
            .asSequence()
            .filter { (_, entry) -> !entry.videoPath.isNullOrBlank() }
            .map { it.key }
            .filter { it.length in 2..24 && !it.startsWith("#") && ' ' !in it }
            .distinct()
            .take(24)
            .toList()
    }

    /** Short demo phrase using lemmas that actually have clips in the active dictionary. */
    fun demoPhraseWords(): List<String> {
        val candidates = listOf(
            listOf("I", "need", "help"),
            listOf("how", "you"),
            listOf("I", "go", "home"),
            listOf("yes", "good"),
        )
        for (phrase in candidates) {
            if (phrase.all { lookup(it.lowercase())?.videoPath != null }) return phrase
        }
        return suggestedSpokenWords().take(3)
    }
}
