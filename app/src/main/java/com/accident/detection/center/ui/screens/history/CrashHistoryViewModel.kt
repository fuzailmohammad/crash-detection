package com.accident.detection.center.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accident.detection.center.data.local.entity.CrashEventLogEntity
import com.accident.detection.center.domain.repository.CrashLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrashHistoryViewModel @Inject constructor(
    private val crashLogRepository: CrashLogRepository
) : ViewModel() {

    val crashLogs: StateFlow<List<CrashEventLogEntity>> = crashLogRepository.getAllLogsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            crashLogRepository.deleteLogById(logId)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            crashLogRepository.clearAllLogs()
        }
    }
}

