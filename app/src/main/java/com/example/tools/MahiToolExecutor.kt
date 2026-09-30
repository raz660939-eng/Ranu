package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class ExecutedToolEvent(
    val toolName: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MahiToolExecutor(
    private val context: Context,
    private val onToolExecuted: (ExecutedToolEvent) -> Unit
) {
    companion object {
        private const val TAG = "MahiToolExecutor"
    }

    fun execute(toolName: String, args: Map<String, Any?>): Map<String, Any?> {
        Log.d(TAG, "Executing tool $toolName with args $args")
        return when (toolName) {
            "openWebsite" -> {
                val rawUrl = args["url"]?.toString() ?: "https://google.com"
                val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    "https://$rawUrl"
                } else {
                    rawUrl
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    val event = ExecutedToolEvent(
                        toolName = "openWebsite",
                        summary = "Opened: $formattedUrl"
                    )
                    onToolExecuted(event)
                    mapOf(
                        "status" to "success",
                        "url" to formattedUrl,
                        "result" to "Successfully launched $formattedUrl in user's browser"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open URL $formattedUrl", e)
                    mapOf(
                        "status" to "error",
                        "message" to "Could not launch browser: ${e.message}"
                    )
                }
            }

            "changeMood" -> {
                val mood = args["mood"]?.toString() ?: "sassy"
                val event = ExecutedToolEvent(
                    toolName = "changeMood",
                    summary = "Mahi vibe set to $mood"
                )
                onToolExecuted(event)
                mapOf("status" to "success", "currentMood" to mood)
            }

            else -> {
                mapOf("status" to "unknown_tool", "message" to "Tool $toolName not recognized")
            }
        }
    }
}
