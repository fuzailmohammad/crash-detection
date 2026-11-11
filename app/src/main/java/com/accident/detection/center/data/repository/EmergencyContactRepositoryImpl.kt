package com.accident.detection.center.data.repository

import com.accident.detection.center.data.local.dao.EmergencyContactDao
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyContactRepositoryImpl @Inject constructor(
    private val emergencyContactDao: EmergencyContactDao
) : EmergencyContactRepository {

    override fun getAllContactsFlow(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getAllContactsFlow()

    override suspend fun getAllContacts(): List<EmergencyContactEntity> =
        emergencyContactDao.getAllContacts()

    override suspend fun getContactById(id: Long): EmergencyContactEntity? =
        emergencyContactDao.getContactById(id)

    override fun getContactCountFlow(): Flow<Int> =
        emergencyContactDao.getContactCountFlow()

    override suspend fun getContactCount(): Int =
        emergencyContactDao.getContactCount()

    override suspend fun insertContact(contact: EmergencyContactEntity): Long =
        emergencyContactDao.insertContact(contact)

    override suspend fun updateContact(contact: EmergencyContactEntity) =
        emergencyContactDao.updateContact(contact)

    override suspend fun deleteContact(contact: EmergencyContactEntity) =
        emergencyContactDao.deleteContact(contact)

    override suspend fun deleteContactById(id: Long) =
        emergencyContactDao.deleteContactById(id)

    override suspend fun reorderPriorities(contacts: List<EmergencyContactEntity>) {
        contacts.forEachIndexed { index, contact ->
            emergencyContactDao.updateContact(contact.copy(priority = index + 1))
        }
    }
}

