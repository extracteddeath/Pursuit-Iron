package com.pursuitiron.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Health Connect's "why does this app ask?" link: the privacy policy's Health Connect section, then out of the way. */
class HealthRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.pursuitiron.app.ui.screens.WEB_APP_URL + "privacy.html")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}
