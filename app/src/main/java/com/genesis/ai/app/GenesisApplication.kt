package com.genesis.ai.app

import android.app.Application
import com.genesis.ai.app.service.GenesisAIService
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics


class GenesisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase with existing catalog.
        // If you have google-services.json in your app/ directory and the 'google-services' plugin
        // applied in build.gradle.kts, this is usually sufficient and reads config automatically.
        FirebaseApp.initializeApp(this)

        // Initialize Crashlytics
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true


        // Initialize Firebase Auth - simply getting the instance is usually enough after FirebaseApp init
        // No explicit 'initialize(this)' call is typically needed here.
        FirebaseAuth.getInstance()
        // You might use authInstance.currentUser to check login state, etc.

        // Initialize Firebase Analytics - getting the instance is enough after FirebaseApp init
        FirebaseAnalytics.getInstance(this).setUserProperty("app_version", BuildConfig.VERSION_NAME)

        // You might also need to get instances for other Firebase services if you use them:
        // val firestoreInstance = FirebaseFirestore.getInstance()
        // val messagingInstance = FirebaseMessaging.getInstance()
        // val databaseInstance = FirebaseDatabase.getInstance()


        // Start the AI service when the application starts
        GenesisAIService.startService(this)
    }
}