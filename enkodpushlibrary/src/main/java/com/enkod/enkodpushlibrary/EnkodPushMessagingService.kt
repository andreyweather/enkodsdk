package com.enkod.enkodpushlibrary

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.enkod.enkodpushlibrary.EnkodPushLibrary.createdServiceNotification
import com.enkod.enkodpushlibrary.EnkodPushLibrary.downloadImageToPush
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initPreferences
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initRetrofit
import com.enkod.enkodpushlibrary.EnkodPushLibrary.processMessage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import rx.Observable
import rx.Observer
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.Callable


private val TAG = "EnkodPushLibrary"
private val EXIT_TAG: String = "${TAG}_EXIT"
private val MESSAGEID_TAG = "${TAG}_MESSAGEID"

class EnkodPushMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()

    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d("onNewToken", token.toString())

    }

    override fun onDeletedMessages() {


        EnkodPushLibrary.onDeletedMessage()

    }

    @RequiresApi(Build.VERSION_CODES.O)

    override fun onMessageReceived(message: RemoteMessage) {

        super.onMessageReceived(message)
        Log.d("onMessageReceived", message.toString())


        val variable = Variables


        // EXIT_TAG - preferences с содержанием поля "noexit" открывают доступ к методу startForeground() в InternetService

        val preferences = applicationContext.getSharedPreferences(TAG, MODE_PRIVATE)
        preferences.edit()

            .putString(EXIT_TAG, "noexit")
            .apply()

        preferences.edit()
            .remove(MESSAGEID_TAG).apply()


        preferences.edit()
            .putString(MESSAGEID_TAG, "${message.data["messageId"]}")
            .apply()


        if (!message.data["image"].isNullOrEmpty()) {

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
                    val future = Glide.with(applicationContext).asBitmap()
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

                        Log.d("onError_one", e.message.toString())

                        startService(message)

                    }

                    override fun onNext(bitmap: Bitmap?) {

                        EnkodPushLibrary.createNotificationChannel(applicationContext)

                        with(message.data) {

                            val data = message.data

                            Log.d("message", data.toString())

                            var url = ""

                            if (data.containsKey("url") && data[url] != null) {
                                url = data["url"].toString()
                            }

                            val builder = NotificationCompat.Builder(
                                applicationContext,
                                EnkodPushLibrary.CHANEL_Id
                            )

                            val pendingIntent: PendingIntent = EnkodPushLibrary.getIntent(
                                applicationContext, message.data, "", url
                            )

                            builder

                                .setIcon(applicationContext, data["imageUrl"])
                                .setLights(
                                    get(variable.ledColor),
                                    get(variable.ledOnMs),
                                    get(variable.ledOffMs
                                    )
                                )
                                .setVibrate(get(variable.vibrationOn).toBoolean())
                                .setSound(get(variable.soundOn).toBoolean())
                                .setColor(Color.BLACK)
                                .setContentTitle(data[variable.title])
                                .setContentText(data[variable.body])
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)
                                .addActions(applicationContext, message.data)
                                .setPriority(NotificationCompat.PRIORITY_MAX)


                            if (bitmap != null) {

                                try {

                                    builder
                                        .setLargeIcon(bitmap)
                                        .setStyle(
                                            NotificationCompat.BigPictureStyle()
                                                .bigPicture(bitmap)
                                                .bigLargeIcon(bitmap)

                                        )
                                } catch (e: Exception) {


                                }
                            }

                            with(NotificationManagerCompat.from(applicationContext)) {
                                if (ActivityCompat.checkSelfPermission(
                                        applicationContext,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {

                                    return
                                }

                                notify(message.data[variable.messageId]!!.toInt(), builder.build())

                            }
                        }

                        Log.d("onNext", bitmap.toString())

                    }
                })

        } else {
            processMessage(this, message, null)
        }


        initRetrofit()
        initPreferences(this)

    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun startService(message: RemoteMessage) {


        if (!isAppInforegrounded()) {

            Log.d("start_service", "start")

            val service = Intent(this, InternetService::class.java)
            this.startForegroundService(service)
            createdServiceNotification(this, message)

        } else {

            downloadImageToPush(this, message)

        }

    }

}

fun isAppInforegrounded(): Boolean {
    val appProcessInfo = ActivityManager.RunningAppProcessInfo();
    ActivityManager.getMyMemoryState(appProcessInfo);
    return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
}






