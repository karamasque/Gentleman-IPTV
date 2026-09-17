package com.kaynanamtv.domain.model

/**
 * User preference for UI performance & rendering mode (Rocket Mode).
 */
enum class PerformanceModePreference {
    /**
     * Automatically detect device hardware (RAM, CPU, Android low-ram flag)
     * and enable Rocket Mode optimizations for low/mid-tier TV Boxes and devices.
     */
    AUTO,

    /**
     * Always enable Rocket Mode (maximum UI fluidity, 16-bit images, lightweight rendering).
     */
    ALWAYS_ON,

    /**
     * Keep all visual effects, high-fidelity blurring and live ambilight enabled.
     */
    OFF;

    val storageKey: String
        get() = name

    companion object {
        fun fromStorageKey(value: String?): PerformanceModePreference {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}
