package com.genesis.ai.app.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.genesis.ai.app.R
import com.genesis.ai.app.data.model.GenesisRepositoryNew
import com.genesis.ai.app.data.model.ImportResponse
import com.genesis.ai.app.data.model.MessageRequest
import com.genesis.ai.app.data.model.MessageResponse
import com.genesis.ai.app.service.GenesisAIService
import com.google.android.material.switchmaterial.SwitchMaterial
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

private const val MIME_TYPE = "application/octet-stream"
private val FILE_PICKER_MIME_TYPES = arrayOf("*/*")

class MainActivity : AppCompatActivity() {

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GenesisAIService.PROACTIVE_MESSAGE_ACTION) {
                val message = intent.getStringExtra("message") ?: return
                updateChatLog("Genesis", message)
            }
        }
    }
    private lateinit var chatLog: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var rootToggle: SwitchMaterial
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var fileManagerButton: Button
    private lateinit var aiQuestions: TextView

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val fileBytes = inputStream?.readBytes() ?: return@registerForActivityResult
                    val reqBody = fileBytes.toRequestBody(MIME_TYPE.toMediaTypeOrNull())
                    val filePart =
                        MultipartBody.Part.createFormData("file", "importedfile", reqBody)
                    GenesisRepositoryNew.api.importFile(filePart)
                        .enqueue(object : Callback<ImportResponse> {
                            override fun onResponse(
                                call: Call<ImportResponse>,
                                response: Response<ImportResponse>,
                            ) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import: ${response.body()?.status}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onFailure(call: Call<ImportResponse>, t: Throwable) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error reading file: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val fileManagerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val fileUri = result.data?.data
                fileUri?.let { uri ->
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val fileContent = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                        messageInput.setText(fileContent)
                        Toast.makeText(this, "File loaded successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }

    override fun onUserInteraction() {
        super.onUserInteraction()
        GenesisAIService.updateUserActivity()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(GenesisAIService.PROACTIVE_MESSAGE_ACTION)
        try {
            if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                val flags = if (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    RECEIVER_EXPORTED
                } else {
                    @Suppress("DEPRECATION")
                    RECEIVER_EXPORTED
                }
                this.registerReceiver(messageReceiver, filter, flags)
            } else {
                @Suppress("DEPRECATION")
                this.registerReceiver(messageReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error registering receiver: ${e.message}")
            e.printStackTrace()
            try {
                @Suppress("DEPRECATION")
                this.registerReceiver(messageReceiver, filter)
            } catch (e2: Exception) {
                Log.e("MainActivity", "Fallback receiver registration failed: ${e2.message}")
                e2.printStackTrace()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(messageReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        } catch (e: IllegalArgumentException) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        rootToggle = findViewById(R.id.rootToggle)
        importButton = findViewById(R.id.importButton)
        exportButton = findViewById(R.id.exportButton)
        fileManagerButton = findViewById(R.id.fileManagerButton)
        aiQuestions = findViewById(R.id.aiQuestions)

        sendButton.setOnClickListener { sendMessage() }
        exportButton.setOnClickListener { exportChatViaFileManager() }
        importButton.setOnClickListener { checkStoragePermissionAndPickFile() }
        fileManagerButton.setOnClickListener { openFileManager() }

        initializeService()
    }

    private fun initializeService() {
        try {
            GenesisAIService.startService(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Background service could not start", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermissionAndPickFile() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED ||
            VERSION.SDK_INT >= VERSION_CODES.Q
        ) {
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                0
            )
            Toast.makeText(
                this,
                "Please grant storage permission to import files",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openFileManager() {
        try {
            val intent = Intent(this, FileManagerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            fileManagerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening file manager: ${e.message}")
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Error opening file manager: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            e.printStackTrace()
        }
    }

    private fun exportChatViaFileManager() {
        val chatContent = "=== GenComm Chat Export ===\n\n${chatLog.text}"
        val intent = Intent(this, FileManagerActivity::class.java).apply {
            action = FileManagerActivity.ACTION_EXPORT_CHAT
            putExtra(FileManagerActivity.EXTRA_CHAT_CONTENT, chatContent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun getOrCreateUserId(): String {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var userId = sharedPrefs.getString("user_id", null)

        if (userId == null) {
            userId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("user_id", userId).apply()
        }

        return userId
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return

        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        val userId = getOrCreateUserId()

        Log.d("MainActivity", "Sending message: $message with userId: $userId")

        val request = MessageRequest(
            message = message,
            userId = userId
        )

        GenesisRepositoryNew.api.sendMessage(request)
            .enqueue(object : Callback<MessageResponse> {
                override fun onResponse(
                    call: Call<MessageResponse>,
                    response: Response<MessageResponse>,
                ) {
                    runOnUiThread {
                        sendButton.isEnabled = true
                        sendButton.text = "Send"

                        if (response.isSuccessful) {
                            val responseBody = response.body()
                            if (responseBody != null) {
                                Log.d(
                                    "MainActivity",
                                    "Message sent successfully: ${responseBody.id}"
                                )
                                updateChatLog(
                                    message,
                                    responseBody.message
                                )
                            } else {
                                Log.e("MainActivity", "Empty response body")
                                updateChatLog(message, "Error: Empty response from server")
                            }
                        } else {
                            val errorMessage =
                                "Failed to send message: ${response.code()} ${response.message()}"
                            Log.e("MainActivity", errorMessage)
                            Toast.makeText(
                                this@MainActivity,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                    runOnUiThread {
                        sendButton.isEnabled = true
                        sendButton.text = "Send"

                        val errorMessage = "Error: ${t.message}"
                        Log.e("MainActivity", errorMessage, t)
                        Toast.makeText(
                            this@MainActivity,
                            errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
    }

    private fun updateChatLog(userMessage: String, aiResponse: String) {
        chatLog.append("You: $userMessage\n")
        chatLog.append("AI: $aiResponse\n\n")
        messageInput.text.clear()
    }
}