package com.liveisl.app.bootstrap

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Ensures on-device ASR model directory layout. Models can be:
 * 1) Pre-shipped under assets/asr/ and unpacked once
 * 2) Dropped manually into filesDir/asr for Sherpa-ONNX
 *
 * Expected Sherpa layout (filesDir/asr):
 *   tokens.txt, encoder.onnx, decoder.onnx, joiner.onnx  (or model.onnx)
 */
class ModelBootstrap(private val context: Context) {
    data class Status(
        val asrReady: Boolean,
        val asrEngineHint: String,
        val dictionaryReady: Boolean,
        val message: String,
        val unpackProgress: Float = 1f,
    )

    @Volatile
    var status: Status = Status(
        asrReady = false,
        asrEngineHint = "checking",
        dictionaryReady = false,
        message = "Initializing…",
        unpackProgress = 0f,
    )
        private set

    fun ensureModels(): Status {
        val asrDir = File(context.filesDir, "asr")
        asrDir.mkdirs()

        // Unpack any packaged placeholder / config from assets
        try {
            val assetList = context.assets.list("asr").orEmpty()
            for (name in assetList) {
                val out = File(asrDir, name)
                if (!out.exists()) {
                    context.assets.open("asr/$name").use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ASR asset unpack: ${e.message}")
        }

        val sherpaReady = File(asrDir, "tokens.txt").exists() &&
            (File(asrDir, "encoder.onnx").exists() || File(asrDir, "model.onnx").exists())

        val dictOk = try {
            context.assets.open("dictionary/glosses.json").close()
            true
        } catch (_: Exception) {
            false
        }

        status = if (sherpaReady) {
            Status(
                asrReady = true,
                asrEngineHint = "sherpa",
                dictionaryReady = dictOk,
                message = "Sherpa-ONNX models ready (offline)",
                unpackProgress = 1f,
            )
        } else {
            Status(
                asrReady = true, // Android speech still usable
                asrEngineHint = "android-speech",
                dictionaryReady = dictOk,
                message = "Sherpa models not installed — using device speech engine (prefer offline packs)",
                unpackProgress = 1f,
            )
        }
        return status
    }

    companion object {
        private const val TAG = "ModelBootstrap"
    }
}
