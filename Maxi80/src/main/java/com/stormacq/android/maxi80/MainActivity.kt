package com.stormacq.android.maxi80

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import com.amazonaws.amplify.generated.graphql.ArtworkQuery
import com.amazonaws.amplify.generated.graphql.StationQuery
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
import okhttp3.OkHttpClient
import saschpe.exoplayer2.ext.icy.IcyHttpDataSourceFactory
import com.amazonaws.regions.Regions
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
//import com.google.android.gms.security.ProviderInstaller
import com.squareup.picasso.Picasso
import javax.net.ssl.SSLContext


/**
 * Test application, doesn't necessarily show the best way to do things.
 */
class MainActivity : AppCompatActivity() {
    private var exoPlayer: SimpleExoPlayer? = null
    private val exoPlayerEventListener = ExoPlayerEventListener()
    private var isPlaying = false

    private var currentArtist : String = ""
    private var currentTrack : String = ""

    private var appSyncClient: AWSAppSyncClient? = null

    private var station : StationQuery.Station? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        play_pause.setOnClickListener {
            if (isPlaying) {
                stop()
            } else {
                play()
            }
        }

        prepareSeekBar()

        prepareAppSync()

        if (Build.VERSION.SDK_INT <= MINIMUM_SDK_FEATURES) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        // query radio data
        appSyncClient!!.query(StationQuery.builder().build())
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<StationQuery.Data>() {
                    override fun onResponse(response: Response<StationQuery.Data>) {
                        this@MainActivity.runOnUiThread {
                            Log.d(TAG, "StationQuery returned : " + response.data().toString())
                            station = response.data()?.station()
                            currentTrack = station!!.name()
                            currentArtist = station!!.desc()
                            preparePlayer()
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        this@MainActivity.runOnUiThread {
                            Log.e(TAG, "Failed to perform StationQuery", e)
                            station = StationQuery.Station("Station",
                                    resources.getString(R.string.app_name),
                                    resources.getString(R.string.app_url),
                                    "",
                                    resources.getString(R.string.app_description),
                                    resources.getString(R.string.app_description))
                            preparePlayer()
                        }
                    }
                })

        //play() will be called when we will receive the StationQuery callback
    }

    override fun onStart() {
        Log.d(TAG, "onStart")
        super.onStart()

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

    private fun preparePlayer() {
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
                .setUserAgent(Util.getUserAgent(applicationContext, station?.name()))
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
                .createMediaSource(Uri.parse(station?.streamURL()))

        // Prepares media to play (happens on background thread) and triggers
        // {@code onPlayerStateChanged} callback when the stream is ready to play
        exoPlayer?.prepare(mediaSource)
        exoPlayer?.playWhenReady = true

    }

    private fun prepareAppSync() {
        // Initialize the Amazon Cognito credentials provider
        val credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext,
                "eu-west-1:74b938b1-4a81-43ed-a4de-86b37001110a", // Identity pool ID
                Regions.EU_WEST_1 // Region
        )

        // initialize the AppSync client
        if (appSyncClient == null) {
            appSyncClient = AWSAppSyncClient.builder()
                    .context(applicationContext)
                    .awsConfiguration(AWSConfiguration(applicationContext))
                    .credentialsProvider(credentialsProvider)
                    .build()
        }


    }

    private fun updateTitle(artist : String, track : String) {
        this@MainActivity.runOnUiThread {
            this.artist.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in))
            this.track.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in))
            this.artist.text = artist
            currentArtist = artist
            this.track.text = track
            currentTrack = track

            loadArtwork(currentArtist, currentTrack)
        }
    }

    private fun loadArtwork(currentArtist : String, currentTrack: String) {

        Log.d(TAG,"Loading artwork for current artist (%s) and track (%s)".format(currentArtist, currentTrack))
        appSyncClient!!.query(ArtworkQuery.builder()
                                        .artist(currentArtist)
                                        .track(currentTrack)
                                        .build())
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<ArtworkQuery.Data>() {
                    override fun onResponse(response: Response<ArtworkQuery.Data>) {
                        this@MainActivity.runOnUiThread {
                            Log.d(TAG, "ArtworkQuery returned : " + response.data().toString())
                            var url = response.data()!!.artwork()!!.url()!!
//                            cover.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in));
                            Picasso.get().load(url).into(cover)
                        }
                    }

                    override fun onFailure(e: ApolloException) {
                        this@MainActivity.runOnUiThread {
                            Log.e(TAG, "Failed to perform ArtworkQuery", e)
//                            cover.startAnimation(AnimationUtils.loadAnimation(applicationContext, android.R.anim.fade_in));
                            cover.setImageResource(R.drawable.nocover_400x400)
                        }
                    }
                })

    }

    private fun handleMetaData(metadata : String) {
        var data = metadata.split(" - ")
        Log.d(TAG, data.toString())
        if (data.size < 2) {
            data = metadata.split("-") // try without space
            Log.d(TAG, data.toString())
            if (data.size < 2) {
                updateTitle(station!!.name(), metadata)
            } else {
                updateTitle(data[0], data[1])
            }
        } else {
            updateTitle(data[0], data[1])
        }
    }

    private fun play() {

        if (exoPlayer == null) {
            preparePlayer()
        }
    }

    private fun stop() {
        if (Build.VERSION.SDK_INT <= 20) {
            play_pause.setImageResource((R.drawable.ic_play_arrow_black_24dp))
        } else {
            play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_play_arrow_black_24dp, null))
        }
        releaseResources()
        isPlaying = false
        updateTitle(station!!.name(), station!!.desc())
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        stop()
        releaseResources()
    }

    private fun releaseResources() {
        Log.d(TAG, "releaseResources") //: releasePlayer=$releasePlayer")

        // Stops and releases player (if requested and available).
        if (exoPlayer != null) {
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
                Player.STATE_IDLE ->
                    Log.d(TAG, "idle")

                Player.STATE_BUFFERING, Player.STATE_READY -> {
                    if (Build.VERSION.SDK_INT <= MINIMUM_SDK_FEATURES) {
                        play_pause.setImageResource((R.drawable.ic_stop_black_24dp))
                    } else {
                        play_pause.setImageDrawable(resources.getDrawable(R.drawable.ic_stop_black_24dp, null))
                    }
                    isPlaying = true
                }

                Player.STATE_ENDED ->
                    stop()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            Log.e(TAG, "onPlayerError: error=$error")
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
        private const val MINIMUM_SDK_FEATURES = 20
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
