package com.example.voicetranslate.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Glassmorphism Color Palette - Dark Mode First
 * 
 * Design Philosophy:
 * - Frosted glass surfaces with blur
 * - Subtle gradients
 * - High contrast text
 * - Depth through transparency
 */

// Background Colors
val BackgroundPrimary = Color(0xFF0A0E27)      // Deep navy blue
val BackgroundSecondary = Color(0xFF151B3D)    // Slightly lighter navy
val BackgroundTertiary = Color(0xFF1E2749)     // Card background base

// Glass Surface Colors (with alpha for transparency)
val GlassSurface = Color(0x40FFFFFF)           // 25% white - primary glass
val GlassSurfaceLight = Color(0x60FFFFFF)      // 38% white - elevated glass
val GlassSurfaceDark = Color(0x20FFFFFF)       // 12% white - subtle glass

// Accent Colors
val AccentPrimary = Color(0xFF6C63FF)          // Vibrant purple
val AccentSecondary = Color(0xFF4ECDC4)        // Teal
val AccentTertiary = Color(0xFFFF6B9D)         // Pink

// Gradient Colors
val GradientStart = Color(0xFF6C63FF)          // Purple
val GradientMiddle = Color(0xFF4ECDC4)         // Teal
val GradientEnd = Color(0xFF44A08D)            // Green

// Text Colors (High Contrast)
val TextPrimary = Color(0xFFFFFFFF)            // Pure white
val TextSecondary = Color(0xB3FFFFFF)          // 70% white
val TextTertiary = Color(0x80FFFFFF)           // 50% white
val TextDisabled = Color(0x4DFFFFFF)           // 30% white

// Status Colors
val StatusSuccess = Color(0xFF4ECDC4)          // Teal
val StatusError = Color(0xFFFF6B6B)            // Red
val StatusWarning = Color(0xFFFFE66D)          // Yellow
val StatusInfo = Color(0xFF6C63FF)             // Purple

// Border Colors
val BorderGlass = Color(0x40FFFFFF)            // 25% white
val BorderAccent = Color(0xFF6C63FF)           // Purple
val BorderSubtle = Color(0x20FFFFFF)           // 12% white

// Shadow Colors
val ShadowLight = Color(0x40000000)            // 25% black
val ShadowMedium = Color(0x60000000)           // 38% black
val ShadowHeavy = Color(0x80000000)            // 50% black

// Overlay Colors
val OverlayLight = Color(0x20000000)           // 12% black
val OverlayMedium = Color(0x40000000)          // 25% black
val OverlayHeavy = Color(0x80000000)           // 50% black
