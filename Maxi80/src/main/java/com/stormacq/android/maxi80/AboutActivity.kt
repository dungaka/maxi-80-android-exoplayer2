package com.stormacq.android.maxi80

import android.os.Bundle
import android.support.v7.app.AppCompatActivity;
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_about.*
import android.content.Intent
import android.net.Uri


class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val app = application as Maxi80Application

        setContentView(R.layout.activity_about)

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        Log.d(TAG, "%s (%s)".format(versionName, versionCode))

        app_copyright.text = resources.getString(R.string.copyright).format(app.station.name(), versionName, versionCode)
    }

    fun showWebsite(@Suppress("UNUSED_PARAMETER") view : View) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.website_url)))
        startActivity(browserIntent)
    }

    fun showDonate(@Suppress("UNUSED_PARAMETER") view : View) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.donation_url)))
        startActivity(browserIntent)
    }

    companion object {
        private const val TAG = "Maxi80_AboutActivity"
    }

}
