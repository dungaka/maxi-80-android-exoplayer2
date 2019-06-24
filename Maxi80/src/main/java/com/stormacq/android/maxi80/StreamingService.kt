package com.stormacq.android.maxi80

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory

class StreamingService() : Service() {

    private var exoPlayer: SimpleExoPlayer? = null
    private val exoPlayerEventListener = ExoPlayerEventListener()

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")

        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand : ${intent.toString()}")

        val app = application as Maxi80Application

        if (Build.VERSION.SDK_INT >= 26) {

            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

            val notification = Notification.Builder(applicationContext, Maxi80Application.NOTIFICATION_CHANNEL_ID)
                    .setContentText(app.station.name())
                    .setContentText(app.station.desc())
                    .setSmallIcon(R.drawable.ic_radio_black_24dp)
                    .setContentIntent(pendingIntent)
                    .build()

            startForeground(1, notification)
        }
        preparePlayer()

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        releaseResources()
    }

    private fun preparePlayer() {
        val app = application as Maxi80Application

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

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        val userAgent = "Android/ExoPlayer 2.10.0/%s %s (%s)".format(app.station.name(),versionName, versionCode)

        val mediaSource = ExtractorMediaSource
            .Factory(DefaultDataSourceFactory(applicationContext, userAgent))
            .setExtractorsFactory(DefaultExtractorsFactory())
            .createMediaSource(Uri.parse(app.station.streamURL()))

        // Prepares media to play (happens on background thread) and triggers
        // {@code onPlayerStateChanged} callback when the stream is ready to play
        exoPlayer?.prepare(mediaSource)

        // register our meta data listener
        exoPlayer?.addMetadataOutput {
            // ICY: title="Gianna Nannini - I maschi (N2 du ToP 50 le 24-10-88)", url="null"
            Log.d(TAG, it.get(0).toString())
            val md = parseMetadata(it.get(0).toString()).streamTitle
            Log.d(TAG, md.toString())
            app.handleiCyMetaData(md)
        }

        exoPlayer?.playWhenReady = true
    }

    private fun releaseResources() {
        Log.d(TAG, "releaseResources") //: releasePlayer=$releasePlayer")

        // Stops and releases player (if requested and available).
        if (exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer?.removeListener(exoPlayerEventListener)
            exoPlayer = null

            val app = application as Maxi80Application
            app.isPlaying = false
        }
    }

    companion object {
        private const val TAG = "Maxi80_StreamingService"
    }

    /**************************************************************************
     *
     * Parsing ICY (Shoutcast) Metadata
     *
     *************************************************************************/

    private fun parseMetadata(metaDataString: String): IcyMetadata {
        val keyAndValuePairs = metaDataString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val icyMetadata = IcyMetadata()

        for (keyValuePair in keyAndValuePairs) {
            val equalSignPosition = keyValuePair.indexOf('=')
            if (equalSignPosition < 1) continue

            val isString = (equalSignPosition + 1 < keyValuePair.length
                    && keyValuePair[keyValuePair.length - 1] == '\''
                    && keyValuePair[equalSignPosition + 1] == '\'')

            val key = keyValuePair.substring(0, equalSignPosition)
            val value = if (isString)
                keyValuePair.substring(equalSignPosition + 2, keyValuePair.length - 1)
            else if (equalSignPosition + 1 < keyValuePair.length)
                keyValuePair.substring(equalSignPosition + 1)
            else
                ""

            when (key) {
                ICY_METADATA_STREAM_TITLE_KEY -> {
                    icyMetadata.streamTitle = value
                    icyMetadata.streamUrl = value
                }
                ICY_METADATA_STREAM_URL_KEY -> icyMetadata.streamUrl = value
            }

            icyMetadata.metadata.put(key, value)
        }

        return icyMetadata
    }

    private val ICY_METADATA_STREAM_TITLE_KEY = "StreamTitle"
    private val ICY_METADATA_STREAM_URL_KEY = "StreamUrl"

    /**
     * Container for stream title and URL.
     *
     *
     * The exact contents isn't specified and implementation specific. It's therefore up to the
     * user to figure what format a given stream returns.
     */
    inner class IcyMetadata {
        /**
         * @return The song title.
         */
        var streamTitle: String = ""
            internal set
        /**
         * @return Url to album artwork or more information about the current song.
         */
        var streamUrl: String = ""
            internal set
        /**
         * Provides a map of all stream metadata.
         *
         * @return Complete metadata
         */
        var metadata: HashMap<String, String> = HashMap()
            internal set

        override fun toString(): String {
            return "IcyMetadata{" +
                    "streamTitle='" + streamTitle + '\''.toString() +
                    ", streamUrl='" + streamUrl + '\''.toString() +
                    ", metadata='" + metadata + '\''.toString() +
                    '}'.toString()
        }
    }

    /**************************************************************************
     *
     * Exo Player callback
     *
     *************************************************************************/

    private inner class ExoPlayerEventListener : Player.EventListener {
        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            Log.i(TAG, "onPlayerStateChanged: playWhenReady=$playWhenReady, playbackState=$playbackState")
            val app = application as Maxi80Application
            when (playbackState) {
                Player.STATE_IDLE ->
                    Log.d(TAG, "player idle")

                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "player buffering")
                    // hack to ensure isPlaying is set early (otherwise we might receive and process
                    // metadata when isPlaying is false, causing the cover to not refresh
                    app.isPlaying = true
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "player ready")
                    app.isPlaying = true
                }

                Player.STATE_ENDED -> {
                    Log.d(TAG, "player ended")
                    releaseResources()
                }
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
    } // end of private inner class

}

