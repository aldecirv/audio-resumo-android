package com.audioresumo.app

import android.app.Application
import com.audioresumo.app.data.Store
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AudioResumoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        Store.init(this)
    }
}
