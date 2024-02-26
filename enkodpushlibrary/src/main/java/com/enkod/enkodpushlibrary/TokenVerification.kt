package com.example.enkodpushlibrary

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.enkodpushlibrary.EnkodPushLibrary
import com.enkod.enkodpushlibrary.GetTokenResponse
import com.enkod.enkodpushlibrary.Preferences
import com.enkod.enkodpushlibrary.Variables.defaultTimeVerificationToken
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

internal object TokenVerification {

    class verificationOfTokenWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            verificationExpedition(applicationContext)

            return Result.success()

        }
    }


    internal fun verificationOfTokenWorker(context: Context) {

        val preferences = context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)

        val preferencesTimeVerification: Int? =
            preferences.getInt(Preferences.TIME_VERIFICATION_TAG, defaultTimeVerificationToken)

        val time = preferencesTimeVerification ?: defaultTimeVerificationToken

        val constraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =

            PeriodicWorkRequestBuilder<verificationOfTokenWorkManager>(
                time.toLong(),
                TimeUnit.HOURS
            )

                .setConstraints(constraint)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "verificationOfTokenWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

    }

    fun verificationExpedition(context: Context) {

        val workRequest = OneTimeWorkRequestBuilder<VerificationExpeditionWorker>()

            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager

            .getInstance(context)
            .enqueue(workRequest)

    }

    class VerificationExpeditionWorker(context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            CoroutineScope(Dispatchers.IO).launch {

                delay(1000)

                EnkodPushLibrary.initPreferences(applicationContext)
                EnkodPushLibrary.initRetrofit(applicationContext)

                val preferences =
                    applicationContext.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)
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
                            Log.d("verification", "ok")

                        } else {

                            EnkodPushLibrary.init(context, account, currentToken)

                            EnkodPushLibrary.logInfo("token verification false")

                        }
                    }
                }
            }

            override fun onFailure(call: Call<GetTokenResponse>, t: Throwable) {

                EnkodPushLibrary.logInfo("token verification error retrofit $t")
                Log.d("verification", "error")

                return

            }
        })
    }


}