package com.enkod.enkodpushlibrary

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.lang.reflect.Type
import java.util.Random
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

object EnkodPushLibrary {

    private const val TAG = "EnkodPushLibrary"
    private const val SESSION_ID_TAG: String = "${TAG}_SESSION_ID"
    private const val TOKEN_TAG: String = "${TAG}_TOKEN"
    private const val ACCOUNT_TAG: String = "${TAG}_ACCOUNT"
    private const val MESSAGEID_TAG = "${TAG}_MESSAGEID"
    private const val WORKER_TAG = "${TAG}_WORKER"
    private const val START_TIMER_TAG = "${TAG}_STARTTIMER"
    private const val TIME_TAG  = "${TAG}_TIME"



    private val initLibObserver = InitLibObserver(false)

    internal val CHANEL_Id = "enkod_lib_1"
    internal var exit = 0
    internal var exitSelf = 0
    internal var serviceCreated = false
    internal var isOnline = true
    internal var addContactAccess = false


    internal var account: String? = null
    internal var token: String? = null
    internal var sessionId: String? = null

    internal var intentName = "intent"
    internal var url: String = "url"

    internal val vibrationPattern = longArrayOf(1500, 500)
    internal val defaultIconId: Int = R.drawable.ic_launcher_background

    private var onPushClickCallback: (Bundle, String) -> Unit = { _, _ -> }
    private var onDynamicLinkClick: ((String) -> Unit)? = null
    internal var newTokenCallback: (String) -> Unit = {}
    private var onDeletedMessage: () -> Unit = {}
    private var onProductActionCallback: (String) -> Unit = {}
    private var onErrorCallback: (String) -> Unit = {}

    internal lateinit var retrofit: Api
    private lateinit var client: OkHttpClient


    class NullOnEmptyConverterFactory : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type,
            annotations: Array<Annotation>,
            retrofit: Retrofit
        ): Converter<ResponseBody, *> {
            Log.d("Library", "responseBodyConverter")
            val delegate: Converter<ResponseBody, *> =
                retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
            return Converter { body ->
                if (body.contentLength() == 0L) null else delegate.convert(
                    body
                )
            }
        }
    }


    enum class OpenIntent {
        DYNAMIC_LINK, OPEN_URL, OPEN_APP;

        fun get(): String {
            Log.d("Library", "get")
            return when (this) {
                DYNAMIC_LINK -> "0"
                OPEN_URL -> "1"
                OPEN_APP -> "2"
            }
        }

        companion object {
            fun get(intent: String?): OpenIntent {
                Log.d("Library", "get")
                return when (intent) {
                    "0" -> DYNAMIC_LINK
                    "1" -> OPEN_URL
                    "2" -> OPEN_APP
                    else -> OPEN_APP
                }
            }
        }
    }

  internal fun initPreferences(context: Context) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        var preferencesAcc = preferences.getString(ACCOUNT_TAG, null)
        var preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        var preferencesToken = preferences.getString(TOKEN_TAG, null)


        this.sessionId = preferencesSessionId
        this.token = preferencesToken
        this.account = preferencesAcc

    }


    internal fun initRetrofit() {

        Log.d("Library", "initRetrofit")
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

        var baseUrl = "http://dev.ext.enkod.ru/"

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(NullOnEmptyConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(Api::class.java)

    }


    internal fun init (context: Context, account: String, token: String? = null) {

        initRetrofit()
        setClientName(context, account)
        initPreferences(context)

        when (token) {

            null -> {

                if (sessionId.isNullOrEmpty()) getSessionIdFromApi(context)
                if (!sessionId.isNullOrEmpty()) startSession()

                Log.d("lib_lvl", "no_fb")

            }

            else -> {


                if (this.token == token && !sessionId.isNullOrEmpty()) {

                    Log.d("lib_lvl", "start_session_fb_true")
                    startSession()
                }

                if (this.token != token) {

                    val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                    preferences.edit()
                        .putString(TOKEN_TAG, token)
                        .apply()
                    this.token = token

                    Log.d("lib_lvl", this.token.toString())


                    if (!sessionId.isNullOrEmpty()) {

                        Log.d("lib_lvl", "update_fb_true")

                        updateToken(sessionId, token)

                    }
                }

                if (sessionId.isNullOrEmpty()) {

                    Log.d("lib_lvl", "created_session_fb_true")
                    getSessionIdFromApi(context)

                }
            }
        }
    }

    private fun getSessionIdFromApi(ctx: Context) {

        Log.d("lib_lvl", "getSessionIdFromApi")

        retrofit.getSessionId(getClientName()).enqueue(object : Callback<SessionIdResponse> {
            override fun onResponse(
                call: Call<SessionIdResponse>,
                response: Response<SessionIdResponse>
            ) {
                response.body()?.session_id?.let {

                    Log.d("new_session", it)
                    newSessions(ctx, it)
                    Toast.makeText(ctx, "connect_getSessionIdFromApi", Toast.LENGTH_LONG).show()

                } ?: run {

                    Toast.makeText(ctx, "error_getSessionIdFromApi", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<SessionIdResponse>, t: Throwable) {

                Toast.makeText(ctx, "error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }


    private fun newSessions(ctx: Context, nsession: String?) {

        Log.d("lib_lvl", "newSessions")


        val preferences = ctx.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        var newPreferencesToken = preferences.getString(TOKEN_TAG, null)

        preferences.edit()
            .putString(SESSION_ID_TAG, nsession)
            .apply()

        this.sessionId = nsession

        Log.d("session", this.sessionId.toString())
        Log.d("new_token", newPreferencesToken.toString())

        if (newPreferencesToken.isNullOrEmpty()) {

            subscribeToPush (getClientName(), getSession(), token)

        } else updateToken(nsession, newPreferencesToken)

    }


     private fun updateToken(session: String?, token: String?) {

        Log.d("lib_lvl", "updateToken")
        retrofit.updateToken(
            getClientName(),
            getSession(),
            SubscribeBody(
                sessionId = session!!,
                token = token!!
            )
        ).enqueue(object : Callback<UpdateTokenResponse> {
            override fun onResponse(
                call: Call<UpdateTokenResponse>,
                response: Response<UpdateTokenResponse>
            ) {
                logInfo("token updated")
                newTokenCallback(token!!)
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
        Log.d("lib_lvl", "startSession")
        tokenSession?.let {
            logInfo("on start session \n")
            sessionId?.let { it1 ->
                retrofit.startSession(it1, getClientName())
                    .enqueue(object : Callback<SessionIdResponse> {
                        override fun onResponse(
                            call: Call<SessionIdResponse>,
                            response: Response<SessionIdResponse>
                        ) {
                            logInfo("session started ${response.body()?.session_id}")
                            //isSessionStarted = true
                            newTokenCallback(it)
                            subscribeToPush (getClientName(), getSession(), token)
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

        var c = ""
        var client: String? = if (client != null) client else c

        var s = ""
        var session: String? = if (session != null) session else s

        var t = ""
        var token: String? = if (token != null) token else t


        Log.d("lib_lvl", "subscribeToPush")

        retrofit.subscribeToPushToken(
            client!!,
            session!!,
            SubscribeBody(
                sessionId = session!!,
                token = token!!,
                os = "android"
            )
        ).enqueue(object : Callback<UpdateTokenResponse> {
            override fun onResponse(
                call: Call<UpdateTokenResponse>,
                response: Response<UpdateTokenResponse>
            ) {
                logInfo("subscribed")

                addContactAccess = true
                initLibObserver.value = true

            }

            override fun onFailure(call: Call<UpdateTokenResponse>, t: Throwable) {
                logInfo("MESSAGE ${t.localizedMessage}")

            }

        })
    }



    fun addContact(

        email: String = "",
        phone: String = "",
        source: String = "mobile",

        extrafileds: Map<String, String>? = null

    ) {

        initLibObserver.observable.subscribe {

            if (it) {

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


                    if (!extrafileds.isNullOrEmpty()) {
                        val keys = extrafileds.keys

                        for (i in 0 until keys.size) {

                            fileds.addProperty(
                                keys.elementAt(i),
                                extrafileds.getValue(keys.elementAt(i))
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
                            val msg = "ok"
                            Log.d("succes", msg)
                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when subscribing: ${t.localizedMessage}"
                            Log.d("error", msg)

                            onErrorCallback(msg)

                        }
                    })
                } else {
                    Log.d("Internet", "Интернет отсутствует")
                }
            }
        }
    }

    fun UpdateContacts(email: String, phone: String) {
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
            }
        })
        return
    }

    fun isOnlineStatus (status: Boolean) {
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
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }



    private fun getClientName(): String {
        Log.d("Library", "getClientName ${this.account}")
        return this.account!!
    }


    private fun getSession(): String {

        return if (!this.sessionId.isNullOrEmpty()) {
            Log.d("getSession", " $sessionId")
            this.sessionId!!
        } else ""

    }

    fun getSessionFromLibrary(context: Context): String {
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        Log.d("prefstring", preferencesSessionId.toString())
        return if (!preferencesSessionId.isNullOrEmpty()) {
            preferencesSessionId!!
        } else ""
    }

    fun getTokenFromLibrary(context: Context): String {
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesToken = preferences.getString(TOKEN_TAG, null)
        return if (!preferencesToken.isNullOrEmpty()) {
            preferencesToken!!
        } else ""
    }


    fun logOut(context: Context) {
        FirebaseMessaging.getInstance().deleteToken();
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        preferences.edit().remove(SESSION_ID_TAG).apply()
        preferences.edit().remove(TOKEN_TAG).apply()
        preferences.edit().remove(WORKER_TAG).apply()
        preferences.edit().remove(START_TIMER_TAG).apply()
        preferences.edit().remove(TIME_TAG).apply()

        sessionId = ""
        token = ""
        WorkManager.getInstance(context).cancelUniqueWork("refreshInMemoryWorker")
        WorkManager.getInstance(context).cancelUniqueWork("refreshToken")
        WorkManager.getInstance(context).cancelUniqueWork("verificationOfTokenWorker")

        initLibObserver.value = false

    }


    internal fun logInfo(msg: String) {
        Log.d("Library", "logInfo + ${msg}")
        Log.i(TAG, msg)
    }


    fun processMessage(context: Context, message: RemoteMessage, image: Bitmap?) {

        createNotificationChannel(context)
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

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("").build()

        return notification
    }



    fun createNotification(context: Context, message: RemoteMessage, image: Bitmap?) {


        with(message.data) {

            val data = message.data

            Log.d("message", data.toString())

            var url = ""

            if (data.containsKey("url") && data[url] != null) {
                url = data["url"].toString()
            }

            for (key in keys) {
                //message.data[key]
                Log.d("message_tag", key.toString())
            }

            val builder = NotificationCompat.Builder(context, CHANEL_Id)


            val pendingIntent: PendingIntent = getIntent(
                context, message.data, "", url
            )

            builder

                .setIcon(context, data["imageUrl"])
                //.setColor(context, data["color"])
                .setLights(
                    get(variables.ledColor), get(variables.ledOnMs), get(variables.ledOffMs)
                )
                .setVibrate(get(variables.vibrationOn).toBoolean())
                .setSound(get(variables.soundOn).toBoolean())
                .setContentTitle(data["title"])
                .setContentText(data["body"])
                .setColor(Color.BLACK)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addActions(context, message.data)
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

                notify(message.data["messageId"]!!.toInt(), builder.build())

                exit = 1

            }
        }
    }

    fun exitSelf() {

        exitSelf = 1

    }

    fun createdService () {

        serviceCreated = true

    }

    fun createdServiceNotification (context: Context, message: RemoteMessage) {

        var observer = true

        CoroutineScope(Dispatchers.IO).launch {
            while (observer) {

                delay(100)

                if (serviceCreated) {
                    delay(3000)
                    Log.d("service_state", "push")
                    downloadImageToPush(context, message)
                    observer = false
                }
            }
        }
    }

    fun createNotificationChannel(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notification Title"
            val descriptionText = "Notification Description"
            //val importance = NotificationManager.IMPORTANCE_MAX
            val channel = NotificationChannel(
                CHANEL_Id,
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

    fun downloadImageToPush(context: Context, message: RemoteMessage) {

        val userAgent = System.getProperty("http.agent")

        val url = GlideUrl(

            message.data["image"], LazyHeaders.Builder()
                .addHeader(
                    "User-Agent",
                    userAgent
                )
                .build()
        )

        Observable.fromCallable(object : Callable<Bitmap?> {
            override fun call(): Bitmap? {
                val future = Glide.with(context).asBitmap()
                    .timeout(30000)
                    .load(url).submit()
                return future.get()
            }
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<Bitmap?> {

                override fun onCompleted() {

                }

                override fun onError(e: Throwable) {

                    Log.d("onError", e.message.toString())
                    processMessage(context, message, null)

                }

                override fun onNext(t: Bitmap?) {
                    processMessage(context, message, t!!)
                    Log.d("onNext", t.toString())
                    exitSelf()
                }
            })
    }

    internal fun getIntent(
        context: Context,
        data: Map<String, String>,
        field: String,
        url: String
    ): PendingIntent {

        Log.d("message_info", "${data["intent"].toString()} ${field.toString()}")
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

        intent!!.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(variables.personId, data[variables.personId])

        return PendingIntent.getActivity(
            context,
            Random().nextInt(1000),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun getOpenAppIntent(context: Context): Intent {
        Log.d("Library", "getOpenAppIntent")
        return Intent(context, OnOpenActivity::class.java).also {
            it.putExtras(
                bundleOf(
                    intentName to OpenIntent.OPEN_APP.get(),
                    OpenIntent.OPEN_APP.name to true
                )
            )
        }
    }

    // функция (getPackageLauncherIntent) создает намерение которое открывает приложение при нажатии на push
    internal fun getPackageLauncherIntent(context: Context): Intent? {

        Log.d("package_intent", "getPackageLauncherIntent")
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
        Log.d("Library", "getOpenUrlIntent")
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


    private fun setClientName(context: Context, acc: String) {
        Log.d("Library", "setClientName ${acc.toString()}")
        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        preferences
            .edit()
            .putString(ACCOUNT_TAG, acc)
            .apply()

        this.account = acc
    }


    internal fun getResourceId(
        context: Context,
        pVariableName: String?,
        resName: String?,
        pPackageName: String?
    ): Int {
        Log.d("Library", "getResourceId")
        return try {
            context.resources.getIdentifier(pVariableName, resName, pPackageName)
        } catch (e: Exception) {
            e.printStackTrace()
            defaultIconId
        }
    }


    internal fun onDeletedMessage() {
        Log.d("Library", "onDeletedMessage")
        onDeletedMessage.invoke()
    }

    internal fun set(hasVibration: Boolean): LongArray {
        Log.d("Library", "set_vibrationPattern")
        return if (hasVibration) {
            vibrationPattern
        } else {
            longArrayOf(0)
        }
    }

    fun handleExtras(context: Context, extras: Bundle) {
        val link = extras.getString(url)
        Log.d("handleExtras", "handleExtras ${extras.getString("messageId")}")
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

        Log.d("intent_lvl", "sendPushClickInfo")


        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        var preferencesAcc = preferences.getString(ACCOUNT_TAG, null)
        var preferencesSessionId = preferences.getString(SESSION_ID_TAG, null)
        var preferencesToken = preferences.getString(TOKEN_TAG, null)
        var preferencesMessageId = preferences.getString(MESSAGEID_TAG, null)

        this.sessionId = preferencesSessionId!!
        this.token = preferencesToken!!
        this.account = preferencesAcc!!


        var sessionID = ""
        var personID = extras.getString(Variables.personId, "0").toInt()
        var messageID = -1
        var intent = extras.getString(intentName, "2").toInt()
        val url = extras.getString(url)

        when (preferencesMessageId) {
            null -> messageID = -1
            else -> messageID = preferencesMessageId.toInt()
        }

        when (preferencesSessionId) {
            null -> sessionID = ""
            else -> sessionID = preferencesSessionId
        }


        initRetrofit()


        Log.d("sendPushClickInfo", "$sessionID, ${personID}, ${messageID}, ${intent}, $url")


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
                Log.d("sendPushClickInfo", response.code().toString())

                onPushClickCallback(extras, msg)
            }

            override fun onFailure(call: Call<PushClickBody>, t: Throwable) {

                val msg = "failure"
                Log.d("sendPushClickInfo", "failure $t")
                logInfo(msg)
                onPushClickCallback(extras, msg)

            }
        })

    }



    // функция (RemoveFromFavourite) фиксирует событые добавления в корзину
    fun addToCart(product: Product) {

        initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    var req = JsonObject()
                    val products = JsonObject()
                    val history = JsonObject()
                    var property = ""

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("productId", product.id)
                    history.addProperty("categoryId", product.categoryId)
                    history.addProperty("count", product.count)
                    history.addProperty("price", product.price)
                    history.addProperty("picture", product.picture)



                    history.addProperty("action", "productAdd")
                    property = "cart"



                    req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    retrofit.addToCart(
                        getClientName(),
                        sessionId!!,
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "success"
                            logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
        }
    }

    // функция (RemoveFromFavourite) фиксирует событые удаления из корзины
    fun removeFromCart(product: Product) {

        initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    var req = JsonObject()
                    val products = JsonObject()
                    val history = JsonObject()
                    var property = ""

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("productId", product.id)
                    history.addProperty("categoryId", product.categoryId)
                    history.addProperty("count", product.count)
                    history.addProperty("price", product.price)
                    history.addProperty("picture", product.picture)



                    history.addProperty("action", "productRemove")
                    property = "cart"


                    req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    retrofit.addToCart(
                        getClientName(),
                        sessionId!!,
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "success"
                            logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
        }
    }

    // функция (RemoveFromFavourite) фиксирует событые добавления из избранное
    fun addToFavourite(product: Product) {

        initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    var req = JsonObject()
                    val products = JsonObject()
                    val history = JsonObject()
                    var property = ""

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("productId", product.id)
                    history.addProperty("categoryId", product.categoryId)
                    history.addProperty("count", product.count)
                    history.addProperty("price", product.price)
                    history.addProperty("picture", product.picture)


                    history.addProperty("action", "productLike")
                    property = "wishlist"


                    req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    retrofit.addToFavourite(
                        getClientName(),
                        sessionId!!,
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            Log.d("Favourite", "success")
                            val msg = "success"
                            logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            Log.d("Favourite", "${t.localizedMessage}")
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })

                } else return@subscribe
            }
        }
    }

// функция (RemoveFromFavourite) фиксирует событые удаления из избранного

    fun removeFromFavourite(product: Product) {

        initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    var req = JsonObject()
                    val products = JsonObject()
                    val history = JsonObject()
                    var property = ""

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("productId", product.id)
                    history.addProperty("categoryId", product.categoryId)
                    history.addProperty("count", product.count)
                    history.addProperty("price", product.price)
                    history.addProperty("picture", product.picture)


                    history.addProperty("action", "productDislike")
                    property = "wishlist"


                    req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    retrofit.addToFavourite(
                        getClientName(),
                        sessionId!!,
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "success"
                            logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })

                } else return@subscribe
            }
        }
    }


// функция (productBuy) для передачи информации о покупках на сервис

    fun productBuy(order: Order) {

        initLibObserver.observable.subscribe {

            if (it) {

                if (order.id.isNullOrEmpty()) {
                    order.id = UUID.randomUUID().toString()
                }

                val orderInfo = JsonObject()
                val items = JsonArray()

                val position = JsonObject()
                position.addProperty("productId", order.productId)

                items.add(position)

                orderInfo.add("items", items)

                orderInfo.add("order", JsonObject().apply {
                    if (order.sum != null) {
                        addProperty("sum", order.sum)
                    }
                    if (order.price != null) {
                        addProperty("price", order.price)
                    }
                    if (order.productId != null) {
                        addProperty("productId", order.productId)
                    }
                    if (order.count != null) {
                        addProperty("count", order.count)
                    }
                    if (order.picture != null) {
                        addProperty("picture", order.picture)
                    }

                })

                val req = JsonObject().apply {
                    addProperty("orderId", order.id)
                    add("orderInfo", orderInfo)
                }
                Log.d("buy", req.toString())
                retrofit.order(
                    getClientName(),
                    sessionId!!,
                    req
                ).enqueue(object : Callback<Unit> {
                    override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                        val msg = "buying ok"
                        logInfo(msg)
                        //ClearCart()
                        onProductActionCallback(msg)
                    }

                    override fun onFailure(call: Call<Unit>, t: Throwable) {
                        val msg = "error when buying: ${t.localizedMessage}"
                        logInfo(msg)
                        onProductActionCallback(msg)
                        onErrorCallback(msg)
                    }
                })
            }
        }
    }

// функция (ProductOpen) для передаци информации об открытии товаров на сервис

    fun productOpen(product: Product) {
        initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    val params = JsonObject()

                    params.addProperty("categoryId", product.categoryId)
                    params.addProperty("price", product.price)
                    params.addProperty("picture", product.picture)


                    val productRequest = JsonObject().apply {
                        addProperty("id", product.id)
                        add("params", params)
                    }
                    val req = JsonObject().apply {
                        addProperty("action", "productOpen")
                        add("product", productRequest)
                    }

                    Log.d("open", req.toString())

                    retrofit.productOpen(
                        getClientName(),
                        sessionId!!,
                        req

                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                            val msg = "product opened"
                            logInfo(msg)
                            onProductActionCallback(msg)
                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when saving product open: ${t.localizedMessage}"
                            logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
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







