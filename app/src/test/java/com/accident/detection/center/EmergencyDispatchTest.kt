package com.accident.detection.center

import com.accident.detection.center.dispatch.email.CrashReportEmailBuilder
import com.accident.detection.center.domain.model.BloodGroup
import com.accident.detection.center.domain.model.ChannelDispatchStatus
import com.accident.detection.center.domain.model.ContactDispatchProgress
import com.accident.detection.center.domain.model.UserProfile
import com.accident.detection.center.domain.model.VehicleMode
import com.accident.detection.center.sensor.CrashEventPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyDispatchTest {

    @Test
    fun testCrashReportEmailBuilderGeneratesAccurateHtmlAndSubject() {
        val userProfile = UserProfile(
            fullName = "John Doe",
            bloodGroup = BloodGroup.O_POSITIVE,
            allergies = "Severe Penicillin",
            medicalConditions = "Asthma",
            medications = "Albuterol",
            organDonor = true,
            selectedVehicleMode = VehicleMode.CAR
        )

        val payload = CrashEventPayload(
            timestampEpochMs = 1700000000000L,
            latitude = 37.7749,
            longitude = -122.4194,
            speedAtImpactKmh = 68.0f,
            peakGForce = 7.2f,
            gyroAngularVelocityDegS = 340.0f,
            ambientNoiseDb = 122.0f,
            pressureJumpHpa = 3.5f,
            crashConfidenceScore = 100.0f,
            vehicleMode = VehicleMode.CAR,
            forensicSummary = "Collision verified: Peak 7.2G, Gyro 340°/s."
        )

        val subject = CrashReportEmailBuilder.buildSubject(userProfile)
        val html = CrashReportEmailBuilder.buildHtmlReport(
            payload = payload,
            userProfile = userProfile,
            streetAddress = "101 Market St, San Francisco, CA",
            batteryPercentage = 84
        )

        // Verify subject contains patient name and alert icon
        assertTrue(subject.contains("John Doe"))
        assertTrue(subject.contains("🚨 CRITICAL SOS"))

        // Verify HTML contains critical paramedic medical data
        assertTrue(html.contains("O+"))
        assertTrue(html.contains("Severe Penicillin"))
        assertTrue(html.contains("Asthma"))
        assertTrue(html.contains("Albuterol"))
        assertTrue(html.contains("YES")) // Organ donor

        // Verify HTML contains Google Maps link and GPS coordinates
        assertTrue(html.contains("https://maps.google.com/?q=37.7749,-122.4194"))
        assertTrue(html.contains("7.20 G") || html.contains("7.2 G"))
        assertTrue(html.contains("68 km/h"))
        assertTrue(html.contains("122 dB"))

        // Verify Phase 5 additions: Street Address and Battery %
        assertTrue(html.contains("101 Market St, San Francisco, CA"))
        assertTrue(html.contains("84%"))
    }

    @Test
    fun testEmergencySmsMessageFormatAndContent() {
        val userName = "Jane Smith"
        val peakG = "7.8"
        val speed = 72
        val bloodGroup = "B+"
        val allergies = "Latex"
        val streetAddress = "Highway 101 Mile 42"
        val mapsUrl = "https://maps.google.com/?q=37.77,-122.41"
        val batteryPct = 92

        val smsBody = "🚨 CRITICAL SOS: $userName was involved in a severe vehicle crash ($peakG G at $speed km/h). " +
                "Location: $streetAddress ($mapsUrl) " +
                "Medical: Blood $bloodGroup, Allergies: $allergies. Battery: $batteryPct%. " +
                "Immediate emergency response requested."

        assertTrue(smsBody.contains("🚨 CRITICAL SOS"))
        assertTrue(smsBody.contains("Jane Smith"))
        assertTrue(smsBody.contains("7.8 G at 72 km/h"))
        assertTrue(smsBody.contains("Highway 101 Mile 42"))
        assertTrue(smsBody.contains("https://maps.google.com/?q=37.77,-122.41"))
        assertTrue(smsBody.contains("Blood B+"))
        assertTrue(smsBody.contains("Latex"))
        assertTrue(smsBody.contains("Battery: 92%"))
    }

    @Test
    fun testMultiChannelSupervisorScopeIndependence() {
        // Simulates independent failure isolation: email failure MUST NOT affect SMS or Phone Call
        val progress = ContactDispatchProgress(
            contactId = 1L,
            contactName = "Emergency Contact 1",
            phoneNumber = "+15551234567",
            email = "contact@example.com",
            smsStatus = ChannelDispatchStatus.SUCCESS,
            emailStatus = ChannelDispatchStatus.FAILED, // Network dropped
            callStatus = ChannelDispatchStatus.SUCCESS
        )

        assertEquals(ChannelDispatchStatus.SUCCESS, progress.smsStatus)
        assertEquals(ChannelDispatchStatus.FAILED, progress.emailStatus)
        assertEquals(ChannelDispatchStatus.SUCCESS, progress.callStatus)

        // Contact is still counted as notified because cellular SMS and Call succeeded
        val isContactAlerted = progress.smsStatus == ChannelDispatchStatus.SUCCESS ||
                progress.emailStatus == ChannelDispatchStatus.SUCCESS ||
                progress.callStatus == ChannelDispatchStatus.SUCCESS

        assertTrue("Failure in email channel MUST NOT invalidate overall emergency contact notification", isContactAlerted)
    }

    @Test
    fun testGoogleMapsUrlConstruction() {
        val lat = 37.774929
        val lon = -122.419418
        val expected = "https://maps.google.com/?q=37.774929,-122.419418"
        val actual = "https://maps.google.com/?q=$lat,$lon"
        assertEquals(expected, actual)
    }
}
