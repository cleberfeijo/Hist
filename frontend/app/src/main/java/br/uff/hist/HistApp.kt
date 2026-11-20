package br.uff.hist

import android.app.Application
import br.uff.hist.core.session.SessionExpiryHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HistApp : Application() {
    @Inject lateinit var sessionExpiryHandler: SessionExpiryHandler

    override fun onCreate() {
        super.onCreate()
        sessionExpiryHandler.start()
    }
}
