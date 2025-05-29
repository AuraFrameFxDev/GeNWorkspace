package com.genesis.ai.app.data.model

data class MessageRequest(
    val message: String,
    val userId: String,
    val timestamp: Long = System.currentTimeMillis(),
)