package io.github.extracteddeath.pursuitiron

import android.app.Application

/** Exactly one canonical worker and native writer across Activity recreation. */
class PursuitIronApplication : Application() {
    internal val nativeRuntime by lazy { NativeRuntimeHost(applicationContext) }
    // Separate Kotlin-only lifecycle. No implicit owner enrollment, JS import,
    // screen selection or mutation of the existing canonical store.
    internal val kotlinCandidate by lazy { NativeKotlinCandidateHost(applicationContext) }
    override fun onTerminate() {
        nativeRuntime.close()
        super.onTerminate()
    }
}
