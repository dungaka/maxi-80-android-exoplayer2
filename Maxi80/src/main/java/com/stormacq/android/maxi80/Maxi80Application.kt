package com.stormacq.android.maxi80

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.amazonaws.amplify.generated.graphql.StationQuery
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.fetcher.AppSyncResponseFetchers
import com.amazonaws.regions.Regions
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class Maxi80Application : Application() {


    // default value (in case API is down)
    var station = StationQuery.Station("Station",
                                        resources.getString(R.string.app_name),
                                        resources.getString(R.string.app_url),
                                        "",
                                        resources.getString(R.string.app_description),
                                        resources.getString(R.string.app_description))

    lateinit var appSyncClient: AWSAppSyncClient

    var currentArtist : String = ""
        private set

    var currentTrack : String = ""
        private set

    // I know, I know, this should be a list and I should implement register(), unregister() methods
    var metaDataChangedListener : MetaDataListener? = null

    var isPlaying = false

    /**************************************************************************
     *
     *  Lifecycle
     *
     *************************************************************************/


    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        var primaryLocale: Locale

        //check locale for debugging
        if (Build.VERSION.SDK_INT >= 24) {
            primaryLocale = applicationContext.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            primaryLocale = applicationContext.resources.configuration.locale
        }
        Log.d(TAG, "onCreate, locale is ${primaryLocale}}")

        prepareAppSync()

        queryRadioData()

        prepareNotificationChannel()

    }

    private fun prepareAppSync() {

        // Initialize the Amazon Cognito credentials provider
        val credentialsProvider = CognitoCachingCredentialsProvider(
                applicationContext,
                "eu-west-1:74b938b1-4a81-43ed-a4de-86b37001110a", // Identity pool ID
                Regions.EU_WEST_1 // Region
        )

        // initialize the AppSync client
        val builder = AWSAppSyncClient.builder()
                .context(applicationContext)
                .awsConfiguration(AWSConfiguration(applicationContext))
                .credentialsProvider(credentialsProvider)


        if (Build.VERSION.SDK_INT <= MINIMUM_SDK_FEATURES) {

            // The below is required on Android API <= 20 to enable TLSv1.0 (SSLv3 is not supported)
            // https://stackoverflow.com/questions/29249630/android-enable-tlsv1-2-in-okhttp
            // https://github.com/square/okhttp/issues/1934
            // https://stackoverflow.com/questions/31002159/now-that-sslsocketfactory-is-deprecated-on-android-what-would-be-the-best-way-t

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)

            val tms = tmf.trustManagers
            if (tms.size != 1 || tms[0] !is X509TrustManager) {
                throw IllegalStateException("Unexpected default trust managers: $tms")
            }
            val tm = tms[0] as X509TrustManager

            val okHTTPClient = OkHttpClient.Builder().sslSocketFactory(TLSSocketFactory(), tm).build()

            // use our specific OKHTTP Client for Android <= 20
            builder.okHttpClient(okHTTPClient) // for android <= 20
        }

        appSyncClient = builder.build()
    }

    private fun prepareNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {
            val serviceChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    resources.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW)
            val manager : NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun queryRadioData() {

        // query radio data
        appSyncClient.query(StationQuery.builder().build())
                .responseFetcher(AppSyncResponseFetchers.NETWORK_ONLY)
                .enqueue(object : GraphQLCall.Callback<StationQuery.Data>() {
                    override fun onResponse(response: Response<StationQuery.Data>) {
                        Log.d(TAG, "StationQuery returned : " + response.data().toString())
                        station = response.data()?.station() as StationQuery.Station
                        setTrack(station.name(), station.desc())
                    }

                    override fun onFailure(e: ApolloException) {
                        Log.e(TAG, "Failed to perform StationQuery", e)
                        // default value is set already, let's use that one
                    }
                })
    }


    /**************************************************************************
     *
     *  Meta Data
     *
     *************************************************************************/

    fun setTrack(artist: String?, track: String?) {
        val _artist = artist ?: station.name()
        val _track = track ?: station.desc()

        currentArtist = _artist
        currentTrack = _track

        metaDataChangedListener?.onCurrentTrackChanged(currentArtist, currentTrack)
    }



    // callback to receive metadata
    fun handleiCyMetaData(metadata : String) {

        var data = metadata.split(" - ")
        Log.d(TAG, data.toString())

        if (data.size == 2) {
            setTrack(data[0], data[1])
        } else {

            data = metadata.split("-") // try without space
            Log.d(TAG, data.toString())

            if (data.size == 2) {
                setTrack(data[0], data[1])
            } else {
                setTrack(null, metadata)
            }
        }

    }

    companion object {
        private const val TAG = "Maxi80_Application"
        const val NOTIFICATION_CHANNEL_ID = "com.stormacq.android.maxi80"
        const val MINIMUM_SDK_FEATURES = 20
    }
}