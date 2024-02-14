package com.enkod.enkodpushlibrary


import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Preferences.WORKER_TAG
import com.enkod.enkodpushlibrary.Variables.start
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit


class BackgroundTasks(_context: Context) {

    private val context: Context

    init {

        context = _context
    }

    val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)


    fun refreshInMemoryWorker(timeUpdate: Int) {

        val workRequest =
            PeriodicWorkRequestBuilder<RefreshAppInMemoryWorkManager>(timeUpdate.toLong(), TimeUnit.HOURS)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "refreshInMemoryWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        preferences.edit()

            .putString(WORKER_TAG, start)
            .apply()

    }

    class RefreshAppInMemoryWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :
        Worker(context, workerParameters) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            try {

                applicationContext.startForegroundService(
                    Intent(
                        applicationContext,
                        RefreshAppInMemoryService::class.java
                    )
                )

                return Result.success()

            } catch (e: Exception) {

                return Result.failure();
            }
        }
    }


    class verificationOfTokenWorkManager (
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            CoroutineScope(Dispatchers.IO).launch {

                delay(1000)

                EnkodPushLibrary.initRetrofit(applicationContext)

                val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
                var preferencesAcc = preferences.getString(Preferences.ACCOUNT_TAG, null)
                val preferencesSession = preferences.getString(Preferences.SESSION_ID_TAG, null)


                if (preferencesAcc != null && preferencesSession != null) {

                    try {

                        FirebaseMessaging.getInstance().token.addOnCompleteListener(
                            OnCompleteListener { task ->

                                if (!task.isSuccessful) {

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

                        EnkodPushLibrary.logInfo("error get token in VerificationTokenService $e")

                    }
                }
            }

            return Result.success()

        }
    }

    fun verificationOfTokenWorker(context: Context) {

        val preferencesTimeVerification: Int?  = preferences.getInt(Preferences.START_TIMER_TAG, 1)

        val time = preferencesTimeVerification ?: 1

        val workRequest =
            PeriodicWorkRequestBuilder<verificationOfTokenWorkManager>(time.toLong(), TimeUnit.HOURS)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "verificationOfTokenWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

    }
}

internal fun verificationOfTokenCompliance(
    context: Context,
    account: String?,
    session: String?,
    currentToken: String?
) {

    val account = account ?: ""
    val session = session ?: ""

    EnkodPushLibrary.retrofit.getToken(
        account,
        session
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


                    if (tokenOnService == currentToken) {

                        WorkManager.getInstance(context)

                            .cancelUniqueWork("verificationOfTokenWorker")

                        EnkodPushLibrary.logInfo("token verification true")

                    } else {

                        EnkodPushLibrary.init(context, account, currentToken)

                        CoroutineScope(Dispatchers.IO).launch {

                            EnkodPushLibrary.logInfo("token verification false")


                        }
                    }
                }
            }
        }

        override fun onFailure(call: Call<GetTokenResponse>, t: Throwable) {

            EnkodPushLibrary.logInfo("token verification error retrofit $t")

            return

        }
    })
}