package com.genesis.ai.app.data

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils.TruncateAt
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genesis.ai.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {
    private val fileLoadScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLoadJob: Job? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSelect: Button
    private lateinit var btnExport: Button
    private lateinit var btnCancel: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var currentPathText: TextView
    private lateinit var currentDirectory: File
    private var selectedFile: File? = null
    private var chatContentToExport: String? = null

    companion object {
        const val ACTION_EXPORT_CHAT = "com.genesis.ai.app.data.ACTION_EXPORT_CHAT"
        const val EXTRA_CHAT_CONTENT = "com.genesis.ai.app.data.EXTRA_CHAT_CONTENT"
        private const val REQUEST_MANAGE_STORAGE = 1000
        private const val EXPORT_FILE_REQUEST = 1001
        private const val PICK_FILE_REQUEST = 1002
    }

    private val createFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            uri?.let {
                if (chatContentToExport != null) {
                    exportContentToFile(chatContentToExport!!, it)
                    chatContentToExport = null
                } else {
                    exportContentToFile(selectedFile?.readText() ?: "", it)
                }
            }
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importFileFromUri(it) }
        }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            when (intent.action) {
                ACTION_EXPORT_CHAT -> {
                    createFile.launch("text/plain")
                }

                else -> {
                    loadFiles(currentDirectory)
                }
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            when (intent.action) {
                ACTION_EXPORT_CHAT -> {
                    createFile.launch("text/plain")
                }

                else -> {
                    loadFiles(currentDirectory)
                }
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.rvFiles)
        btnSelect = findViewById(R.id.btnSelect)
        btnExport = findViewById(R.id.btnExportFile)
        btnCancel = findViewById(R.id.btnCancel)
        btnSettings = findViewById(R.id.btnSettings)
        currentPathText = findViewById(R.id.tvCurrentPath)

        if (intent.action == ACTION_EXPORT_CHAT) {
            chatContentToExport = intent.getStringExtra(EXTRA_CHAT_CONTENT)
            btnSelect.visibility = View.GONE
            btnExport.text = getString(R.string.export_chat)
            checkStoragePermissionForExport()
        } else {
            btnSelect.setOnClickListener {
                selectedFile?.let { file ->
                    val resultIntent = Intent().apply {
                        data = Uri.fromFile(file)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } ?: run {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
                }
            }
            btnExport.setOnClickListener {
                if (selectedFile != null) {
                    prepareExport()
                } else {
                    createFile.launch("*/*")
                }
            }
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, com.genesis.ai.app.ui.SettingsActivity::class.java))
        }

        setupRecyclerView()

        currentDirectory = Environment.getExternalStorageDirectory()
        if (intent.action != ACTION_EXPORT_CHAT) {
            checkStoragePermissionForLoad()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FileAdapter(emptyList()) { file ->
            if (file.isDirectory) {
                loadFiles(file)
                selectedFile = null
                btnSelect.isEnabled = false
                (recyclerView.adapter as? FileAdapter)?.setSelectedFile(null)
            } else {
                selectedFile = file
                btnSelect.isEnabled = true
                (recyclerView.adapter as? FileAdapter)?.setSelectedFile(file)
            }
        }
    }

    private fun loadFiles(directory: File) {
        currentLoadJob?.cancel()

        currentPathText.text = getString(R.string.loading)

        currentLoadJob = fileLoadScope.launch {
            try {
                if (directory.exists() && directory.isDirectory) {
                    currentDirectory = directory

                    val files = withContext(Dispatchers.IO) {
                        directory.listFiles()?.toList() ?: emptyList()
                    }

                    withContext(Dispatchers.Main) {
                        currentPathText.text = directory.absolutePath
                        (recyclerView.adapter as? FileAdapter)?.updateFiles(files)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FileManagerActivity,
                            getString(R.string.directory_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FileManagerActivity,
                        getString(R.string.error_loading_files, e.localizedMessage),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun importFileFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(uri) ?: "imported_file_${System.currentTimeMillis()}"
                val file = File(currentDirectory, fileName)

                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                Toast.makeText(this, "File imported successfully", Toast.LENGTH_SHORT).show()
                loadFiles(currentDirectory)
            }
        } catch (e: Exception) {
            val errorMsg = "Error reading file: ${e.message ?: "Unknown error"}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareExport() {
        if (selectedFile != null) {
            createFile.launch(getMimeType(selectedFile!!.extension))
        } else {
            Toast.makeText(this, "No file selected for export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportContentToFile(content: String, uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            Toast.makeText(this, "File exported successfully", Toast.LENGTH_SHORT).show()
            loadFiles(currentDirectory)
        } catch (e: Exception) {
            val errorMsg = "Error writing file: ${e.message ?: "Unknown error"}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermissionForLoad() {
        val permissionsToRequest = getRequiredPermissionsForLoad()
        if (permissionsToRequest.isEmpty() || hasAllPermissions(permissionsToRequest)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestManageExternalStoragePermission()
            } else {
                loadFiles(currentDirectory)
            }
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun checkStoragePermissionForExport() {
        val permissionsToRequest = getRequiredPermissionsForExport()
        if (permissionsToRequest.isEmpty() || hasAllPermissions(permissionsToRequest)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createFile.launch("text/plain")
            } else {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    createFile.launch("text/plain")
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun getRequiredPermissionsForLoad(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    private fun getRequiredPermissionsForExport(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            emptyList()
        } else {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestManageExternalStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse(
                String.format(
                    "package:%s",
                    applicationContext.packageName
                )
            )
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    loadFiles(currentDirectory)
                } else {
                    Toast.makeText(
                        this,
                        "Manage External Storage permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                    showPermissionDeniedDialog()
                }
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permission is required to manage files")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Storage permission is required to manage files. The app will now exit.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getMimeType(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }

    override fun onDestroy() {
        super.onDestroy()
        currentLoadJob?.cancel()
        fileLoadScope.cancel()
    }
}

class FileAdapter(
    private var files: List<File>,
    private val onFileClick: (File) -> Unit,
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var selectedFile: File? = null

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById<TextView>(R.id.tvFileName).apply {
            isSingleLine = true
            ellipsize = TruncateAt.END
        }
        val fileInfo: TextView = view.findViewById<TextView>(R.id.tvFileInfo).apply {
            isSingleLine = true
            ellipsize = TruncateAt.END
        }
        val fileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
    }

    private val fileComparator = compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }

    fun updateFiles(newFiles: List<File>) {
        val oldList = files
        files = newFiles.sortedWith(fileComparator)

        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object :
            androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = files.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldList[oldPos].absolutePath == files[newPos].absolutePath
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldFile = oldList[oldPos]
                val newFile = files[newPos]
                return oldFile.lastModified() == newFile.lastModified() &&
                        oldFile.length() == newFile.length() &&
                        oldFile.name == newFile.name
            }
        })

        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectedFile(file: File?) {
        val previousSelected = selectedFile
        selectedFile = file

        previousSelected?.let { oldFile ->
            val oldPos = files.indexOfFirst { it.absolutePath == oldFile.absolutePath }
            if (oldPos != -1) notifyItemChanged(oldPos)
        }

        selectedFile?.let { newFile ->
            val newPos = files.indexOfFirst { it.absolutePath == newFile.absolutePath }
            if (newPos != -1) notifyItemChanged(newPos)
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FileViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    private var lastClickTime: Long = 0

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        val isSelected = file == selectedFile

        holder.fileName.text = file.name

        val fileInfo = StringBuilder()
        if (file.isFile) {
            fileInfo.append(formatFileSize(file.length()))
            fileInfo.append(" • ")
        }
        fileInfo.append(
            SimpleDateFormat("MMM d,yyyy", Locale.getDefault())
                .format(Date(file.lastModified()))
        )

        holder.fileInfo.text = fileInfo

        val iconRes = when {
            file.isDirectory -> android.R.drawable.ic_menu_gallery
            file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp") ->
                android.R.drawable.ic_menu_report_image

            file.extension.lowercase() in listOf(
                "pdf",
                "doc",
                "docx",
                "xls",
                "xlsx"
            ) -> android.R.drawable.ic_menu_agenda

            file.extension.lowercase() in listOf(
                "zip",
                "rar",
                "7z",
                "tar",
                "gz"
            ) -> android.R.drawable.extracted()

            file.extension.lowercase() in listOf(
                "mp3",
                "wav",
                "aac"
            ) -> android.R.drawable.ic_media_play

            file.extension.lowercase() in listOf(
                "mp4",
                "mov",
                "avi"
            ) -> android.R.drawable.ic_media_play

            file.extension.lowercase() in listOf(
                "txt",
                "log",
                "json",
                "xml",
                "html",
                "csv",
                "md",
                "kt",
                "java",
                "py"
            ) -> android.R.drawable.ic_menu_edit

            else -> android.R.drawable.ic_menu_more
        }

        holder.fileIcon.setImageResource(iconRes)

        holder.checkBox.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = isSelected

        holder.itemView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 500) {
                lastClickTime = now
                onFileClick(file)
            }
        }
    }

    override fun getItemCount() = files.size

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}