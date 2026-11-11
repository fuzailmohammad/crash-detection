package com.accident.detection.center.dispatch.email

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.dispatch.model.EmailDispatchResult
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.sensor.CrashEventPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashEmailDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Enqueues resilient background email dispatch through WorkManager.
     * Guarantees eventual delivery even if network drops during collision.
     */
    suspend fun dispatchCrashReportEmail(
        contact: EmergencyContactEntity,
        payload: CrashEventPayload,
        userProfile: UserProfile
    ): EmailDispatchResult = withContext(Dispatchers.IO) {
        if (!contact.isEmailEnabled || contact.email.isBlank()) {
            return@withContext EmailDispatchResult(
                contactId = contact.id,
                recipientEmail = contact.email,
                status = ChannelDispatchStatus.SKIPPED,
                errorMessage = "Email channel disabled or empty"
            )
        }

        try {
            val subject = CrashReportEmailBuilder.buildSubject(userProfile)
            val html = CrashReportEmailBuilder.buildHtmlReport(payload, userProfile)

            val inputData = workDataOf(
                CrashEmailDispatchWorker.KEY_RECIPIENT_EMAIL to contact.email,
                CrashEmailDispatchWorker.KEY_SUBJECT to subject,
                CrashEmailDispatchWorker.KEY_HTML_CONTENT to html
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val emailWorkRequest = OneTimeWorkRequestBuilder<CrashEmailDispatchWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueue(emailWorkRequest)

            EmailDispatchResult(
                contactId = contact.id,
                recipientEmail = contact.email,
                status = ChannelDispatchStatus.SUCCESS
            )
        } catch (e: Exception) {
            EmailDispatchResult(
                contactId = contact.id,
                recipientEmail = contact.email,
                status = ChannelDispatchStatus.FAILED,
                errorMessage = e.localizedMessage ?: "Failed to enqueue email dispatch"
            )
        }
    }

    suspend fun dispatchEmailBatch(
        contacts: List<EmergencyContactEntity>,
        payload: CrashEventPayload,
        userProfile: UserProfile
    ): List<EmailDispatchResult> = withContext(Dispatchers.IO) {
        contacts.filter { it.isEmailEnabled && it.email.isNotBlank() }.map { contact ->
            dispatchCrashReportEmail(contact, payload, userProfile)
        }
    }
}

