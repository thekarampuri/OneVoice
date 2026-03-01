package com.example.voicetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.voicetranslate.data.model.CallState
import com.example.voicetranslate.data.repository.CallRepository
import com.example.voicetranslate.data.repository.UserRepository
import com.example.voicetranslate.databinding.ActivityCallBinding
import kotlinx.coroutines.launch

/**
 * Call screen with WebRTC integration
 * 
 * Features:
 * - WebRTC audio calling
 * - Call status display
 * - Mute/Speaker/End call controls
 */
class CallActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCallBinding
    private lateinit var callRepository: CallRepository
    
    private var callId: String = ""
    private var serverUrl: String = ""
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val DEFAULT_SERVER_URL = "192.168.31.29:8001" // Updated to match actual network
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CallActivity", "=== CallActivity onCreate ===")
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        callId = intent.getStringExtra("CALL_ID") ?: ""
        serverUrl = intent.getStringExtra("SERVER_URL") ?: DEFAULT_SERVER_URL
        
        Log.d("CallActivity", "Call ID: $callId")
        Log.d("CallActivity", "Server URL: $serverUrl")
        
        if (callId.isEmpty()) {
            Log.e("CallActivity", "❌ Invalid Call ID")
            Toast.makeText(this, "Invalid Call ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize repository
        val userRepository = UserRepository(this)
        callRepository = CallRepository(this, userRepository)
        
        setupUI()
        checkPermissionsAndStartCall()
        Log.d("CallActivity", "CallActivity initialized")
    }
    
    private fun setupUI() {
        binding.tvTargetId.text = callId
        
        // End call button
        binding.fabEndCall.setOnClickListener {
            endCall()
        }
        
        // Mute button (will be enabled when connected)
        binding.fabMute.setOnClickListener {
            callRepository.toggleMute()
        }
        
        // Speaker button
        binding.fabSpeaker.setOnClickListener {
            // Toggle speaker logic would go here
        }
        
        // Observe call state
        lifecycleScope.launch {
            callRepository.callState.collect { state ->
                updateCallStatus(state)
            }
        }
        
        // Observe mute state
        lifecycleScope.launch {
            callRepository.isMuted.collect { muted ->
                updateMuteButton(muted)
            }
        }
    }
    
    private fun checkPermissionsAndStartCall() {
        Log.d("CallActivity", "Checking audio permissions...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            Log.d("CallActivity", "Requesting RECORD_AUDIO permission")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission already granted
            Log.d("CallActivity", "✅ Audio permission already granted")
            startCall()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("CallActivity", "✅ Audio permission granted by user")
                startCall()
            } else {
                Log.e("CallActivity", "❌ Audio permission denied")
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun startCall() {
        Log.d("CallActivity", "🚀 Starting call...")
        Log.d("CallActivity", "   Server URL: $serverUrl")
        Log.d("CallActivity", "   Call ID: $callId")
        lifecycleScope.launch {
            try {
                callRepository.startCall(serverUrl, callId)
                Log.d("CallActivity", "✅ Call started successfully")
            } catch (e: Exception) {
                Log.e("CallActivity", "❌ Failed to start call: ${e.message}", e)
                Toast.makeText(this@CallActivity, "Failed to start call: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun updateCallStatus(state: CallState) {
        val statusText = when (state) {
            CallState.IDLE -> "Idle"
            CallState.CALLING -> "Calling..."
            CallState.RINGING -> "Ringing..."
            CallState.CONNECTING -> "Connecting..."
            CallState.CONNECTED -> "Connected"
            CallState.ENDED -> "Call Ended"
        }
        
        binding.tvDuration.text = statusText
        
        // Enable mute button when connected
        binding.fabMute.isEnabled = (state == CallState.CONNECTED)
        
        // End call if state is ENDED
        if (state == CallState.ENDED) {
            finish()
        }
    }
    
    private fun updateMuteButton(muted: Boolean) {
        // Update button appearance based on mute state
        binding.fabMute.alpha = if (muted) 1.0f else 0.5f
    }
    
    private fun endCall() {
        callRepository.endCall()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("CallActivity", "CallActivity destroyed - ending call")
        callRepository.endCall()
    }
}
