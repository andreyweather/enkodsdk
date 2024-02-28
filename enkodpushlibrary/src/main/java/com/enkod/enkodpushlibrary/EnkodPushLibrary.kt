package com.enkod.enkodpushlibrary

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.Notification.FOREGROUND_SERVICE_IMMEDIATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobScheduler
import android.content.Context
import android.content.Context.JOB_SCHEDULER_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.enkod.enkodpushlibrary.Preferences.ACCOUNT_TAG
import com.enkod.enkodpushlibrary.Preferences.DEV_TAG
import com.enkod.enkodpushlibrary.Preferences.LOAD_TIMEOUT_TAG
import com.enkod.enkodpushlibrary.Preferences.MESSAGEID_TAG
import com.enkod.enkodpushlibrary.Preferences.SESSION_ID_TAG
import com.enkod.enkodpushlibrary.Preferences.START_AUTO_UPDATE_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_LAST_TOKEN_UPDATE_TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_TOKEN_AUTO_UPDATE_TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_VERIFICATION_TAG
import com.enkod.enkodpushlibrary.Preferences.TOKEN_TAG
import com.enkod.enkodpushlibrary.Variables.body
import com.enkod.enkodpushlibrary.Variables.ledColor
import com.enkod.enkodpushlibrary.Variables.ledOffMs
import com.enkod.enkodpushlibrary.Variables.ledOnMs
import com.enkod.enkodpushlibrary.Variables.messageId
import com.enkod.enkodpushlibrary.Variables.personId
import com.enkod.enkodpushlibrary.Variables.soundOn
import com.enkod.enkodpushlibrary.Variables.title
import com.enkod.enkodpushlibrary.Variables.vibrationOn
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.subjects.BehaviorSubject
import java.lang.reflect.Type
import java.util.Random
import java.util.concurrent.TimeUnit


object EnkodPushLibrary {

    private const val baseUrl = "https://ext.enkod.ru/"
    internal const val chanelEnkod = "enkod_lib_1"

    internal var isOnline = true


    private var account: String? = null
    private var token: String? = null
    private var sessionId: String? = null

    internal var intentName = "intent"
    internal var url: String = "url"

    internal val vibrationPattern = longArrayOf(1500, 500)
    internal val defaultIconId: Int = R.drawable.ic_launcher_foreground

    internal val initLibObserver = InitLibObserver(false)
    internal val pushLoadObserver = PushLoadObserver(false)
    internal val startTokenAutoUpdateObserver = StartTokenAutoUpdateObserver(false)


    private var onPushClickCallback: (Bundle, String) -> Unit = { _, _ -> }
    private var onDynamicLinkClick: ((String) -> Unit)? = null
    internal var newTokenCallback: (String) -> Unit = {}
    private var onDeletedMessage: () -> Unit = {}
    private var onProductActionCallback: (String) -> Unit = {}
    private var onErrorCallback: (String) -> Unit = {}


    internal lateinit var retrofit: Api
    private lateinit var client: OkHttpClient


    internal fun init(context: Context, account: String, token: String? = null) {

        initRetrofit(context)
        setClientName(context, account)
        initPreferences(context)


        when (token) {

            null -> {

                if (sessionId.isNullOrEmpty()) getSessionIdFromApi(context)
                if (!sessionId.isNullOrEmpty()) startSession()

            }

            else -> {

                if (this.token == token && !sessionId.isNullOrEmpty()) {

                    startSession()
                }

                if (this.token != token) {

                    val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                    preferences.edit()
                        .putString(TOKEN_TAG, token)
                        .apply()


                    this.token = token

                    logInfo( "token updated in library")

                    if (!sessionId.isNullOrEmpty()) {

                        updateToken(context, sessionId, token)
                    }
                }

                if (sessionId.isNullOrEmpty()) {

                    getSessionIdFromApi(context)

                }
            }
        }
    }

    private fun setClientName(context: Context, acc: String) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putString(ACCOUNT_TAG, acc)
            .apply()

        this.account = acc
    }

    internal fun initPreferences(context: Context) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        val preferencesAcc = preferences.getString(ACCOUNT_TAG, null)
        val preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        val preferencesToken = preferences.getString(TOKEN_TAG, null)


        this.sessionId = preferencesSessionId
        this.token = preferencesToken
        this.account = preferencesAcc

    }

    class NullOnEmptyConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit
        ): Converter<ResponseBody, *> {

            val delegate: Converter<ResponseBody, *> =
                retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
            return Converter { body ->
                if (body.contentLength() == 0L) null else delegate.convert(
                    body
                )
            }
        }
    }

    internal fun initRetrofit(context: Context) {

        client = OkHttpClient.Builder()
            .callTimeout(60L, TimeUnit.SECONDS)
            .connectTimeout(60L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY

                }
            )
            .build()

        val urlRetrofit = when (dev(context)) {

            null -> baseUrl
            else -> dev(context)!!
        }

        Log.d("retrofitBaseUrl", urlRetrofit)

        retrofit = Retrofit.Builder()
            .baseUrl(urlRetrofit)
            .addConverterFactory(NullOnEmptyConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(Api::class.java)

    }


    private fun getSessionIdFromApi(context: Context) {

        retrofit.getSessionId(getClientName()).enqueue(object : Callback<SessionIdResponse> {
            override fun onResponse(
                call: Call<SessionIdResponse>,
                response: Response<SessionIdResponse>
            ) {
                response.body()?.session_id?.let {

                    newSessions(context, it)

                    logInfo("created_newSession")

                    when (dev(context)) {
                        null -> return
                        else ->  Toast.makeText(context, "connect_getSessionIdFromApi", Toast.LENGTH_LONG).show()
                    }

                } ?: run {

                    logInfo("error_created_newSession")

                    when (dev(context)) {
                        null -> return
                        else ->  Toast.makeText(context, "error_getSessionIdFromApi", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onFailure(call: Call<SessionIdResponse>, t: Throwable) {

                logInfo("error_created session retrofit $t")

                when (dev(context)) {
                    null -> return
                    else ->  Toast.makeText(context, "error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }


    private fun newSessions(context: Context, session: String?) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        val newPreferencesToken = preferences.getString(TOKEN_TAG, null)

        preferences.edit()
            .putString(SESSION_ID_TAG, session)
            .apply()

        this.sessionId = session


        if (newPreferencesToken.isNullOrEmpty()) {

            startSession()

        }
        else updateToken(context, session, newPreferencesToken)
    }


    private fun updateToken(context: Context, session: String?, token: String?) {

        val session = session ?: ""
        val token = token ?: ""

        retrofit.updateToken(
            getClientName(),
            getSession(),
            SubscribeBody(
                sessionId = session,
                token = token
            )
        ).enqueue(object : Callback<UpdateTokenResponse> {
            override fun onResponse(
                call: Call<UpdateTokenResponse>,
                response: Response<UpdateTokenResponse>
            ) {

                logInfo("token updated in service code: ${response.code()}")
                newTokenCallback(token)

                val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

                preferences.edit()
                    .putLong (TIME_LAST_TOKEN_UPDATE_TAG, System.currentTimeMillis())
                    .apply()

                startSession()

            }

            override fun onFailure(call: Call<UpdateTokenResponse>, t: Throwable) {


                logInfo("token update failure")
            }

        })
    }



    private fun startSession() {

        var tokenSession = ""
        if (!this.token.isNullOrEmpty()) {
            tokenSession = this.token!!
        }

        tokenSession?.let {
            sessionId?.let { it1 ->
                retrofit.startSession(it1, getClientName())
                    .enqueue(object : Callback<SessionIdResponse> {
                        override fun onResponse(
                            call: Call<SessionIdResponse>,
                            response: Response<SessionIdResponse>
                        ) {
                            logInfo("session started ${response.body()?.session_id}")
                            newTokenCallback(it)
                            subscribeToPush(getClientName(), getSession(), token)
                        }

                        override fun onFailure(call: Call<SessionIdResponse>, t: Throwable) {
                            logInfo("session not started ${t.message}")
                            newTokenCallback(it)
                        }
                    })
            }
        }
    }


    private fun subscribeToPush(client: String?, session: String?, token: String?) {

        val client = client ?: ""
        val session = session ?: ""
        val token = token ?: ""

        retrofit.subscribeToPushToken(
            client,
            session,
            SubscribeBody(
                sessionId = session,
                token = token,
                os = "android"
            )
        ).enqueue(object : Callback<UpdateTokenResponse> {
            override fun onResponse(
                call: Call<UpdateTokenResponse>,
                response: Response<UpdateTokenResponse>
            ) {
                logInfo("subscribed")

                initLibObserver.value = true

            }

            override fun onFailure(call: Call<UpdateTokenResponse>, t: Throwable) {
                logInfo("no subscribed ${t.localizedMessage}")

            }

        })
    }

    fun addContact(

        email: String = "",
        phone: String = "",
        source: String = "mobile",

        params: Map<String, String>? = null

    ) {

        initLibObserver.observable.subscribe {

            Log.d ("observable", "ok")

            if (it) {

                Log.d ("observable", "in contact")

                if (isOnline) {

                    val req = JsonObject()

                    if (!email.isNullOrEmpty() && !phone.isNullOrEmpty()) {
                        req.add("mainChannel", Gson().toJsonTree("email"))
                    } else if (!email.isNullOrEmpty() && phone.isNullOrEmpty()) {
                        req.add("mainChannel", Gson().toJsonTree("email"))
                    } else if (email.isNullOrEmpty() && !phone.isNullOrEmpty()) {
                        req.add("mainChannel", Gson().toJsonTree("phone"))
                    }


                    val fileds = JsonObject()


                    if (!params.isNullOrEmpty()) {

                        val keys = params.keys

                        for (i in keys.indices) {

                            fileds.addProperty(
                                keys.elementAt(i),
                                params.getValue(keys.elementAt(i))
                            )
                        }
                    }

                    if (!email.isNullOrEmpty()) {
                        fileds.addProperty("email", email)
                    }

                    if (!phone.isNullOrEmpty()) {
                        fileds.addProperty("phone", phone)
                    }

                    req.addProperty("source", source)

                    req.add("fields", fileds)

                    Log.d("req_json", req.toString())

                    retrofit.subscribe(
                        getClientName(),
                        sessionId!!,
                        req

                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            logInfo("add contact")
                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when subscribing: ${t.localizedMessage}"
                            logInfo("error add contact $t")
                            onErrorCallback(msg)
                        }
                    })
                } else {
                    logInfo("error add contact no Internet")
                }
            }
        }
    }

    fun updateContacts (email: String, phone: String) {
        val params = hashMapOf<String, String>()
        if (email.isNotEmpty()) {
            params.put("email", email)
        }
        if (phone.isNotEmpty()) {
            params.put("phone", phone)
        }

        retrofit.updateContacts(
            getClientName(),
            getSession(),
            params
        ).enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {

            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                onErrorCallback("Error when updating: ${t.localizedMessage}")
                logInfo("error updatingContact $t")
            }
        })
        return
    }

    fun isOnlineStatus(status: Boolean) {
        isOnline = status
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return true
                }
            }
        }
        return false
    }

    fun isAppInforegrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    }

    fun devMode(context: Context, url: String) {
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        preferences.edit()
            .putString(DEV_TAG, url)
            .apply()
    }

     private fun dev (context: Context): String? {
         val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
         val devUrl = preferences.getString(DEV_TAG, null)
         return devUrl
     }

    internal fun getClientName(): String {

        return if (!this.account.isNullOrEmpty()) {
            this.account!!
        } else ""
    }

     internal fun getSession(): String {

        return if (!this.sessionId.isNullOrEmpty()) {
            this.sessionId!!
        } else ""
    }

    fun getSessionFromLibrary(context: Context): String {
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        return if (!preferencesSessionId.isNullOrEmpty()) {
            preferencesSessionId
        } else ""
    }

    fun getTokenFromLibrary(context: Context): String {
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesToken = preferences.getString(TOKEN_TAG, null)
        return if (!preferencesToken.isNullOrEmpty()) {
            preferencesToken
        } else ""
    }


    fun logOut(context: Context) {

        FirebaseMessaging.getInstance().deleteToken()

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        preferences.edit().remove(SESSION_ID_TAG).apply()
        sessionId = ""
        preferences.edit().remove(ACCOUNT_TAG).apply()
        account = ""
        preferences.edit().remove(TOKEN_TAG).apply()
        token = ""


        preferences.edit().remove(TIME_VERIFICATION_TAG).apply()
        preferences.edit().remove(DEV_TAG).apply()
        preferences.edit().remove(LOAD_TIMEOUT_TAG).apply()
        preferences.edit().remove(TIME_LAST_TOKEN_UPDATE_TAG).apply()
        preferences.edit().remove(START_AUTO_UPDATE_TAG).apply()
        preferences.edit().remove(TIME_TOKEN_AUTO_UPDATE_TAG).apply()

        WorkManager.getInstance(context).cancelUniqueWork("verificationOfTokenWorker")
        WorkManager.getInstance(context).cancelUniqueWork("tokenAutoUpdateWorker")

        val scheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler?

        when (scheduler){

            null -> return
            else -> {

                scheduler.cancel(1)
                scheduler.cancel(2)
            }
        }

        initLibObserver.value = false

        logInfo("logOut")

    }

    internal fun logInfo(msg: String) {
        Log.d("enkodLibrary", msg)
        Log.i(TAG, msg)
    }

    fun creatureInputDataFromMessage (message: RemoteMessage): Data {

        val dataBuilder = Data.Builder()

        for (key in message.data.keys) {

            if (!message.data[key].isNullOrEmpty())  {
                dataBuilder.putString(key, message.data[key])
            }
        }

        val inputData = dataBuilder.build()

        return inputData
    }


    fun loadImageFromUrl(context: Context, url: String): Single<Bitmap> {

        val userAgent = when (val agent: String? = System.getProperty("http.agent")) {
            null -> "android"
            else -> agent
        }

        val imageUrl = GlideUrl(

            url, LazyHeaders.Builder()
                .addHeader(
                    "User-Agent",
                    userAgent
                )
                .build()
        )

        return Single.create { emitter ->
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        emitter.onSuccess(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        emitter.onError(Exception("Failed to load image"))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Optional implementation
                    }
                })
        }
    }

    @SuppressLint("CheckResult")
    fun managingTheNotificationCreationProcess (context: Context, message: Map <String,String>) {

        if (!message[Variables.imageUrl].isNullOrEmpty()) {

            loadImageFromUrl(context, message[Variables.imageUrl]!!).subscribe(

                {
                        bitmap ->
                    logInfo("onMessageReceived successful image upload")
                    processMessage(context, message, bitmap)
                },

                {
                        error ->
                    logInfo ("onMessageReceived error image upload: $error")
                    processMessage(context, message, null)

                }
            )

        } else {
            logInfo("onMessageReceived without image url")
            processMessage(context, message, null)
        }

        initRetrofit(context)
        initPreferences(context)
    }


    fun processMessage(context: Context, message: Map<String, String>, image: Bitmap?) {

        createNotificationChannelForPush(context)
        createNotification(context, message, image)

    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun createdNotificationForNetworkService(context: Context): Notification {

        val CHANNEL_ID = "my_channel_service"
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Channel",
            NotificationManager.IMPORTANCE_MIN
        )
        (context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            channel
        )


        val builder = Notification.Builder(context, CHANNEL_ID)


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        } else {
            builder
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
        }


        builder
            .setContentTitle("")
            .setContentText("").build()


        return builder.build()
    }

    fun createNotificationChannelForPush (context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification Title"
            val descriptionText = "Notification Description"
            val channel = NotificationChannel(
                chanelEnkod,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager? =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun createNotification(context: Context, message: Map<String, String>, image: Bitmap?) {

        with(message) {

            val data = message

            var url = ""

            if (data.containsKey("url") && data[url] != null) {
                url = data["url"].toString()
            }

            val builder = NotificationCompat.Builder(context, chanelEnkod)

            val pendingIntent: PendingIntent = getIntent(
                context, message, "", url
            )

            builder

                .setIcon(context, data["imageUrl"])
                .setLights(
                    get(ledColor), get(ledOnMs), get(ledOffMs)
                )
                .setVibrate(get(vibrationOn).toBoolean())
                .setSound(get(soundOn).toBoolean())
                .setContentTitle(data[title])
                .setContentText(data[body])
                .setColor(Color.BLACK)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addActions(context, message)
                .setPriority(NotificationCompat.PRIORITY_MAX)


            if (image != null) {

                try {

                    builder
                        .setLargeIcon(image)
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(image)
                                .bigLargeIcon(image)

                        )
                } catch (e: Exception) {

                    logInfo("error push img builder" )
                }
            }

            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    return
                }

                val messageID = message[messageId]?.toInt() ?: -1

                notify(messageID, builder.build())

               pushLoadObserver.value = true

            }
        }
    }


    internal fun getIntent(
        context: Context,
        data: Map<String, String>,
        field: String,
        url: String
    ): PendingIntent {

        val intent =
            if (field == "1") {
                getOpenUrlIntent(context, data, url)
            } else if (data["intent"] == "1") {
                getOpenUrlIntent(context, data, "null")
            } else if (field == "0") {
                getDynamicLinkIntent(context, data, url)
            } else if (data["intent"] == "0")
                getDynamicLinkIntent(context, data, "null")
            else {

                getOpenAppIntent(context)
            }

        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(personId, data[personId])

        return PendingIntent.getActivity(
            context,
            Random().nextInt(1000),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    enum class OpenIntent {
        DYNAMIC_LINK, OPEN_URL, OPEN_APP;

        fun get(): String {

            return when (this) {
                DYNAMIC_LINK -> "0"
                OPEN_URL -> "1"
                OPEN_APP -> "2"

            }
        }

        companion object {
            fun get(intent: String?): OpenIntent {

                return when (intent) {
                    "0" -> DYNAMIC_LINK
                    "1" -> OPEN_URL
                    "2" -> OPEN_APP
                    else -> OPEN_APP
                }
            }
        }
    }


    internal fun getOpenAppIntent(context: Context): Intent {

        return Intent(context, OnOpenActivity::class.java).also {
            it.putExtras(
                bundleOf(
                    intentName to OpenIntent.OPEN_APP.get(),
                    OpenIntent.OPEN_APP.name to true
                )
            )
        }
    }

    internal fun getPackageLauncherIntent(context: Context): Intent? {

        val pm: PackageManager = context.packageManager
        return pm.getLaunchIntentForPackage(context.packageName).also {
            val bundle = (
                    bundleOf(
                        intentName to OpenIntent.OPEN_APP.get(),
                        OpenIntent.OPEN_APP.name to true
                    )
                    )

        }
    }

    private fun getDynamicLinkIntent(
        context: Context,
        data: Map<String, String>,
        URL: String
    ): Intent {
        if (URL != "null") {
            return Intent(context, OnOpenActivity::class.java).also {
                it.putExtras(
                    bundleOf(
                        intentName to OpenIntent.DYNAMIC_LINK.get(),
                        OpenIntent.OPEN_APP.name to true,
                        this.url to URL
                    )
                )
            }

        } else {
            return Intent(context, OnOpenActivity::class.java).also {
                it.putExtras(
                    bundleOf(
                        intentName to OpenIntent.DYNAMIC_LINK.get(),
                        OpenIntent.OPEN_APP.name to true,
                        url to data[url]
                    )
                )
            }
        }
    }


    private fun getOpenUrlIntent(context: Context, data: Map<String, String>, URL: String): Intent {

        if (URL != "null") {
            return Intent(context, OnOpenActivity::class.java).also {
                it.putExtras(
                    bundleOf(
                        intentName to OpenIntent.OPEN_URL.get(),
                        OpenIntent.OPEN_APP.name to true,
                        this.url to URL
                    )
                )
            }
        } else {
            logInfo("GET INTENT ${OpenIntent.get(data[intentName])} ${data[intentName]} ${data[url]}")
            return Intent(context, OnOpenActivity::class.java).also {
                it.putExtra(intentName, OpenIntent.OPEN_URL.get())
                it.putExtra(url, data[url])
                it.putExtra(OpenIntent.OPEN_APP.name, true)
                it.putExtras(
                    bundleOf(
                        intentName to OpenIntent.OPEN_URL.get(),
                        OpenIntent.OPEN_APP.name to true,
                        url to data[url]
                    )
                )
            }
        }
    }


    internal fun getResourceId(
        context: Context,
        pVariableName: String?,
        resName: String?,
        pPackageName: String?
    ): Int {

        return try {
            context.resources.getIdentifier(pVariableName, resName, pPackageName)
        } catch (e: Exception) {
            e.printStackTrace()
            defaultIconId
        }
    }


    internal fun onDeletedMessage() {

        onDeletedMessage.invoke()
    }

    internal fun set(hasVibration: Boolean): LongArray {
        return if (hasVibration) {
            vibrationPattern
        } else {
            longArrayOf(0)
        }
    }

    fun handleExtras(context: Context, extras: Bundle) {
        val link = extras.getString(url)
        sendPushClickInfo(extras, context)
        when (OpenIntent.get(extras.getString(intentName))) {
            OpenIntent.OPEN_URL -> {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse(link))
                )
            }

            OpenIntent.DYNAMIC_LINK -> {
                link?.let {
                    onDynamicLinkClick?.let { callback ->
                        return callback(it)
                    }
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse(link))
                    )
                }
            }

            else -> {
                context.startActivity(getPackageLauncherIntent(context))
            }
        }
    }

    private fun sendPushClickInfo(extras: Bundle, context: Context) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        val preferencesAcc = preferences.getString(ACCOUNT_TAG, null)
        val preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        val preferencesToken = preferences.getString(TOKEN_TAG, null)
        val preferencesMessageId = preferences.getString(MESSAGEID_TAG, null)

        this.sessionId = preferencesSessionId ?: ""
        this.token = preferencesToken ?: ""
        this.account = preferencesAcc ?: ""


        val personID = extras.getString(personId, "0").toInt()
        val intent = extras.getString(intentName, "2").toInt()
        val url = extras.getString(url)

        val messageID = when (preferencesMessageId) {
            null -> -1
            else -> preferencesMessageId.toInt()
        }

        val sessionID = when (preferencesSessionId) {
            null ->  ""
            else -> preferencesSessionId
        }

        initRetrofit(context)

        retrofit.pushClick(
            getClientName(),
            PushClickBody(

                sessionId = sessionID,
                personId = personID,
                messageId = messageID,
                intent = intent,
                url = url

            )
        ).enqueue(object : Callback<PushClickBody> {


            override fun onResponse(
                call: Call<PushClickBody>,
                response: Response<PushClickBody>
            ) {
                val msg = "succsess"
                response.code()
                onPushClickCallback(extras, msg)
            }

            override fun onFailure(call: Call<PushClickBody>, t: Throwable) {

                val msg = "failure"
                logInfo(msg)
                onPushClickCallback(extras, msg)

            }
        })

    }

    fun PageOpen(url: String){
        if (url.isEmpty()){
            return
        }
        retrofit.pageOpen(
            getClientName(),
            getSession(),
            PageUrl(url)
        ).enqueue(object : Callback<Unit>{
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                val msg = "page opened"
                logInfo(msg)
                onProductActionCallback
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                val msg = "error when saving page open: ${t.localizedMessage}"
                logInfo(msg)
                onProductActionCallback(msg)
                onErrorCallback(msg)
            }
        })
    }

    fun checkBatteryOptimization(mContext: Context) {

        val powerManager =
            mContext.getSystemService(AppCompatActivity.POWER_SERVICE) as PowerManager
        val packageName = mContext.packageName
        val i = Intent()
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Log.d("checkBatteryOptimization", "inmethod")
            i.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            i.data = Uri.parse("package:$packageName")
            mContext.startActivity(i)
        }
    }
 }



class InitLibObserver<T>(private val defaultValue: T) {
    var value: T = defaultValue
        set(value) {
            field = value
            observable.onNext(value)
        }
    val observable = BehaviorSubject.create<T>(value)
}

class PushLoadObserver<T>(private val defaultValue: T) {
    var value: T = defaultValue
        set(value) {
            field = value
            observable.onNext(value)
        }
    val observable = BehaviorSubject.create<T>(value)
}

class StartTokenAutoUpdateObserver<T>(private val defaultValue: T) {
    var value: T = defaultValue
        set(value) {
            field = value
            observable.onNext(value)
        }
    val observable = BehaviorSubject.create<T>(value)
}


class LoadImageWorker (context: Context, workerParameters: WorkerParameters) :
    Worker(context, workerParameters) {

    @SuppressLint("CheckResult")
    override fun doWork(): Result {

        Log.d("onMessageReceived", "doWork")

        return try {

            val inputData = inputData

            val message = inputData.keyValueMap as Map<String,String>

            EnkodPushLibrary.managingTheNotificationCreationProcess(applicationContext, message)

            Result.success()
        } catch (e: Exception) {

            Result.failure();
        }
    }
}




