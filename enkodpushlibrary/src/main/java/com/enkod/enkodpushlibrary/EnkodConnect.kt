package com.enkod.enkodpushlibrary

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import com.enkod.enkodpushlibrary.EnkodPushLibrary.isAppInforegrounded
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.Preferences.LOAD_TIMEOUT_TAG
import com.enkod.enkodpushlibrary.Preferences.START_AUTO_UPDATE_TAG
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_LAST_TOKEN_UPDATE_TAG
import com.enkod.enkodpushlibrary.Preferences.TIME_VERIFICATION_TAG
import com.enkod.enkodpushlibrary.Variables.defaultImageLoadTimeout
import com.enkod.enkodpushlibrary.Variables.defaultTimeAutoUpdateToken
import com.enkod.enkodpushlibrary.Variables.defaultTimeManualUpdateToken
import com.enkod.enkodpushlibrary.Variables.defaultTimeVerificationToken
import com.enkod.enkodpushlibrary.Variables.millisInHours
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging


class EnkodConnect(

    _account: String?,
    _tokenManualUpdate: Boolean? = false,
    _tokenAutoUpdate: Boolean? = false,
    _timeTokenManualUpdate: Int? = defaultTimeManualUpdateToken,
    _timeTokenAutoUpdate: Int? = defaultTimeAutoUpdateToken,
    _timeTokenVerification: Int? = defaultTimeVerificationToken,
    _imageLoadTimeout: Int? = defaultImageLoadTimeout


) {

    private val account: String
    private val tokenManualUpdate: Boolean
    private val tokenAutoUpdate: Boolean
    private var timeTokenManualUpdate: Int
    private var timeTokenAutoUpdate: Int
    private var timeTokenVerification: Int
    private var imageLoadTimeout: Int


    init {

        account = _account ?: ""
        tokenManualUpdate = _tokenManualUpdate ?: false
        tokenAutoUpdate = _tokenAutoUpdate ?: false

        timeTokenManualUpdate =

            if (_timeTokenManualUpdate != null && _timeTokenManualUpdate > 0) _timeTokenManualUpdate
            else defaultTimeManualUpdateToken

        timeTokenAutoUpdate =

            if (_timeTokenAutoUpdate != null && _timeTokenAutoUpdate > 0) _timeTokenAutoUpdate
            else defaultTimeAutoUpdateToken

        timeTokenVerification =

            if (_timeTokenVerification != null && _timeTokenVerification > 0) _timeTokenVerification
            else defaultTimeVerificationToken

        imageLoadTimeout =

            if (_imageLoadTimeout != null && _imageLoadTimeout > 0) _imageLoadTimeout
            else defaultImageLoadTimeout

    }


    @SuppressLint("BatteryLife")
    fun start(context: Context) {

        logInfo( "user settings: $account, $tokenManualUpdate, $tokenAutoUpdate, $timeTokenManualUpdate, $timeTokenAutoUpdate")

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val preferencesStartTokenAutoUpdate = preferences.getString(START_AUTO_UPDATE_TAG, null)


        preferences.edit()
            .putInt(LOAD_TIMEOUT_TAG, imageLoadTimeout)
            .apply()

        preferences.edit()
            .putInt(TIME_VERIFICATION_TAG, timeTokenVerification)
            .apply()


        if (EnkodPushLibrary.isOnline(context)) {

            EnkodPushLibrary.isOnlineStatus(true)

            try {

                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {

                        return@OnCompleteListener
                    }

                    val token = task.result

                    logInfo("start library with fcm")

                    if (preferencesStartTokenAutoUpdate == null && tokenAutoUpdate) {

                        preferences.edit()

                            .putInt(Preferences.TIME_TOKEN_AUTO_UPDATE_TAG, timeTokenAutoUpdate)
                            .apply()


                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                            TokenAutoUpdate.startTokenAutoUpdateUsingWorkManager(
                                context,
                                timeTokenAutoUpdate
                            )

                        } else {

                            TokenAutoUpdate.startAutoUpdatesUsingJobScheduler(
                                context,
                                timeTokenAutoUpdate
                            )

                        }

                        preferences.edit()

                            .putString(START_AUTO_UPDATE_TAG, Variables.start)
                            .apply()
                    }

                    if (tokenManualUpdate) {

                        tokenUpdate(context, timeTokenManualUpdate)

                    }

                    EnkodPushLibrary.init(context, account, token)

                })

            } catch (e: Exception) {

                EnkodPushLibrary.init(context, account)

                logInfo("start library without fcm")

            }

        } else {

            EnkodPushLibrary.isOnlineStatus(false)

            logInfo("error internet")
        }

    }


    private fun tokenUpdate(context: Context, timeUpdate: Int) {

        val timeUpdateInMillis: Long = (timeUpdate * millisInHours).toLong()

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val timeLastTokenUpdate = preferences.getLong(TIME_LAST_TOKEN_UPDATE_TAG, 0)

        if (isAppInforegrounded()) {

            if (EnkodPushLibrary.isOnline(context)) {

                if (timeLastTokenUpdate > 0) {

                    if ((System.currentTimeMillis() - timeLastTokenUpdate) > timeUpdateInMillis) {

                        logInfo("start manual update in start method")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(
                                Intent(
                                    context,
                                    TokenManualUpdateService::class.java
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

