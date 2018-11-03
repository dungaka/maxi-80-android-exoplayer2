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
        setContentView(R.layout.activity_about)

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        Log.d(TAG, "%s (%s)".format(versionName, versionCode))

        app_copyright.text = resources.getString(R.string.copyright).format(versionName, versionCode)
    }

    fun showWebsite(view : View) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maxi80.com"))
        startActivity(browserIntent)
    }

    fun showDonate(view : View) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.maxi80.com/paypal.htm"))
        startActivity(browserIntent)
    }

    companion object {
        private const val TAG = "Maxi80_AboutActivity"
        private const val MINIMUM_SDK_FEATURES = 20
    }

}
