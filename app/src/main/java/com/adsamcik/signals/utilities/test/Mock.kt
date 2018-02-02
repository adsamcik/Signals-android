package com.adsamcik.signalcollector.test

import android.os.Build


val useMock: Boolean = isEmulator || isTestMode

val isTestMode: Boolean
    get() {
        return try {
            Class.forName("com.adsamcik.signalcollector.activities.AppTest")
            true
        } catch (e: Exception) {
            false
        }
    }

/**
 * Checks if the device looks like an emulator. This is used primarily to detect automated testing.
 *
 * @return true if emulator is detected
 */
val isEmulator: Boolean
    get() = (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || "google_sdk" == Build.PRODUCT)
