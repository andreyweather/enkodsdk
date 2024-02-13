package com.enkod.enkodpushlibrary


import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private val TAGL = "EnkodPushLibrary"
private val WORKER_TAG: String = "${TAGL}_WORKER"

class BackgroundTasks(_context: Context) {

    private val context: Context

    init {

        context = _context
    }


    val preferences = context.getSharedPreferences(TAGL, Context.MODE_PRIVATE)


    fun refreshInMemoryWorker(timeUpdate: Long) {


        val workRequest =
            PeriodicWorkRequestBuilder<RefreshAppInMemoryWorkManager>(timeUpdate, TimeUnit.HOURS)
                .build()

        WorkManager

            .getInstance(context)
            .enqueueUniquePeriodicWork(
                "refreshInMemoryWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        preferences.edit()
            .putString(WORKER_TAG, "start")
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


                Log.d("doWork", "refreshInMemoryWorkerStart")

                return Result.success()
            } catch (e: Exception) {
                Log.d("doWork", "RefreshAppInMemoryWorkManager error $e")
                return Result.failure();
            }
        }
    }


    fun startOneTimeWorkerForTokenUpdate() {

        Log.d("doWork", "OneTimeWorkerStart")

        val workRequest = OneTimeWorkRequestBuilder<OneTimeWorkManager>()
            //.setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager

            .getInstance(context)
            .enqueue(workRequest)

        preferences.edit()
            .putString(WORKER_TAG, "start")
            .apply()

    }


    class OneTimeWorkManager(context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {

        fun refreshTokenWorker() {

            val workRequest =

                PeriodicWorkRequestBuilder<UpdateTokenWorker>(15, TimeUnit.MINUTES)
                    .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "refreshToken", ExistingPeriodicWorkPolicy.UPDATE, workRequest
            );
        }

        override fun doWork(): Result {

            try {

                refreshTokenWorker()

                return Result.success()
            } catch (e: Exception) {

                Log.d("doWork", "OneTimeWorkManager error $e")
                return Result.failure();
            }
        }
    }


    class UpdateTokenWorker(context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)

        override fun doWork(): Result {
            try {

                Log.d("doWork", "updateTokenWorkerStart")


                applicationContext.startForegroundService(
                    Intent(
                        applicationContext,
                        UpdateTokenService::class.java
                    )
                )

                return Result.success()
            } catch (e: Exception) {
                Log.d("doWork", "UpdateTokenWorker error $e")
                return Result.failure();
            }
        }
    }


    class verificationOfTokenWorkManager(
        context: Context,
        workerParameters: WorkerParameters
    ) :

        Worker(context, workerParameters) {


        @RequiresApi(Build.VERSION_CODES.O)
        override fun doWork(): Result {

            Log.d("doWork", "verificationWorkerStart")

            applicationContext.startForegroundService(
                Intent(
                    applicationContext,
                    VerificationTokenService::class.java
                )
            )

            return Result.success()

        }
    }

    fun verificationOfTokenWorker(context: Context) {

        val workRequest =
            PeriodicWorkRequestBuilder<verificationOfTokenWorkManager>(15, TimeUnit.MINUTES)
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