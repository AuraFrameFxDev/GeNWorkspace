package com.genesis.ai.app.data.model

import com.google.gson.annotations.SerializedName

data class SyncRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("last_sync_time") val lastSyncTime: Long,
    @SerializedName("tasks") val tasks: List<Task>,
)

data class SyncResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("synced_tasks") val syncedTasks: List<Task>,
    @SerializedName("server_time") val serverTime: Long = System.currentTimeMillis(),
)

data class Task(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("is_completed") val isCompleted: Boolean = false,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("is_deleted") val isDeleted: Boolean = false,
)
