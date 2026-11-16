package com.example.pardadns.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pardadns.data.repository.AutomationRepository
import com.example.pardadns.data.repository.ProRepository
import com.example.pardadns.data.repository.ProtectionSchedule
import com.example.pardadns.service.ProtectionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val repository: AutomationRepository,
    proRepository: ProRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = proRepository.isPro
    val schedule: StateFlow<ProtectionSchedule> = repository.schedule
    val connectOnBoot: StateFlow<Boolean> = repository.connectOnBoot
    val connectOnLaunch: StateFlow<Boolean> = repository.connectOnLaunch

    fun setSchedule(schedule: ProtectionSchedule) {
        repository.setSchedule(schedule)
        ProtectionScheduler.reschedule(context)
    }

    fun setConnectOnBoot(enabled: Boolean) = repository.setConnectOnBoot(enabled)
    fun setConnectOnLaunch(enabled: Boolean) = repository.setConnectOnLaunch(enabled)

    fun canScheduleExact(): Boolean = ProtectionScheduler.canScheduleExact(context)
}
