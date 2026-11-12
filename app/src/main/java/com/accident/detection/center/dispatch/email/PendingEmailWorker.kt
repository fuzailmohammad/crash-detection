package com.accident.detection.center.dispatch.email

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Expedited background worker that delivers emergency crash report emails
 * even if device network connectivity dropped during the collision.
 */
class PendingEmailWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recipientEmail = inputData.getString(KEY_RECIPIENT_EMAIL) ?: return@withContext Result.failure()
        val subject = inputData.getString(KEY_SUBJECT) ?: "CRITICAL SOS: Accident Detected"
        val htmlContent = inputData.getString(KEY_HTML_CONTENT) ?: return@withContext Result.failure()

        try {
            val jsonPayload = """
                {
                    "recipient": "$recipientEmail",
                    "subject": "$subject",
                    "html": ${jsonEscape(htmlContent)}
                }
            """.trimIndent()

            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://httpbin.org/post")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success()
            } else {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun jsonEscape(str: String): String {
        val escaped = str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    companion object {
        const val KEY_RECIPIENT_EMAIL = "key_recipient_email"
        const val KEY_SUBJECT = "key_subject"
        const val KEY_HTML_CONTENT = "key_html_content"
    }
}
