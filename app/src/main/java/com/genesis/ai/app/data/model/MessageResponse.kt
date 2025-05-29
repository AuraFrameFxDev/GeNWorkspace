package com.genesis.ai.app.data.model

import java.util.UUID

data class MessageResponse(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "success",
)
