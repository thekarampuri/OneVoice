package com.example.voicetranslate.data.model

/**
 * Language data class
 */
data class Language(
    val name: String,
    val code: String
) {
    override fun toString(): String = name
    
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            Language("English", "en"),
            Language("Marathi", "mr"),
            Language("Hindi", "hi"),
            Language("Bengali", "bn"),
            Language("Gujarati", "gu"),
            Language("Kannada", "kn"),
            Language("Malayalam", "ml"),
            Language("Punjabi", "pa"),
            Language("Tamil", "ta"),
            Language("Telugu", "te")
        )
    }
}
