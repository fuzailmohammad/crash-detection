package com.accident.detection.center.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.local.entity.EmergencyContactEntity
import com.accident.detection.center.domain.repository.EmergencyContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    private val contactRepository: EmergencyContactRepository
) : ViewModel() {

    val contacts: StateFlow<List<EmergencyContactEntity>> = contactRepository.getAllContactsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val contactCount: StateFlow<Int> = contactRepository.getContactCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addContact(
        name: String,
        phone: String,
        email: String,
        relationship: String,
        isCallEnabled: Boolean,
        isSmsEnabled: Boolean,
        isEmailEnabled: Boolean
    ) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()
        val trimmedEmail = email.trim()

        if (trimmedName.isBlank()) {
            _errorMessage.value = "Contact name cannot be empty."
            return
        }
        if (trimmedPhone.length < 7) {
            _errorMessage.value = "Please enter a valid phone number with country/area code."
            return
        }
        if (trimmedEmail.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }
        if (contacts.value.size >= 5) {
            _errorMessage.value = "Maximum of 5 emergency contacts allowed."
            return
        }

        viewModelScope.launch {
            val newPriority = contacts.value.size + 1
            val entity = EmergencyContactEntity(
                name = trimmedName,
                phoneNumber = trimmedPhone,
                email = trimmedEmail,
                relationship = relationship.ifBlank { "Emergency Contact" },
                priority = newPriority,
                isCallEnabled = isCallEnabled,
                isSmsEnabled = isSmsEnabled,
                isEmailEnabled = isEmailEnabled
            )
            contactRepository.insertContact(entity)
            _errorMessage.value = null
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            // Re-normalize priorities
            val remaining = contactRepository.getAllContacts()
            contactRepository.reorderPriorities(remaining)
        }
    }

    fun moveContactUp(index: Int) {
        if (index <= 0) return
        val currentList = contacts.value.toMutableList()
        val item = currentList.removeAt(index)
        currentList.add(index - 1, item)
        viewModelScope.launch {
            contactRepository.reorderPriorities(currentList)
        }
    }

    fun moveContactDown(index: Int) {
        val currentList = contacts.value.toMutableList()
        if (index >= currentList.size - 1) return
        val item = currentList.removeAt(index)
        currentList.add(index + 1, item)
        viewModelScope.launch {
            contactRepository.reorderPriorities(currentList)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

