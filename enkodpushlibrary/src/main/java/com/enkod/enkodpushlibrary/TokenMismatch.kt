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
import com.enkod.enkodpushlibrary.Preferences
import com.enkod.enkodpushlibrary.Variables
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

internal object TokenMismatch {

    
    internal fun libraryRebootDueToTokenMismatch (context: Context) {

        EnkodPushLibrary.logInfo("start library reboot due to token mismatch ")


        val workRequest = OneTimeWorkRequestBuilder<oneTimeWorkerForLibraryReboot>()

            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager

            .getInstance(context)
            .enqueue(workRequest)

    }

    class oneTimeWorkerForLibraryReboot (context: Context, workerParameters: WorkerParameters) :

        Worker(context, workerParameters) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            startPeriodicalWorkerForLibraryReboot(applicationContext)

            return Result.success()
        }
    }


    internal fun startPeriodicalWorkerForLibraryReboot(context: Context) {

        EnkodPushLibrary.logInfo("start recurring library reboot")

        val preferences = context.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)

        val preferencesTimeVerification: Int? =
            preferences.getInt(Preferences.TIME_VERIFICATION_TAG,
                Variables.defaultTimeVerificationToken
            )

        val time = preferencesTimeVerification ?: Variables.defaultTimeVerificationToken

        val constraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =

            PeriodicWorkRequestBuilder<LibraryReboot>(
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

    class LibraryReboot (
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

                EnkodPushLibrary.logInfo("token mismatch library reboot")
                EnkodPushLibrary.initPreferences(applicationContext)
                EnkodPushLibrary.initRetrofit(applicationContext)

                val preferences =
                    applicationContext.getSharedPreferences(Preferences.TAG, Context.MODE_PRIVATE)
                var preferencesAcc = preferences.getString(Preferences.ACCOUNT_TAG, null)

                if (preferencesAcc != null) {

                    try {

                        FirebaseMessaging.getInstance().token.addOnCompleteListener(
                            OnCompleteListener { task ->

                                if (!task.isSuccessful) {

                                    return@OnCompleteListener
                                }

                                val currentToken = task.result

                                EnkodPushLibrary.init(applicationContext, preferencesAcc, currentToken)

                            })

                    } catch (e: Exception) {

                        EnkodPushLibrary.logInfo("error in token mismatch library reboot function$e")

                    }
                }

            return Result.success()

        }
    }
}