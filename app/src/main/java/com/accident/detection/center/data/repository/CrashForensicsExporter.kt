package com.accident.detection.center.data.repository

import android.content.Context
import android.content.Intent
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.domain.repository.CrashLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forensic crash blackbox exporter for insurance claim adjusters,
 * highway patrol accident investigators, and medical first responders.
 */
@Singleton
class CrashForensicsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashLogRepository: CrashLogRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
    private val json = Json { prettyPrint = true }

    /**
     * Exports all incident logs formatted as standard RFC 4180 CSV.
     */
    suspend fun exportLogsToCsv(): String {
        val logs = crashLogRepository.getAllLogsFlow().first()
        val sb = StringBuilder()
        // Header
        sb.append("IncidentId,TimestampEpochMs,FormattedDateTime,PeakGForce,ImpactSpeedKmh,GyroAngularRateDegS,AcousticDb,PressureDeltaHpa,ConfidenceScore,VehicleMode,Latitude,Longitude,GoogleMapsLink,WasCancelledByUser,FalseAlarmReason,SosDispatched,ContactsAlertedCount,DispatchSummary\n")

        for (log in logs) {
            val dateStr = dateFormat.format(Date(log.timestampEpochMs))
            val mapsLink = "https://maps.google.com/?q=${log.latitude},${log.longitude}"
            val reason = escapeCsv(log.falseAlarmReason ?: "")
            val summary = escapeCsv(log.dispatchSummary)

            sb.append("${log.id},")
            sb.append("${log.timestampEpochMs},")
            sb.append("\"$dateStr\",")
            sb.append("${String.format(Locale.US, "%.2f", log.peakGForce)},")
            sb.append("${String.format(Locale.US, "%.1f", log.speedAtImpactKmh)},")
            sb.append("${String.format(Locale.US, "%.1f", log.gyroRotationalRateRadS)},")
            sb.append("${String.format(Locale.US, "%.1f", log.ambientNoiseDb)},")
            sb.append("${String.format(Locale.US, "%.2f", log.barometricPressureSpikeHpa)},")
            sb.append("${String.format(Locale.US, "%.1f", log.crashConfidenceScore)},")
            sb.append("${log.vehicleMode.name},")
            sb.append("${log.latitude},")
            sb.append("${log.longitude},")
            sb.append("\"$mapsLink\",")
            sb.append("${log.wasCancelledByUser},")
            sb.append("\"$reason\",")
            sb.append("${log.sosDispatchTriggered},")
            sb.append("${log.contactsNotifiedCount},")
            sb.append("\"$summary\"\n")
        }

        return sb.toString()
    }

    /**
     * Launches the system share sheet with the generated forensic CSV report.
     */
    fun shareForensicReport(csvContent: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TITLE, "Crash Telemetry Blackbox Audit Report")
            putExtra(Intent.EXTRA_SUBJECT, "Forensic Accident Log Export - ${dateFormat.format(Date())}")
            putExtra(Intent.EXTRA_TEXT, csvContent)
            type = "text/csv"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Forensic Blackbox Logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
    }
}

