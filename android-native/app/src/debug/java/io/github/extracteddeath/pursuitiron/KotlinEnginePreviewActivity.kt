package io.github.extracteddeath.pursuitiron

/**
 * Debug lab retains its isolated app ID and manifest launcher, but shares
 * exactly the same Kotlin-native activity and navigation as the beta.
 * There is no second Compose launcher implementation to drift.
 */
class KotlinEnginePreviewActivity:MainActivity()
