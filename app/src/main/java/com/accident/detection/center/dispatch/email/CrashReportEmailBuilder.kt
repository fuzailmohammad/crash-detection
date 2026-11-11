package com.accident.detection.center.dispatch.email

import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.sensor.CrashEventPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReportEmailBuilder {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.getDefault())

    fun buildSubject(userProfile: UserProfile): String {
        val name = userProfile.fullName.ifBlank { "User" }
        return "🚨 CRITICAL SOS: Vehicular Collision Involving $name (Live Coordinates & Medical ID)"
    }

    fun buildHtmlReport(
        payload: CrashEventPayload,
        userProfile: UserProfile,
        streetAddress: String = "",
        batteryPercentage: Int = -1
    ): String {
        val formattedDate = dateFormat.format(Date(payload.timestampEpochMs))
        val mapsUrl = "https://maps.google.com/?q=${payload.latitude},${payload.longitude}"

        // Generate telemetry table rows for pre-impact samples
        val telemetryRows = StringBuilder()
        val preImpact = payload.preImpactTelemetry
        val step = (preImpact.size / 10).coerceAtLeast(1)
        for (i in preImpact.indices step step) {
            val sample = preImpact[i]
            val timeOffsetSec = (sample.timestampEpochMs - payload.timestampEpochMs) / 1000.0f
            telemetryRows.append(
                """
                <tr>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #333;">${String.format("%.1f", timeOffsetSec)}s</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #333; color: #00E676;">${sample.speedKmh.toInt()} km/h</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #333; color: #FF5252;">${String.format("%.2f", sample.gForce)}G</td>
                    <td style="padding: 6px 10px; border-bottom: 1px solid #333;">${sample.gyroAngularVelocityDegS.toInt()}°/s</td>
                </tr>
                """.trimIndent()
            )
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Emergency Crash Telemetry Report</title>
        </head>
        <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0A0A0A; color: #FFFFFF; margin: 0; padding: 20px;">
            <div style="max-width: 650px; margin: 0 auto; background-color: #141414; border-radius: 12px; border: 1px solid #FF1744; overflow: hidden;">
                <!-- Red Emergency Header Banner -->
                <div style="background-color: #D50000; padding: 24px 20px; text-align: center;">
                    <h1 style="margin: 0; font-size: 24px; font-weight: 900; letter-spacing: 1px; color: #FFFFFF;">
                        🚨 AUTOMATED EMERGENCY CRASH DISPATCH
                    </h1>
                    <p style="margin: 8px 0 0 0; font-size: 14px; color: #FFCDD2;">
                        Multi-sensor impact confirmation system triggered emergency cascade
                    </p>
                </div>

                <div style="padding: 24px;">
                    <!-- Incident & Patient Summary Card -->
                    <div style="background-color: #1E1E1E; border-radius: 8px; padding: 16px; margin-bottom: 20px; border-left: 4px solid #00E676;">
                        <h2 style="margin: 0 0 10px 0; font-size: 18px; color: #FFFFFF;">Incident Information</h2>
                        <p style="margin: 4px 0; font-size: 14px; color: #B0BEC5;"><strong>Person Involved:</strong> <span style="color: #FFFFFF;">${userProfile.fullName.ifBlank { "Unidentified User" }}</span></p>
                        <p style="margin: 4px 0; font-size: 14px; color: #B0BEC5;"><strong>Timestamp:</strong> $formattedDate</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #B0BEC5;"><strong>Vehicle Mode:</strong> ${payload.vehicleMode.displayName}</p>
                        <p style="margin: 4px 0; font-size: 14px; color: #B0BEC5;"><strong>Crash Confidence:</strong> <span style="color: #FF1744; font-weight: bold;">${payload.crashConfidenceScore.toInt()}% (HIGH CONFIDENCE)</span></p>
                        ${if (streetAddress.isNotBlank()) "<p style=\"margin: 4px 0; font-size: 14px; color: #B0BEC5;\"><strong>Street Address:</strong> <span style=\"color: #FFFFFF;\">$streetAddress</span></p>" else ""}
                        ${if (batteryPercentage >= 0) "<p style=\"margin: 4px 0; font-size: 14px; color: #B0BEC5;\"><strong>Phone Battery:</strong> <span style=\"color: #00E676;\">$batteryPercentage%</span></p>" else ""}
                    </div>

                    <!-- Direct Action Button Linking to Google Maps -->
                    <div style="text-align: center; margin: 24px 0;">
                        <a href="$mapsUrl" style="background-color: #FF1744; color: #FFFFFF; font-weight: bold; text-decoration: none; padding: 14px 28px; border-radius: 8px; display: inline-block; font-size: 16px;">
                            📍 VIEW ACCIDENT LOCATION ON GOOGLE MAPS
                        </a>
                        <p style="margin: 8px 0 0 0; font-size: 12px; color: #9E9E9E;">
                            GPS Coordinates: ${payload.latitude}, ${payload.longitude}
                        </p>
                    </div>

                    <!-- Paramedic Medical ID Section -->
                    <div style="background-color: #FFFFFF; color: #000000; border-radius: 8px; padding: 18px; margin-bottom: 20px;">
                        <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #D50000; padding-bottom: 8px; margin-bottom: 12px;">
                            <span style="font-weight: 900; font-size: 16px; color: #D50000;">PARAMEDIC MEDICAL ID</span>
                            <span style="background-color: #D50000; color: #FFFFFF; font-weight: 900; font-size: 18px; padding: 4px 12px; border-radius: 6px;">
                                ${userProfile.bloodGroup.label}
                            </span>
                        </div>
                        <p style="margin: 4px 0; font-size: 14px;"><strong>Allergies:</strong> ${userProfile.allergies.ifBlank { "None Reported" }}</p>
                        <p style="margin: 4px 0; font-size: 14px;"><strong>Medical Conditions:</strong> ${userProfile.medicalConditions.ifBlank { "None Reported" }}</p>
                        <p style="margin: 4px 0; font-size: 14px;"><strong>Current Medications:</strong> ${userProfile.medications.ifBlank { "None Reported" }}</p>
                        <p style="margin: 4px 0; font-size: 14px;"><strong>Registered Organ Donor:</strong> ${if (userProfile.organDonor) "YES" else "NO"}</p>
                    </div>

                    <!-- Forensic Telemetry Metrics Table -->
                    <div style="background-color: #1E1E1E; border-radius: 8px; padding: 16px; margin-bottom: 20px;">
                        <h3 style="margin: 0 0 12px 0; font-size: 16px; color: #00E5FF;">Forensic Impact Telemetry</h3>
                        <table style="width: 100%; border-collapse: collapse; font-size: 14px;">
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Peak Resultant G-Force:</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #FF1744; text-align: right;">${String.format("%.2f", payload.peakGForce)} G</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Velocity at Impact:</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #FFFFFF; text-align: right;">${payload.speedAtImpactKmh.toInt()} km/h</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Gyroscope Angular Rate:</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #FFFFFF; text-align: right;">${payload.gyroAngularVelocityDegS.toInt()} deg/s</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Acoustic Level (Airbag):</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #FFFFFF; text-align: right;">${payload.ambientNoiseDb.toInt()} dB</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Cabin Pressure Delta:</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #FFFFFF; text-align: right;">${String.format("%.1f", payload.pressureJumpHpa)} hPa</td>
                            </tr>
                            ${if (batteryPercentage >= 0) """
                            <tr>
                                <td style="padding: 6px 0; color: #B0BEC5;">Device Battery Level:</td>
                                <td style="padding: 6px 0; font-weight: bold; color: #00E676; text-align: right;">$batteryPercentage%</td>
                            </tr>
                            """.trimIndent() else ""}
                        </table>
                    </div>

                    <!-- Pre-Impact Deceleration Waveform Table -->
                    ${if (telemetryRows.isNotEmpty()) """
                    <div style="background-color: #1E1E1E; border-radius: 8px; padding: 16px; margin-bottom: 20px;">
                        <h3 style="margin: 0 0 10px 0; font-size: 14px; color: #B0BEC5;">Pre-Impact Velocity & G-Force Sequence</h3>
                        <table style="width: 100%; border-collapse: collapse; font-size: 12px; text-align: left;">
                            <thead>
                                <tr style="color: #9E9E9E; border-bottom: 1px solid #444;">
                                    <th style="padding: 6px 10px;">Time</th>
                                    <th style="padding: 6px 10px;">Speed</th>
                                    <th style="padding: 6px 10px;">G-Force</th>
                                    <th style="padding: 6px 10px;">Gyro</th>
                                </tr>
                            </thead>
                            <tbody>
                                $telemetryRows
                            </tbody>
                        </table>
                    </div>
                    """ else ""}

                    <p style="font-size: 12px; color: #757575; text-align: center; margin-top: 24px;">
                        Generated automatically by Accident Detection Center Native Android Application.<br>
                        Immediate response or medical check is advised.
                    </p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
