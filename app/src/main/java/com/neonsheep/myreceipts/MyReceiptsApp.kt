package com.neonsheep.myreceipts

import android.app.Application
import com.neonsheep.myreceipts.data.FailureReports

/** Records a crash as a failure report before Android's own handler ends the app. */
class MyReceiptsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FailureReports.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { FailureReports.recordCrash(error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
