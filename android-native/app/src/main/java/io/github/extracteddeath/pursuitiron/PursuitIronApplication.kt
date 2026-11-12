package io.github.extracteddeath.pursuitiron

import android.app.Application

/**
 * One process-scoped Kotlin source owner. No JS engine is created or started
 * by the beta launcher; an independent data migration is required for any
 * older legacy history and must never silently overwrite this store.
 */
class PursuitIronApplication:Application() {
    internal val kotlinCandidate by lazy {
        NativeKotlinCandidateHost(applicationContext)
    }
}
