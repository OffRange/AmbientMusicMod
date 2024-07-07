package com.kieronquinn.app.ambientmusicmod.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.app.ambientmusicmod.R
import com.kieronquinn.app.ambientmusicmod.components.navigation.ContainerNavigation
import com.kieronquinn.app.ambientmusicmod.repositories.DeviceConfigRepository
import com.kieronquinn.app.ambientmusicmod.repositories.SettingsRepository
import com.kieronquinn.app.ambientmusicmod.repositories.SettingsRepository.RecognitionBuffer
import com.kieronquinn.app.ambientmusicmod.repositories.SettingsRepository.RecognitionPeriod
import com.kieronquinn.app.ambientmusicmod.repositories.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class SettingsViewModel: ViewModel() {

    abstract val state: StateFlow<State>

    abstract fun onRecognitionPeriodClicked()
    abstract fun onRecognitionBufferClicked()
    abstract fun onTriggerWhenScreenOnChanged(enabled: Boolean)
    abstract fun onBedtimeClicked()
    abstract fun onAdvancedClicked()
    abstract fun onAlbumArtChanged(enabled: Boolean)
    abstract fun onUseOnlineAfterLocalFailedChanged(enabled: Boolean)
    abstract fun onHistorySummaryDaysChanged(context: Context, days: HistorySummaryDays)

    sealed class State {
        object Loading: State()
        data class Loaded(
            val recognitionPeriod: RecognitionPeriod,
            val recognitionBuffer: RecognitionBuffer,
            val triggerWhenScreenOn: Boolean,
            val bedtimeMode: Boolean,
            val albumArtEnabled: Boolean,
            val useOnlineAfterLocalFailed: Boolean,
            val supportsSummary: Boolean,
            val historySummaryDays: HistorySummaryDays
        ): State()
    }

    enum class HistorySummaryDays(val days: Int, @StringRes val label: Int) {
        ONE_DAY(1, R.string.settings_history_summary_1_day),
        ONE_WEEK(7, R.string.settings_history_summary_7_days),
        TWO_WEEKS(14, R.string.settings_history_summary_14_days),
        ONE_MONTH(30, R.string.settings_history_summary_30_days),
        TWO_MONTHS(60, R.string.settings_history_summary_60_days),
        ONE_YEAR(365, R.string.settings_history_summary_365_days);

        fun isAtLeast(other: HistorySummaryDays): Boolean {
            return ordinal >= other.ordinal
        }

        companion object {
            internal fun forDays(days: Int): HistorySummaryDays {
                return values().firstOrNull { it.days == days } ?: ONE_MONTH
            }
        }
    }

}

fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> = combine(flow, flow2, flow3, flow4, flow5, flow6) { args: Array<*> ->
    @Suppress("UNCHECKED_CAST")
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6
    )
}

class SettingsViewModelImpl(
    settingsRepository: SettingsRepository,
    deviceConfigRepository: DeviceConfigRepository,
    updatesRepository: UpdatesRepository,
    private val navigation: ContainerNavigation
): SettingsViewModel() {

    private val triggerWhenScreenOn = settingsRepository.triggerWhenScreenOn
    private val showAlbumArt = deviceConfigRepository.showAlbumArt
    private val useOnlineAfterLocalFailed = deviceConfigRepository.useOnlineAfterLocalFailed
    private val historySummaryDays = deviceConfigRepository.historySummaryDays

    private val recognitionState = combine(
        settingsRepository.recognitionPeriod.asFlow(),
        settingsRepository.recognitionBuffer.asFlow()
    ) { period, buffer ->
        Pair(period, buffer)
    }

    override val state = combine(
        recognitionState,
        triggerWhenScreenOn.asFlow(),
        settingsRepository.bedtimeModeEnabled.asFlow(),
        showAlbumArt.asFlow(),
        useOnlineAfterLocalFailed.asFlow(),
        historySummaryDays.asFlow()
    ) { recognitionState, screenOn, bedtime, albumArt, useOnlineAfterLocalFailed, days ->
        State.Loaded(
            recognitionState.first,
            recognitionState.second,
            screenOn,
            bedtime,
            albumArt,
            useOnlineAfterLocalFailed,
            updatesRepository.doesPAMSupportSummaryAndEditing(),
            HistorySummaryDays.forDays(days)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State.Loading)

    override fun onRecognitionPeriodClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsRecognitionPeriodFragment())
        }
    }

    override fun onRecognitionBufferClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsRecognitionBufferFragment())
        }
    }

    override fun onTriggerWhenScreenOnChanged(enabled: Boolean) {
        viewModelScope.launch {
            triggerWhenScreenOn.set(enabled)
        }
    }

    override fun onAlbumArtChanged(enabled: Boolean) {
        viewModelScope.launch {
            showAlbumArt.set(enabled)
        }
    }

    override fun onUseOnlineAfterLocalFailedChanged(enabled: Boolean) {
        viewModelScope.launch {
            useOnlineAfterLocalFailed.set(enabled)
        }
    }

    override fun onHistorySummaryDaysChanged(context: Context, days: HistorySummaryDays) {
        viewModelScope.launch {
            if(days.isAtLeast(HistorySummaryDays.TWO_MONTHS)){
                Toast.makeText(
                    context, R.string.settings_history_summary_toast, Toast.LENGTH_LONG
                ).show()
            }
            historySummaryDays.set(days.days)
        }
    }

    override fun onBedtimeClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsBedtimeFragment())
        }
    }

    override fun onAdvancedClicked() {
        viewModelScope.launch {
            navigation.navigate(SettingsFragmentDirections.actionSettingsFragmentToSettingsAdvancedFragment())
        }
    }

}