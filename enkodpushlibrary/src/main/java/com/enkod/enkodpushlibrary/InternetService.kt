package com.enkod.enkodpushlibrary

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat.Builder
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.Preferences.EXIT_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Variables.exitStatusE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class InternetService : Service() {

    override fun onCreate() {

        super.onCreate()

        EnkodPushLibrary.createdService()

        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->

            System.exit(0)

        }

        try {

            if (Build.VERSION.SDK_INT >= 26) {


                val CHANNEL_ID = "my_channel_service"
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Channel",
                    NotificationManager.IMPORTANCE_MIN
                )
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                    channel
                )

                val notification: Notification = Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build()



                CoroutineScope(Dispatchers.IO).launch {

                    delay(3400)

                    if (!isAppInforegrounded()) {

                        if (EnkodPushLibrary.exit == 1) {

                            val preferences =
                                applicationContext.getSharedPreferences(
                                    TAG,
                                    Context.MODE_PRIVATE
                                )
                            preferences.edit()
                                .putString(EXIT_TAG, exitStatusE)
                                .apply()


                            delay(200)
                            exitProcess(0)

                        }

                        val preferences = applicationContext.getSharedPreferences(
                            TAG,
                            Context.MODE_PRIVATE
                        )
                        val exitPref = preferences.getString(EXIT_TAG, null)

                        if (exitPref.toString() == exitStatusE) {

                            if (!isAppInforegrounded()) {

                                exitProcess(0)
                                stopSelf()

                            } else {
                                startForeground(1, notification)
                                stopSelf()
                            }
                        }

                    } else {

                        startForeground(1, notification)
                        stopSelf()

                    }

                    val preferences = applicationContext.getSharedPreferences(
                        TAG,
                        Context.MODE_PRIVATE
                    )
                    val exitPref = preferences.getString(EXIT_TAG, null)


                    if (exitPref.toString() != exitStatusE) {

                        startForeground(1, notification)


                        var seflJob = true

                        while (seflJob) {

                            delay(100)

                            if (EnkodPushLibrary.exitSelf == 1) {
                                stopSelf()
                                seflJob = false

                            }
                        }
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {

                    var foregroundObserver = true

                    while (foregroundObserver) {

                        if (isAppInforegrounded()) {

                            startForeground(1, notification)
                            stopSelf()
                            foregroundObserver = false

                            delay(50)

                        }
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(15000)
                    stopSelf()

                }
            }
        } catch (e: Exception) {

            logInfo("internet service exception $e")

        }
    }

    fun isAppInforegrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)
    }


    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return super.onStartCommand(intent, flags, startId)


    }


}