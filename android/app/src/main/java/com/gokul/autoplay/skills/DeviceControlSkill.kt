package com.gokul.autoplay.skills

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/** Handles device-level commands that were previously owned by the floating service. */
class DeviceControlSkill : TommySkill {
    override val id: String = "device.control"
    override val name: String = "Device Control"
    override val description: String = "Controls supported device features such as the flashlight and recent apps."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        return (normalized.contains("light") && (normalized.contains("on") || normalized.contains("off") || normalized.contains("turn"))) ||
            (normalized.contains("close") && normalized.containsAny("instagram", "youtube", "whatsapp", "google"))
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)
        return when {
            normalized.contains("light") && normalized.contains("off") -> setFlashlight(context, false)
            normalized.contains("light") -> setFlashlight(context, true)
            normalized.contains("close") -> openRecentApps(context, normalized)
            else -> TommySkillResult.failure(id, "I don't have a device action for that yet.")
        }
    }

    private fun setFlashlight(context: Context, enabled: Boolean): TommySkillResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return TommySkillResult.failure(id, "Camera permission is needed to control the flashlight.", if (enabled) "LIGHT_ON" else "LIGHT_OFF")
        }
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return TommySkillResult.failure(id, "This phone has no available flashlight.")
            cameraManager.setTorchMode(cameraId, enabled)
            TommySkillResult.success(id, if (enabled) "Flashlight ON." else "Flashlight OFF.", if (enabled) "LIGHT_ON" else "LIGHT_OFF")
        } catch (_: Exception) {
            TommySkillResult.failure(id, "Unable to control the flashlight.", if (enabled) "LIGHT_ON" else "LIGHT_OFF")
        }
    }

    private fun openRecentApps(context: Context, command: String): TommySkillResult {
        val appName = when {
            command.contains("instagram") -> "Instagram"
            command.contains("youtube") -> "YouTube"
            command.contains("whatsapp") -> "WhatsApp"
            else -> "Google"
        }
        return try {
            context.startActivity(Intent("android.intent.action.RECENT_APPS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            TommySkillResult.success(id, "Recent Apps opened. Swipe $appName away to close it.", "CLOSE_$appName")
        } catch (_: Exception) {
            TommySkillResult.failure(id, "Android did not allow Recent Apps to open.")
        }
    }

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean = values.any { contains(it) }
}
