package com.accident.detection.center.domain.repository

import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

interface EmergencyContactRepository {
    fun getAllContactsFlow(): Flow<List<EmergencyContactEntity>>
    suspend fun getAllContacts(): List<EmergencyContactEntity>
    suspend fun getContactById(id: Long): EmergencyContactEntity?
    fun getContactCountFlow(): Flow<Int>
    suspend fun getContactCount(): Int
    suspend fun insertContact(contact: EmergencyContactEntity): Long
    suspend fun updateContact(contact: EmergencyContactEntity)
    suspend fun deleteContact(contact: EmergencyContactEntity)
    suspend fun deleteContactById(id: Long)
    suspend fun reorderPriorities(contacts: List<EmergencyContactEntity>)
}

