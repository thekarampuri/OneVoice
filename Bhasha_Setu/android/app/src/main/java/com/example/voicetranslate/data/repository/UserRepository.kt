package com.example.voicetranslate.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.voicetranslate.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Repository for managing user identity
 * 
 * Responsibilities:
 * - Generate unique userId on first app launch
 * - Store userId persistently using DataStore
 * - Provide userId to other components
 * 
 * Lifecycle:
 * 1. App launches for first time
 * 2. getUser() is called
 * 3. No userId found in DataStore
 * 4. Generate new UUID and save to DataStore
 * 5. Return User object
 * 6. Subsequent launches return same userId
 */
class UserRepository(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val CREATED_AT_KEY = longPreferencesKey("created_at")
    }
    
    /**
     * Get current user (creates new user if doesn't exist)
     * 
     * Flow:
     * - Check DataStore for existing userId
     * - If found: return User with stored userId
     * - If not found: generate new User, save to DataStore, return
     */
    suspend fun getUser(): User {
        val preferences = context.dataStore.data.first()
        val userId = preferences[USER_ID_KEY]
        val createdAt = preferences[CREATED_AT_KEY]
        
        return if (userId != null && createdAt != null) {
            // User exists - return stored user
            User(userId = userId, createdAt = createdAt)
        } else {
            // New user - generate and save
            val newUser = User.create()
            saveUser(newUser)
            newUser
        }
    }
    
    /**
     * Get user as Flow (reactive)
     * 
     * Emits current user and updates if user changes (unlikely but possible)
     */
    fun getUserFlow(): Flow<User?> = context.dataStore.data.map { preferences ->
        val userId = preferences[USER_ID_KEY]
        val createdAt = preferences[CREATED_AT_KEY]
        
        if (userId != null && createdAt != null) {
            User(userId = userId, createdAt = createdAt)
        } else {
            null
        }
    }
    
    /**
     * Save user to DataStore
     * 
     * Internal use only - called when generating new user
     */
    private suspend fun saveUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = user.userId
            preferences[CREATED_AT_KEY] = user.createdAt
        }
    }
    
    /**
     * Clear user data (for testing/debugging only)
     * 
     * WARNING: This will generate a new userId on next getUser() call
     */
    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(CREATED_AT_KEY)
        }
    }
    
    /**
     * Check if user exists
     */
    suspend fun hasUser(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[USER_ID_KEY] != null
    }
}
