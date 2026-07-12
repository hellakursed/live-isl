package com.liveisl.app

import android.app.Application
import com.liveisl.app.bootstrap.ModelBootstrap
import com.liveisl.app.data.GlossDictionary

class LiveIslApplication : Application() {
    lateinit var dictionary: GlossDictionary
        private set
    lateinit var modelBootstrap: ModelBootstrap
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val prefs = com.liveisl.app.sign.SignPreferences(this)
        dictionary = GlossDictionary(this).also { it.load(prefs.videoSource) }
        modelBootstrap = ModelBootstrap(this).also { it.ensureModels() }
    }

    companion object {
        lateinit var instance: LiveIslApplication
            private set
    }
}
