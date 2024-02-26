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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initPreferences
import com.enkod.enkodpushlibrary.EnkodPushLibrary.initRetrofit
import com.enkod.enkodpushlibrary.EnkodPushLibrary.logInfo
import com.enkod.enkodpushlibrary.EnkodPushLibrary.startTokenAutoUpdateObserver
import com.enkod.enkodpushlibrary.Preferences.TAG
import com.enkod.enkodpushlibrary.Variables.defaultTimeAutoUpdateToken
import com.enkod.enkodpushlibrary.Variables.millisInHours
import com.example.enkodpushlibrary.TokenVerification.verificationOfTokenWorker
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit


internal object TokenAutoUpdate {


    internal fun startAutoUpdatesUsingWorkManager(context: Context, time: Int) {

        logInfo( "start one time work for token auto update")


        val workRequest = OneTimeWorkRequestBuilder<oneTimeStartTokenAutoUpdateWorker>()

            .setInitialDelay(time.toLong(), TimeUnit.HOURS)
            .build()

        WorkManager

            .getInstance(context)
            .enqueue(workRequest)

    }

    class oneTimeStartTokenAutoUpdateWorker(context: Context, workerParameters: WorkerParameters) :

        Worker(context, workerParameters) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            val preferences = applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            val setTimeTokenUpdate: Int? = preferences.getInt(
                Preferences.TIME_TOKEN_AUTO_UPDATE_TAG,
                defaultTimeAutoUpdateToken
            )

            when (setTimeTokenUpdate) {
                null -> TokenAutoUpdateWork(applicationContext, defaultTimeAutoUpdateToken)
                else -> TokenAutoUpdateWork(applicationContext, setTimeTokenUpdate)
            }


            return Result.success()
        }
    }

    fun TokenAutoUpdateWork(context: Context, time: Int) {

        logInfo("token auto update work")

        val constraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =

            PeriodicWorkRequestBuilder<TokenAutoUpdateWorkManager>(
                time.toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraint)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "tokenAutoUpdateWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

    }

    class TokenAutoUpdateWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        override fun doWork(): Result {

            tokenUpdate(applicationContext)

            return Result.success()

        }
    }

    internal fun startAutoUpdatesUsingJobScheduler (context: Context, time: Int) {

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)

        var timeAutoUpdate = defaultTimeAutoUpdateToken* millisInHours

        val setTimeTokenUpdate: Int? =
            preferences.getInt(Preferences.TIME_TOKEN_AUTO_UPDATE_TAG, defaultTimeAutoUpdateToken)

        if (setTimeTokenUpdate != null && setTimeTokenUpdate > 0) {
            timeAutoUpdate = setTimeTokenUpdate*millisInHours
        }
        else {
            if (time > 0) {
                timeAutoUpdate = time*millisInHours
            }
        }

        val jobInfo = JobInfo.Builder(1, ComponentName(context, TokenAutoUpdateJobService::class.java))
            .setPersisted(true)
            .setPeriodic(timeAutoUpdate.toLong())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(jobInfo)
    }


    class TokenAutoUpdateJobService : JobService() {
        override fun onStartJob(params: JobParameters): Boolean {

            logInfo( "token auto update JobScheduler onStart")

            doBackgroundWork(params)

            return true
        }

        private fun doBackgroundWork(params: JobParameters) {

            tokenUpdate(applicationContext)
            jobFinished(params, true)

        }

        override fun onStopJob(params: JobParameters): Boolean {

            logInfo( "token auto update JobScheduler onStop")

            return true
        }
    }


    internal fun tokenUpdate(context: Context) {

        startTokenAutoUpdateObserver.value = true

        initPreferences(context)
        initRetrofit(context)

        val preferences = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        var preferencesAcc = preferences.getString(Preferences.ACCOUNT_TAG, null)

        val timeLastTokenUpdate: Long? =
            preferences.getLong(Preferences.TIME_LAST_TOKEN_UPDATE_TAG, 0)

        val setTimeTokenUpdate: Int? =
            preferences.getInt(Preferences.TIME_TOKEN_AUTO_UPDATE_TAG, defaultTimeAutoUpdateToken)

        val timeAutoUpdate =
            (setTimeTokenUpdate ?: defaultTimeAutoUpdateToken) * millisInHours


        if (preferencesAcc != null) {

            if (timeLastTokenUpdate != null && timeLastTokenUpdate > 0) {

                if ((System.currentTimeMillis() - timeLastTokenUpdate) >= timeAutoUpdate) {

                    try {

                        FirebaseMessaging.getInstance().deleteToken()

                            .addOnCompleteListener { task ->

                                if (task.isSuccessful) {

                                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->

                                        if (task.isSuccessful) {

                                            val token = task.result

                                            EnkodPushLibrary.init(
                                                context,
                                                preferencesAcc,
                                                token
                                            )

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                                                verificationOfTokenWorker(
                                                    context
                                                )
                                            }

                                            logInfo( "token update in auto update function")

                                        } else {

                                            logInfo("error get new token in token auto update function")

                                        }
                                    }

                                } else {

                                    logInfo("error deletion token in token auto update function")

                                }
                            }

                    } catch (e: Exception) {

                        logInfo("error in  token auto update function: $e")

                    }
                } else {
                    return
                }
            }
        }
    }
}