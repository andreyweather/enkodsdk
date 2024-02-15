package com.enkod.enkodpushlibrary

import android.content.Context
import android.content.Intent
import android.os.Build
import com.enkod.enkodpushlibrary.EnkodPushLibrary.isAppInforegrounded
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.Preferences.LOAD_TIMEOUT_TAG
import com.enkod.enkodpushlibrary.Preferences.START_TIMER_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_VERIFICATION_TAG
import com.enkod.enkodpushlibrary.Preferences.WORKER_TAG
import com.enkod.enkodpushlibrary.Variables.defaultImageLoadTimeout
import com.enkod.enkodpushlibrary.Variables.defaultTimeRefreshAppInMemory
import com.enkod.enkodpushlibrary.Variables.defaultTimeUpdateToken
import com.enkod.enkodpushlibrary.Variables.defaultTimeVerificationToken
import com.enkod.enkodpushlibrary.Variables.millisInHours
import com.enkod.enkodpushlibrary.Variables.start
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging


class EnkodConnect(

    _account: String?,
    _tokenUpdate: Boolean? = false,
    _refreshAppInMemory: Boolean? = false,
    _timeTokenUpdate: Int? = defaultTimeUpdateToken,
    _timeTokenVerification: Int? = defaultTimeVerificationToken,
    _timeRefreshAppInMemory: Int? = defaultTimeRefreshAppInMemory,
    _imageLoadTimeout: Int? = defaultImageLoadTimeout


) {

    private val account: String
    private val tokenUpdate: Boolean
    private val refreshAppInMemory: Boolean
    private var timeTokenUpdate: Int
    private var timeTokenVerification: Int
    private var timeRefreshAppInMemory: Int
    private var imageLoadTimeout: Int


    init {

        account = _account ?: ""
        tokenUpdate = _tokenUpdate ?: false
        refreshAppInMemory = _refreshAppInMemory ?: false

        timeTokenUpdate =

            if (_timeTokenUpdate != null && _timeTokenUpdate > 0) _timeTokenUpdate
            else defaultTimeUpdateToken

        timeTokenVerification =

            if (_timeTokenVerification != null && _timeTokenVerification > 0) _timeTokenVerification
            else defaultTimeVerificationToken

        timeRefreshAppInMemory =

            if (_timeRefreshAppInMemory != null && _timeRefreshAppInMemory > 0) _timeRefreshAppInMemory
            else defaultTimeRefreshAppInMemory

        imageLoadTimeout =

            if (_imageLoadTimeout != null && _imageLoadTimeout > 0) _imageLoadTimeout
            else defaultImageLoadTimeout

    }


    fun start(context: Context) {


        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesWorker = preferences.getString(WORKER_TAG, null)
        val preferencesStartTimer = preferences.getString(START_TIMER_TAG, null)


        preferences.edit()
            .putInt(LOAD_TIMEOUT_TAG, imageLoadTimeout)
            .apply()



        if (preferencesStartTimer == null && tokenUpdate) {

            preferences.edit()

                .putLong(TIME_TAG, System.currentTimeMillis())
                .apply()

            preferences.edit()

                .putString(START_TIMER_TAG, start)
                .apply()

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

                    logInfo("start library with fcm")
                })

            } catch (e: Exception) {

                EnkodPushLibrary.init(context, account)

                logInfo("start library without fcm")

            }

        } else {

            EnkodPushLibrary.isOnlineStatus(false)

            logInfo("error internet")
        }


        if (preferencesWorker == null && refreshAppInMemory) {
            BackgroundTasks(context).refreshInMemoryWorker(timeRefreshAppInMemory)
        }


        if (tokenUpdate) {

            tokenUpdate(context, timeTokenUpdate)

            preferences.edit()
                .putInt(TIME_VERIFICATION_TAG, timeTokenVerification)
                .apply()

        }else {
            when (preferencesStartTimer) {

                null -> return
                else -> {

                    preferences.edit()
                        .remove(START_TIMER_TAG).apply()

                    preferences.edit()
                        .remove(TIME_TAG).apply()

                }
            }
        }
    }

    private fun tokenUpdate(context: Context, timeInHours: Int) {

        val timeUpdateInMillis: Long = (timeInHours * millisInHours).toLong()

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesTime = preferences.getLong(TIME_TAG, 0)

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