package com.example.pardadns

import android.app.Application
import com.example.pardadns.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PardaDnsApplication : Application() {

    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        // Connect to Play and check purchases so Pro is current before any screen needs it.
        billingManager.start()
    }
}
