package com.stormacq.android.maxi80

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import com.amazonaws.amplify.generated.graphql.ArtworkQuery
import kotlinx.android.synthetic.main.activity_main.*
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.squareup.picasso.Picasso

/**
 * Maxi80 Main Activity
 */
class MainActivity : AppCompatActivity(), MetaDataListener {


    private lateinit var contentObserver: VolumeContentObserver

    /**************************************************************************
     *
     *  Application Lifecycle Management
     *
     *************************************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // register ourself to receive track change notifications
        val app = application as Maxi80Application
        app.metaDataChangedListener = this

        setContentView(R.layout.activity_main)

        play_pause.setOnClickListener {
            if (app.isPlaying) {
                stop()
            } else {
                play()
            }
        }

        prepareSeekBar()

        if (Build.VERSION.SDK_INT <= Maxi80Application.MINIMUM_SDK_FEATURES) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        //play() will be called when we will receive the StationQuery callback => onCurrentTrackChanged
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        val app = application as Maxi80Application

        // force update the start / stop button
        if (app.isPlaying) {
            setStopIcon()
        } else {
            setStartIcon()
        }

        // force update the artwork
        if (app.isPlaying) {
            onCurrentTrackChanged(app.currentArtist, app.currentTrack)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        applicationContext.contentResolver.unregisterContentObserver(contentObserver)
    }


    private fun prepareSeekBar() {

        volumeDown.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 24) {
                volumeBar.setProgress(volumeBar.progress - 1, true)
            } else {
                volumeBar.progress = volumeBar.progress - 1
            }
        }
        volumeUp.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 24) {
                volumeBar.setProgress(volumeBar.progress + 1, true)
            } else {
                volumeBar.progress = volumeBar.progress + 1
            }
        }

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            // to fully implement the abstract class
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                Log.d(TAG, "volume bar changed, progress = (%d).  From user = (%b)".format(progress, fromUser))
                if (fromUser) {
                    val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
        })

        val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeBar.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (Build.VERSION.SDK_INT >= 26) {
            volumeBar.min = 0
        }

        // initialise with current volume
        val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d(TAG, "initial volume reported to be (%d)".format(currentVolume))
        volumeBar.progress = currentVolume

        // capture system volume changes and report them back to our volume bar
        // https://stackoverflow.com/questions/6896746/is-there-a-broadcast-action-for-volume-changes
        contentObserver = VolumeContentObserver(Handler())
        applicationContext.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                contentObserver )
    }


    override fun onCurrentTrackChanged(artist: String, track: String) {

        val app = application as Maxi80Application

        // avoid refreshing at app start, when we receive radio station data
        if (app.isPlaying) {
            this@MainActivity.runOnUiThread {
                this.artist.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in))
                this.track.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in))
                this.artist.text = app.currentArtist
                this.track.text = app.currentTrack
                loadArtwork()
            }
        } else {
            // when we receive meta data and the app is not playing, it means we just receive the radio name
            // let's play
            // this is made possible because we change the isPlaying flag as soon as the player is buffering.
            play()
        }
    }

    private fun loadArtwork() {
        val app = application as Maxi80Application

        Log.d(TAG,"Loading artwork for current artist (%s) and track (%s)".format(app.currentArtist, app.currentTrack))
        app.appSyncClient.query(ArtworkQuery.builder()
                                        .artist(app.currentArtist)
                                        .track(app.currentTrack)
                                        .build())
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ArtworkQuery.Data>() {
                    override fun onResponse(response: Response<ArtworkQuery.Data>) {
                        this@MainActivity.runOnUiThread {
                            Log.d(TAG, "ArtworkQuery returned : " + response.data().toString())
                            val url = response.data()?.artwork()?.url()
                            if (url != null) {
                                Picasso.get().load(url).into(cover)
                            } else {
                               Picasso.get().load(R.drawable.nocover_400x400).into(cover)
                            }
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        this@MainActivity.runOnUiThread {
                            Log.e(TAG, "Failed to perform ArtworkQuery", e)
                            Picasso.get().load(R.drawable.nocover_400x400).into(cover)
                        }
                    }
                })

    }

    override fun onError(e: Exception) {

        var s = "unknown error"

        if (e is ApolloException) {
            s = resources.getString(R.string.error_no_network)
        }
        Snackbar.make(window.decorView.rootView,
                s,
                android.R.attr.duration)
                .show()
    }

    /**************************************************************************
     *
     *  Stream Control
     *
     *************************************************************************/

    private fun setStopIcon() {

        // not always necessary, but just to be sure
        this@MainActivity.runOnUiThread {

            if (Build.VERSION.SDK_INT <= Maxi80Application.MINIMUM_SDK_FEATURES) {
                play_pause.setImageResource((R.drawable.ic_stop_black_24dp))
            } else {
                play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_stop_black_24dp, null))
            }
        }
    }

    private fun setStartIcon() {

        // not always necessary, but just to be sure
        this@MainActivity.runOnUiThread {

            if (Build.VERSION.SDK_INT <= Maxi80Application.MINIMUM_SDK_FEATURES) {
                play_pause.setImageResource((R.drawable.ic_play_arrow_black_24dp))
            } else {
                play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_play_arrow_black_24dp, null))
            }
        }
    }

    private fun play() {
        Log.d(TAG, "Play")

        setStopIcon()

        // as per https://developer.android.com/about/versions/oreo/background
        if (Build.VERSION.SDK_INT >= 26) {
            Intent(this, StreamingService::class.java).also { intent ->
                startForegroundService(intent)
            }

        } else {
            Intent(this, StreamingService::class.java).also { intent ->
                startService(intent)
            }
        }
    }

    private fun stop() {
        Log.d(TAG, "Stop")

        setStartIcon()

        Intent(this, StreamingService::class.java).also { intent ->
            stopService(intent)
        }

        val app = application as Maxi80Application
        app.setTrack(null, null )
    }

    companion object {
        private const val TAG = "Maxi80_MainActivity"
    }

    /**************************************************************************
     *
     *  UI Button Handler Control
     *
     *************************************************************************/


    fun showAbout(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    fun showShare(@Suppress("UNUSED_PARAMETER") view: View) {
        val app = application as Maxi80Application

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL, resources.getString(R.string.share_to))
        intent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.share_subject))
        intent.putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_text).format(app.currentTrack, app.currentArtist))

        startActivity(Intent.createChooser(intent, resources.getString(R.string.share_title)))
    }


    /**************************************************************************
     *
     *  Sync volume bar with device's volume button
     *
     *************************************************************************/

    private inner class VolumeContentObserver(handler : Handler) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeBar.progress = currentVolume
            Log.d(TAG, "Volume changed to $currentVolume")
        }
    }

}
