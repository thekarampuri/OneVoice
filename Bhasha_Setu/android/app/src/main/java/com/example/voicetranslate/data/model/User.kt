package com.example.voicetranslate.data.model

import java.util.UUID

/**
 * User identity model
 * 
 * Represents a unique user with a locally generated UUID.
 * No authentication required - identity is device-based.
 * 
 * @property userId Unique identifier (UUID v4)
 * @property createdAt Timestamp when user was first created
 */
data class User(
    val userId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create a new user with generated UUID
         */
        fun create(): User = User()
    }
}
