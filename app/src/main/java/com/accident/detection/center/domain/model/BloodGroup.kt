package com.accident.detection.center.domain.model

enum class BloodGroup(val label: String) {
    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-"),
    UNKNOWN("Unknown / Not Disclosed");

    companion object {
        fun fromLabel(label: String): BloodGroup {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) || it.name.equals(label, ignoreCase = true) }
                ?: UNKNOWN
        }
    }
}

