package com.stormacq.android.maxi80

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
//import kotlinx.android.synthetic.main.activity_about.*
import android.content.Intent
import android.net.Uri
import kotlinx.android.synthetic.main.activity_about.*


class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val app = application as Maxi80Application

        setContentView(R.layout.activity_about)

        val versionName = "v2.1.4"
        val versionCode = "20210708"

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
