package com.enkod.enkodpushlibrary

import android.content.Context
import android.content.Intent
import android.os.Build
import com.enkod.enkodpushlibrary.Preferences.START_TIMER_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_TAG
import com.enkod.enkodpushlibrary.Preferences.WORKER_TAG
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging


class EnkodConnect(

    _account: String,
    _tokenUpdate: Boolean? = false,
    _refreshAppInMemory: Boolean? = false,
    _timeTokenUpdate: Int? = 336,
    _timeRefreshAppInMemory: Long? = 12

) {

    private val account: String
    private val tokenUpdate: Boolean?
    private val refreshAppInMemory: Boolean?
    private var timeTokenUpdate: Int?
    private var timeRefreshAppInMemory: Long?


    init {

        account = _account
        tokenUpdate = _tokenUpdate
        refreshAppInMemory = _refreshAppInMemory
        timeTokenUpdate = _timeTokenUpdate
        timeRefreshAppInMemory = _timeRefreshAppInMemory

    }


    fun start(context: Context) {


        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesWorker = preferences.getString(WORKER_TAG, null)
        val preferencesStartTimer = preferences.getString(START_TIMER_TAG, null)


        if (preferencesStartTimer == null) {

            preferences.edit()
                .putLong(TIME_TAG, System.currentTimeMillis())
                .apply()

            preferences.edit()
                .putString(START_TIMER_TAG, "start")
                .apply()

        }

        if (preferencesWorker == null && refreshAppInMemory != null && refreshAppInMemory) {

            BackgroundTasks(context).refreshInMemoryWorker(timeRefreshAppInMemory!!)

        }


        if (EnkodPushLibrary.isOnline(context)) {

            EnkodPushLibrary.isOnlineStatus(true)

            try {

                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {

                        return@OnCompleteListener
                    }

                    val token = task.result

                    EnkodPushLibrary.init(context, account, token)

                })

            } catch (e: Exception) {

                EnkodPushLibrary.init(context, account)

            }

        } else {

            EnkodPushLibrary.isOnlineStatus(false)

        }

        if (tokenUpdate != null && tokenUpdate && timeTokenUpdate != null) {


            tokenUpdate(context, timeTokenUpdate!!)


        }
    }

    private fun tokenUpdate(context: Context, timeInHours: Int) {

        val timeUpdateInMillis: Long = (timeInHours * 3600000).toLong()

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesTime = preferences.getLong(TIME_TAG, 1)


        if (isAppInforegrounded()) {

            if (EnkodPushLibrary.isOnline(context)) {

                if ((System.currentTimeMillis() - preferencesTime) > timeUpdateInMillis) {


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(
                            Intent(
                                context,
                                UpdateTokenService::class.java
                            )
                        )
                    }

                    preferences.edit()
                        .remove(TIME_TAG).apply()

                    preferences.edit()
                        .putLong(TIME_TAG, System.currentTimeMillis())
                        .apply()

                }
            }
        }
    }
}