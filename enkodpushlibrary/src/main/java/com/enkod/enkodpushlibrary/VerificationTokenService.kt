package com.enkod.enkodpushlibrary

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.WorkManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class VerificationTokenService : Service() {


    private val TAG = "EnkodPushLibrary"
    private val ACCOUNT_TAG: String = "${TAG}_ACCOUNT"
    private val SESSION_TAG = "${TAG}_SESSION_ID"


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {

        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                EnkodPushLibrary.createdNotificationForNetworkService(applicationContext),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, EnkodPushLibrary.createdNotificationForNetworkService(applicationContext))
        }

        CoroutineScope(Dispatchers.IO).launch {

            delay(1000)

            EnkodPushLibrary.initRetrofit()

            val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            var preferencesAcc = preferences.getString(ACCOUNT_TAG, null)
            val preferencesSession = preferences.getString(SESSION_TAG, null)



            if (preferencesAcc != null && preferencesSession != null) {

                try {

                    FirebaseMessaging.getInstance().token.addOnCompleteListener(
                        OnCompleteListener { task ->

                            if (!task.isSuccessful) {
                                Log.d(
                                    "new_token",
                                    "Fetching FCM registration token failed",
                                    task.exception
                                )
                                return@OnCompleteListener
                            }

                            val currentToken = task.result

                            verificationOfTokenCompliance(
                                applicationContext,
                                preferencesAcc,
                                preferencesSession,
                                currentToken
                            )

                        })

                } catch (e: Exception) {

                    Log.d("doWork", "verificationOfTokenWork_fcm_error")

                }

            }
        }

    }

    internal fun verificationOfTokenCompliance(
        context: Context,
        account: String?,
        session: String?,
        currentToken: String?
    ) {

        EnkodPushLibrary.retrofit.getToken(
            account!!,
            session!!
        ).enqueue(object : Callback<GetTokenResponse> {

            override fun onResponse(
                call: Call<GetTokenResponse>,
                response: Response<GetTokenResponse>
            ) {

                val body = response.body()
                var tokenOnService = ""

                when (body) {
                    null -> return
                    else -> {

                        tokenOnService = body.token

                        Log.d("doWork", "token on fcm     $currentToken")
                        Log.d("doWork", "token on service $tokenOnService")

                        if (tokenOnService == currentToken) {
                            WorkManager.getInstance(context)
                                .cancelUniqueWork("verificationOfTokenWorker")

                            Log.d("doWork", "verificationWorkerStop")
                            Log.d("doWork", "verification successful")

                            stopSelf()

                        } else {
                            Log.d("doWork", "verification notPassed, workerReload")
                            EnkodPushLibrary.init(context, account, currentToken)

                            CoroutineScope(Dispatchers.IO).launch {

                                delay(5000)

                                stopSelf()

                            }
                        }
                    }
                }
            }

            override fun onFailure(call: Call<GetTokenResponse>, t: Throwable) {
                Log.d("doWork", "retrofit_getToken_onFailure $t")
                stopSelf()
            }
        })


    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

}