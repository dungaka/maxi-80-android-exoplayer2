package com.stormacq.android.maxi80

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity;
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import okhttp3.OkHttpClient
import saschpe.exoplayer2.ext.icy.IcyHttpDataSourceFactory

/**
 * Test application, doesn't necessarily show the best way to do things.
 */
class MainActivity : AppCompatActivity() {
    private var exoPlayer: SimpleExoPlayer? = null
    private val exoPlayerEventListener = ExoPlayerEventListener()
    private lateinit var userAgent: String
    private var isPlaying = false

    private var currentArtist : String = ""
    private var currentTrack : String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userAgent = Util.getUserAgent(applicationContext, applicationContext.getString(R.string.app_name))

        play_pause.setOnClickListener {
            if (isPlaying) {
                stop()
            } else {
                play()
            }
        }

        prepareSeekBar()
        play()
    }

    private fun prepareSeekBar() {

        volumeDown.setOnClickListener {
            volumeBar.setProgress(volumeBar.progress - 1, true)
        }
        volumeUp.setOnClickListener {
            volumeBar.setProgress(volumeBar.progress + 1, true)
        }

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // TODO Auto-generated method stub

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // TODO Auto-generated method stub

            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
        })

        val am = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val MAX_VOLUME = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeBar.max = MAX_VOLUME
        if (Build.VERSION.SDK_INT >= 26) {
            volumeBar.min = 0
        }
        volumeBar.progress = (MAX_VOLUME + 0) / 2
    }

    private fun updateTitle(artist : String, track : String) {
        this@MainActivity.runOnUiThread {
            this.artist.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in));
            this.track.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in));
            this.artist.text = artist
            currentArtist = artist
            this.track.text = track
            currentTrack = track
        }

    }

    private fun handleMetaData(metadata : String) {
        var data = metadata.split(" - ")
        Log.d(TAG, data.toString())
        if (data.size < 2) {
            data = metadata.split("-") // try without space
            Log.d(TAG, data.toString())
            if (data.size < 2) {
                updateTitle(resources.getString(R.string.app_name), metadata)
            } else {
                updateTitle(data[0], data[1])
            }
        } else {
            updateTitle(data[0], data[1])
        }

    }

    private fun play() {
        play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_stop_black_24dp, null))

        GlobalScope.async(Dispatchers.Default, CoroutineStart.DEFAULT, null, {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayerFactory.newSimpleInstance(applicationContext,
                        DefaultRenderersFactory(applicationContext),
                        DefaultTrackSelector(),
                        DefaultLoadControl()
                )
                exoPlayer?.addListener(exoPlayerEventListener)
            }

            val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
            exoPlayer?.audioAttributes = audioAttributes

            // Custom HTTP data source factory which requests Icy metadata and parses it if
            // the stream server supports it
            val client = OkHttpClient.Builder().build()
            val icyHttpDataSourceFactory = IcyHttpDataSourceFactory.Builder(client)
                    .setUserAgent(userAgent)
                    .setIcyHeadersListener { icyHeaders ->
                        Log.d(TAG, "onIcyMetaData: icyHeaders=$icyHeaders")
                    }
                    .setIcyMetadataChangeListener { icyMetadata ->
                        Log.d(TAG, "onIcyMetaData: icyMetadata=$icyMetadata")
                        handleMetaData(icyMetadata.streamTitle)
                    }
                    .build()

            // Produces DataSource instances through which media data is loaded
            val dataSourceFactory = DefaultDataSourceFactory(
                    applicationContext, null, icyHttpDataSourceFactory
            )
            // Produces Extractor instances for parsing the media data
            val extractorsFactory = DefaultExtractorsFactory()

            // The MediaSource represents the media to be played
            val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
                    .setExtractorsFactory(extractorsFactory)
                    .createMediaSource(Uri.parse(DEFAULT_STREAM))

            // Prepares media to play (happens on background thread) and triggers
            // {@code onPlayerStateChanged} callback when the stream is ready to play
            exoPlayer?.prepare(mediaSource)
            exoPlayer?.playWhenReady = true
        })
    }

    private fun stop() {
        play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_play_arrow_black_24dp, null))
        releaseResources(true)
        isPlaying = false
        updateTitle(resources.getString(R.string.app_name), resources.getString(R.string.app_description))
    }

    private fun releaseResources(releasePlayer: Boolean) {
        Log.d(TAG, "releaseResources: releasePlayer=$releasePlayer")

        // Stops and releases player (if requested and available).
        if (releasePlayer && exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer?.removeListener(exoPlayerEventListener)
            exoPlayer = null
        }
    }

    private inner class ExoPlayerEventListener : Player.EventListener {
        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.i(TAG, "onPlayerStateChanged: playWhenReady=$playWhenReady, playbackState=$playbackState")
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_BUFFERING, Player.STATE_READY ->
                    isPlaying = true
                Player.STATE_ENDED ->
                    stop()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            Log.e(TAG, "onPlayerStateChanged: error=$error")
        }

        override fun onPositionDiscontinuity(reason: Int) {
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        }

        override fun onSeekProcessed() {
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_STREAM = "https://audio1.maxi80.com"
    }


    fun showAbout(view: View) {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    fun showShare(view: View) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL, resources.getString(R.string.share_to))
        intent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.share_subject))
        intent.putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_text).format(currentTrack, currentArtist))

        startActivity(Intent.createChooser(intent, resources.getString(R.string.share_title)))
    }
}
