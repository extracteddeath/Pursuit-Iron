package io.github.extracteddeath.pursuitiron

import android.app.Application

/** Exactly one canonical worker and native writer across Activity recreation. */
class PursuitIronApplication : Application() {
    internal val nativeRuntime by lazy { NativeRuntimeHost(applicationContext) }
    override fun onTerminate() {
        nativeRuntime.close()
        super.onTerminate()
    }
}
