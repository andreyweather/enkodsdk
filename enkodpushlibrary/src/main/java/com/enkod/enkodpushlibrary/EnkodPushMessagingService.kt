package com.enkod.enkodpushlibrary

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initPreferences
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initRetrofit
import com.enkod.enkodpushlibrary.EnkodPushLibrary.isAppInforegrounded
import com.enkod.enkodpushlibrary.EnkodPushLibrary.loadImageFromUrl
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.EnkodPushLibrary.processMessage
import com.enkod.enkodpushlibrary.Preferences.MESSAGEID_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Variables.imageUrl
import com.enkod.enkodpushlibrary.Variables.messageId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class EnkodPushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d("onNewToken", token)

    }

    override fun onDeletedMessages() {

        EnkodPushLibrary.onDeletedMessage()
    }

    @SuppressLint("CheckResult")
    @RequiresApi(Build.VERSION_CODES.O)

    override fun onMessageReceived(message: RemoteMessage) {

        super.onMessageReceived(message)

        EnkodPushLibrary.pushLoadObserver.value = false

        if (!isAppInforegrounded()) {
            val service = Intent(this, InternetService::class.java)
            this.startForegroundService(service)
        }

        val preferences = applicationContext.getSharedPreferences(TAG, MODE_PRIVATE)

        preferences.edit()
            .remove(MESSAGEID_TAG).apply()

        preferences.edit()
            .putString(MESSAGEID_TAG, "${message.data[messageId]}")
            .apply()


        if (!message.data[imageUrl].isNullOrEmpty()) {


            loadImageFromUrl(applicationContext, message.data[imageUrl]!!).subscribe(

                {
                    bitmap ->
                    processMessage(applicationContext, message, bitmap)
                },

                {
                    error ->
                    logInfo("error load img: $error")
                    processMessage(applicationContext, message, null)

                }
            )

        } else {
            processMessage(applicationContext, message, null)
        }


        initRetrofit(applicationContext)
        initPreferences(applicationContext)

        logInfo("onMessageReceived")

    }
}







