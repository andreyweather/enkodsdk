package com.enkod.enkodpushlibrary

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.enkodpushlibrary.Variables.defaultTimeVerificationToken
import com.enkod.enkodpushlibrary.Variables.millisInHours
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

internal object VerificationOfTokenCompliance {

    
    internal fun startVerificationTokenUsingWorkManager (context: Context) {

        EnkodPushLibrary.logInfo("start verification function using workManager")

        val preferences = context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)

        val preferencesTimeVerification: Int? =
            preferences.getInt(
                Preferences.TIME_VERIFICATION_TAG,
                Variables.defaultTimeVerificationToken
            )

        val time = preferencesTimeVerification ?: Variables.defaultTimeVerificationToken


        val constraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =

            PeriodicWorkRequestBuilder<verificationOfTokenWorkManager>(
                time.toLong(),
                TimeUnit.HOURS
            )
                .setInitialDelay(1, TimeUnit.MINUTES)
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


            EnkodPushLibrary.logInfo("verification in process using workManager")

            preparationVerification(applicationContext)

            return Result.success()

        }
    }


    internal fun startVerificationTokenUsingJobScheduler (context: Context) {


        val jobInfo = JobInfo.Builder(2, ComponentName(context, StartTokenVerificationJobService::class.java))
            .setPersisted(true)
            .setMinimumLatency(60000)
            .build()
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }


    class StartTokenVerificationJobService : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {

            EnkodPushLibrary.logInfo("token start verification JobScheduler onStart")

            doBackgroundWork(params)

            return true
        }

        private fun doBackgroundWork(params: JobParameters) {

            verificationTokenUsingJobScheduler(applicationContext)

            jobFinished(params, true)

        }

        override fun onStopJob(params: JobParameters): Boolean {

            EnkodPushLibrary.logInfo("token start  verification JobScheduler onStop")

            return true
        }
    }

    internal fun verificationTokenUsingJobScheduler (context: Context) {

        val preferences = context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)

        val preferencesTimeVerification: Int? =
            preferences.getInt(
                Preferences.TIME_VERIFICATION_TAG,
                defaultTimeVerificationToken
            )

        val time = preferencesTimeVerification ?: defaultTimeVerificationToken
        val timeInMillis = time * millisInHours

        val jobInfo = JobInfo.Builder(2, ComponentName(context, TokenVerificationJobService::class.java))
            .setPersisted(true)
            .setPeriodic(timeInMillis.toLong())
            .build()
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }


    class TokenVerificationJobService : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {

            EnkodPushLibrary.logInfo("token verification JobScheduler onStart")

            doBackgroundWork(params)

            return true
        }

        private fun doBackgroundWork(params: JobParameters) {


            preparationVerification(applicationContext)

            jobFinished(params, true)

        }

        override fun onStopJob(params: JobParameters): Boolean {

            EnkodPushLibrary.logInfo("token verification JobScheduler onStop")

            return true
        }
    }


    fun preparationVerification (context: Context) {

        EnkodPushLibrary.initPreferences(context)
        EnkodPushLibrary.initRetrofit(context)

        val preferences =
            context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)
        val preferencesAcc = preferences.getString(Preferences.ACCOUNT_TAG, null)
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
                            context,
                            preferencesAcc,
                            preferencesSession,
                            currentToken
                        )

                    })

            } catch (e: Exception) {

                EnkodPushLibrary.logInfo("error in verification preparation: $e")

            }
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

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            WorkManager.getInstance(context)
                                .cancelUniqueWork("verificationOfTokenWorker")

                            }else {

                                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?

                                when (scheduler){

                                    null -> return
                                    else -> scheduler.cancel(2)
                                }

                            }

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


