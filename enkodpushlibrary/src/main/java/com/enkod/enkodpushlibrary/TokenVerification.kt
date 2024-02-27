package com.example.enkodpushlibrary

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.enkodpushlibrary.EnkodPushLibrary
import com.enkod.enkodpushlibrary.GetTokenResponse
import com.enkod.enkodpushlibrary.Preferences
import com.enkod.enkodpushlibrary.Variables
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

internal object TokenVerification {



    internal fun startVerificationOfToken (context: Context) {

        EnkodPushLibrary.logInfo("start task token verification ")


        val workRequest = OneTimeWorkRequestBuilder<oneTimeWorkerForTokenVerification>()

            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager

            .getInstance(context)
            .enqueue(workRequest)

    }

    class oneTimeWorkerForTokenVerification (context: Context, workerParameters: WorkerParameters) :

        Worker(context, workerParameters) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            startPeriodicalWorkerForTokenVerification(applicationContext)

            return Result.success()
        }
    }


    internal fun startPeriodicalWorkerForTokenVerification(context: Context) {

        EnkodPushLibrary.logInfo("start verification function")

        val preferences = context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)

        val preferencesTimeVerification: Int? =
            preferences.getInt(Preferences.TIME_VERIFICATION_TAG,
                Variables.defaultTimeVerificationToken
            )

        val time = preferencesTimeVerification ?: Variables.defaultTimeVerificationToken

        EnkodPushLibrary.logInfo(time.toString())

        val constraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

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


    class verificationOfTokenWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            CoroutineScope(Dispatchers.IO).launch {

                EnkodPushLibrary.logInfo("verification in process")

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


                        } else {

                            EnkodPushLibrary.init(context, account, currentToken)

                            EnkodPushLibrary.logInfo("token verification false reload Enkod library")

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
}