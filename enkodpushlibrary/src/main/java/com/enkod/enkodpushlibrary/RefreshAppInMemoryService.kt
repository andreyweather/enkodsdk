package com.enkod.enkodpushlibrary

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.enkod.enkodpushlibrary.EnkodPushLibrary

class RefreshAppInMemoryService : Service() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        startForeground(1, EnkodPushLibrary.createdNotificationForNetworkService(applicationContext))

        stopSelf()

    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

}