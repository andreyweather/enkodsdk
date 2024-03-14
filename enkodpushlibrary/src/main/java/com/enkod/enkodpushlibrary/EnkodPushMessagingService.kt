package com.enkod.enkodpushlibrary

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.enkod.enkodpushlibrary.EnkodPushLibrary.creatureInputDataFromMessage
import com.enkod.enkodpushlibrary.EnkodPushLibrary.isAppInforegrounded
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.EnkodPushLibrary.managingTheNotificationCreationProcess
import com.enkod.enkodpushlibrary.Preferences.MESSAGEID_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Variables.messageId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class EnkodPushMessagingService : FirebaseMessagingService() {


    override fun onNewToken(token: String) {
        super.onNewToken(token)

        logInfo("new token $token")

    }

    override fun onDeletedMessages() {

        EnkodPushLibrary.onDeletedMessage()
    }


    @SuppressLint("SwitchIntDef")
    override fun onMessageReceived(message: RemoteMessage) {

        super.onMessageReceived(message)

        val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesUsingFcm: Boolean? =
            preferences.getBoolean(Preferences.USING_FCM, false)

        if (preferencesUsingFcm != null && preferencesUsingFcm == true) {

            logInfo("message.priority ${message.priority}")

            val dataFromPush =
                creatureInputDataFromMessage(message).keyValueMap as Map<String, String>

            fun showPushWorkManager() {

                logInfo("show push with expedition work manager")

                val workRequest = OneTimeWorkRequestBuilder<LoadImageWorker>()

                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(creatureInputDataFromMessage(message))
                    .build()

                WorkManager

                    .getInstance(applicationContext)
                    .enqueue(workRequest)

            }

            if (!isAppInforegrounded()) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    if (Build.VERSION.SDK_INT < 31) {
                        Log.d("Build.VERSION", Build.VERSION.SDK_INT.toString())
                        val service = Intent(this, InternetService::class.java)
                        this.startForegroundService(service)

                    } else if (Build.VERSION.SDK_INT >= 31) {

                        when (message.priority) {

                            1 -> managingTheNotificationCreationProcess(
                                applicationContext,
                                dataFromPush
                            )

                            2 -> showPushWorkManager()
                            else -> showPushWorkManager()

                        }
                    }
                }
            } else {
                managingTheNotificationCreationProcess(applicationContext, dataFromPush)
            }

            preferences.edit()
                .remove(MESSAGEID_TAG).apply()

            preferences.edit()
                .putString(MESSAGEID_TAG, "${dataFromPush[messageId]}")
                .apply()

        } else return
    }
}














