package com.enkod.enkodpushlibrary

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.enkod.enkodpushlibrary.EnkodPushLibrary.createdNotificationForNetworkService
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.Preferences.LOAD_TIMEOUT_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class InternetService : Service() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {

        logInfo("serviceCreated")

        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            exitProcess(0)
        }

        EnkodPushLibrary.pushLoadObserver.observable.subscribe {completed ->

            if (completed) {

                    logInfo("stopSelf")
                    stopSelf()
                    exitProcess(0)
                }
        }

        CoroutineScope(Dispatchers.IO).launch {

            delay(3400)

            logInfo("startSelf")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    createdNotificationForNetworkService(applicationContext),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1, createdNotificationForNetworkService(applicationContext))
            }
        }

        CoroutineScope(Dispatchers.IO).launch {

            val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)

            when (val preferencesStartTimer: Int? = preferences.getInt(LOAD_TIMEOUT_TAG, 15000)) {
               null -> {
                   delay(15000)
                   stopSelf()
                   logInfo("stopSelfLongLoad")
               }
               else -> {
                   delay(preferencesStartTimer.toLong())
                   stopSelf()
                   logInfo("stopSelfLongLoad")
               }
           }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }
}