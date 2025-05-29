package com.genesis.ai.app.data.model

import android.util.Log // Import Log for logging purposes
import com.genesis.ai.app.data.api.ApiService
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import java.util.UUID // Import UUID for generating IDs
import java.util.concurrent.TimeUnit

object GenesisRepositoryNew {
    // Set this to true to use mock responses
    private const val USE_MOCK_RESPONSES = false // Changed to false to use actual API calls

    // Base URL for the API - can be changed in app settings
    // It's generally better to fetch this from preferences or a build config,
    // rather than hardcoding. For now, it's mutable.
    private var baseUrl = "http://10.0.2.2:8000/" // Default to local development server

    // Mock responses
    private val mockResponses = listOf(
        "I'm currently running in offline mode. The server appears to be down.",
        "This is a mock response. Please check your internet connection.",
        "The Genesis AI service is currently unavailable. Using local responses.",
        "I can still help you with basic tasks while offline."
    )

    // API instance caching
    @Volatile // Ensures visibility across threads
    private var apiInstance: ApiService? = null

    // Using lazy initialization with a double-checked lock for thread safety
    private fun createApiInstance(): ApiService {
        return apiInstance ?: synchronized(this) {
            apiInstance ?: buildRetrofit().also { apiInstance = it }
        }
    }

    // Encapsulate Retrofit building for cleaner code
    private fun buildRetrofit(): ApiService {
        // Add logging interceptor for debugging
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Create OkHttpClient with timeouts and interceptors
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                try {
                    val request = chain.request()
                    val response = chain.proceed(request)

                    // Log request and response details
                    Log.d("API", "Request: ${request.method} ${request.url}")
                    Log.d("API", "Response Code: ${response.code}")

                    if (!response.isSuccessful) {
                        Log.e("API", "Request failed with code: ${response.code}")
                        // It's important to consume the response body even if it's an error
                        // to prevent connection leaks. peekBody is safe for this.
                        Log.e("API", "Response body: ${response.peekBody(1024)?.string()}")
                    }

                    response
                } catch (e: Exception) {
                    Log.e("API", "Network error: ${e.message}", e)
                    throw e
                }
            }
            .build()

        // Create Retrofit instance
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    val api: ApiService
        get() = if (USE_MOCK_RESPONSES) {
            object : ApiService {
                override fun sendMessage(@Body message: MessageRequest): Call<MessageResponse> =
                    createMockCall {
                        MessageResponse(
                            id = UUID.randomUUID().toString(), // Resolved TODO
                            message = mockResponses.random(),
                            userId = message.userId, // Use the user ID from the request
                            timestamp = System.currentTimeMillis() // Resolved TODO
                        )
                    }

                @Multipart
                override fun importFile(@Part file: MultipartBody.Part): Call<ImportResponse> =
                    createMockCall { ImportResponse(status = "success") }

                override fun toggleRoot(@Body request: RootToggleRequest): Call<RootToggleResponse> =
                    createMockCall { RootToggleResponse("success") }

                override fun getAiQuestions(): Call<AskResponse> =
                    createMockCall {
                        AskResponse(
                            questions = listOf(
                                "Question 1 (Mock)",
                                "Question 2 (Mock)",
                                "Question 3 (Mock)"
                            ),
                            status = "success"
                        )
                    }

                // Task synchronization method
                override fun syncTasks(@Body request: SyncRequest): Call<SyncResponse> {
                    return createMockCall {
                        SyncResponse(
                            status = "success",
                            message = "Tasks synchronized successfully (mock)",
                            syncedTasks = request.tasks, // Return the tasks sent in the request for mock purposes
                            serverTime = System.currentTimeMillis()
                        )
                    }
                }

                private inline fun <T> createMockCall(crossinline response: () -> T): Call<T> {
                    return object : Call<T> {
                        override fun execute(): Response<T> = Response.success(response())
                        override fun enqueue(callback: Callback<T>) {
                            // Simulate network delay for mock responses
                            Thread {
                                try {
                                    Thread.sleep(500) // Simulate 500ms delay
                                    callback.onResponse(this, Response.success(response()))
                                } catch (e: InterruptedException) {
                                    callback.onFailure(this, e)
                                }
                            }.start()
                        }

                        override fun isExecuted(): Boolean = false
                        override fun cancel() {}
                        override fun isCanceled(): Boolean = false
                        override fun request(): Request =
                            Request.Builder().url("https://mock.url").build()

                        override fun clone(): Call<T> = this
                        override fun timeout(): Timeout = Timeout.NONE // No timeout for mock calls
                    }
                }
            }
        } else {
            createApiInstance()
        }

    /**
     * Updates the base URL for API requests and recreates the API instance
     * @param newUrl The new base URL to use for API requests
     * @return Boolean indicating if the URL was updated
     */
    fun updateBaseUrl(newUrl: String): Boolean {
        return if (baseUrl != newUrl) {
            baseUrl = newUrl.trimEnd('/') + "/"  // Ensure proper URL format
            apiInstance = null  // Force recreation of API instance on next access
            true
        } else {
            false
        }
    }
}