package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.KotlinShortSessionStatus

/**
 * Begin with the full workout. Shorter sessions require deliberate athlete
 * choice and a current reviewed proposal under the native approval fence.
 * The Full chip uses a 45-minute read-only preview ONLY to obtain the
 * original estimate; this fallback never approves or applies a trim.
 */
internal object NativeKotlinTimeBudgetPolicy {
    val initialSelectionMinutes:Int? = null
    private const val FULL_PREVIEW_FALLBACK_MINUTES=45

    fun previewMinutes(selectedMinutes:Int?):Int =
        selectedMinutes ?: FULL_PREVIEW_FALLBACK_MINUTES

    fun reviewMatchesSelection(
        selectedMinutes:Int?,previewedMinutes:Int
    ):Boolean = previewedMinutes==previewMinutes(selectedMinutes)

    fun mayOpen(
        selectedMinutes:Int?,
        reviewStatus:KotlinShortSessionStatus?,
        alreadyIssued:Boolean
    ):Boolean = alreadyIssued || selectedMinutes==null ||
        reviewStatus==KotlinShortSessionStatus.ALREADY_FITS ||
        reviewStatus==KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW
}
