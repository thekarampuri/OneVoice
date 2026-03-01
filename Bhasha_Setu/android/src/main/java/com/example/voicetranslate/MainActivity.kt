package com.example.voicetranslate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.voicetranslate.data.repository.UserRepository
import com.example.voicetranslate.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Home screen for call setup
 * 
 * Features:
 * - Display user ID (copyable)
 * - Input field for call ID
 * - Start call button
 */
class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "=== MainActivity onCreate ===")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userRepository = UserRepository(this)
        
        setupUI()
        loadUserId()
        Log.d(tag, "MainActivity initialized")
    }
    
    private fun setupUI() {
        // Copy user ID button
        binding.btnCopyUserId.setOnClickListener {
            copyUserIdToClipboard()
        }
        
        // Start call button
        binding.btnStartCall.setOnClickListener {
            Log.d(tag, "🔘 Start Call button pressed")
            val serverUrl = binding.etServerUrl.text.toString().trim()
            val callId = binding.etCallId.text.toString().trim()
            
            Log.d(tag, "Server URL: $serverUrl")
            Log.d(tag, "Call ID: $callId")
            
            if (serverUrl.isEmpty()) {
                Log.w(tag, "❌ Server URL is empty")
                Toast.makeText(this, "Please enter Server URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (callId.isEmpty()) {
                Log.w(tag, "❌ Call ID is empty")
                Toast.makeText(this, "Please enter a Call ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            Log.d(tag, "✅ Starting call with Server: $serverUrl, CallID: $callId")
            startCall(serverUrl, callId)
        }
    }
    
    private fun loadUserId() {
        lifecycleScope.launch {
            val user = userRepository.getUser()
            userId = user.userId
            
            Log.d(tag, "User ID loaded: ${userId.take(8)}...")
            
            // Display shortened user ID (first 8 chars)
            val shortId = userId.take(8)
            binding.tvUserId.text = "$shortId..."
        }
    }
    
    private fun copyUserIdToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("User ID", userId)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun startCall(serverUrl: String, callId: String) {
        Log.d(tag, "📞 Launching CallActivity...")
        Log.d(tag, "   Server: $serverUrl")
        Log.d(tag, "   Call ID: $callId")
        Log.d(tag, "   User ID: ${userId.take(8)}...")
        
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("SERVER_URL", serverUrl)
            putExtra("USER_ID", userId)
        }
        startActivity(intent)
        Log.d(tag, "✅ CallActivity launched")
    }
}
