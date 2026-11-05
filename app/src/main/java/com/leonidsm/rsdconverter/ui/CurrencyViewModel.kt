package com.leonidsm.rsdconverter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leonidsm.rsdconverter.BuildConfig
import com.leonidsm.rsdconverter.data.DEFAULT_WATCHLIST
import com.leonidsm.rsdconverter.data.RatesError
import com.leonidsm.rsdconverter.data.RatesException
import com.leonidsm.rsdconverter.data.RatesRecord
import com.leonidsm.rsdconverter.data.RatesRepository
import com.leonidsm.rsdconverter.data.sanitizeAmountInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

data class CurrencyUiState(
    val fromCurrency: String = "RSD",
    val amountInput: String = "100",
    val watchedCurrencies: List<String> = DEFAULT_WATCHLIST,
    val recordsByBase: Map<String, RatesRecord> = emptyMap(),
    val isLoading: Boolean = true,
    val error: RatesError? = null,
    val useDynamicColor: Boolean = false,
) {
    val currentRecord: RatesRecord?
        get() = recordsByBase[fromCurrency]
}

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RatesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(CurrencyUiState())
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    // Restores persisted state on startup; auto-refresh waits on this so it never
    // fetches before we know the last-used base and cached data.
    private val initJob: Job = viewModelScope.launch {
        val cachedRecords = repository.cachedRecords()
        val lastBase = repository.loadLastBase()
        _uiState.update {
            it.copy(
                recordsByBase = cachedRecords,
                fromCurrency = lastBase ?: it.fromCurrency,
                watchedCurrencies = repository.loadWatchlist(),
                useDynamicColor = repository.loadUseDynamicColor(),
                isLoading = false,
            )
        }
    }

    /**
     * Driven by the screen inside repeatOnLifecycle(STARTED): it ticks hourly
     * while the UI is visible and is cancelled when it goes to background, so the
     * app stops consuming API quota the moment it leaves the foreground. The
     * cache-freshness guard means resuming within the hour costs no request.
     */
    suspend fun runAutoRefreshWhileVisible() {
        initJob.join()
        while (true) {
            fetch(base = _uiState.value.fromCurrency, force = false)
            delay(1.hours)
        }
    }

    /** Manual pull: always hits the network for the current base. */
    fun refresh() {
        viewModelScope.launch {
            initJob.join()
            fetch(base = _uiState.value.fromCurrency, force = true)
        }
    }

    private suspend fun fetch(base: String, force: Boolean) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        repository.saveLastBase(base)
        val result = repository.fetchLatestRates(BuildConfig.EXCHANGE_API_KEY, base, force = force)
        result.onSuccess { record ->
            _uiState.update {
                it.copy(
                    recordsByBase = it.recordsByBase + (base to record),
                    isLoading = false,
                    error = null,
                )
            }
        }.onFailure { throwable ->
            val error = (throwable as? RatesException)?.error ?: RatesError.Unknown(throwable.message)
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }

    fun onFromCurrencyChange(code: String) {
        if (code == _uiState.value.fromCurrency) return
        _uiState.update { it.copy(fromCurrency = code) }
        // Cached data for this base (if any) shows instantly; only touch the
        // network when that base's cache is missing or stale.
        viewModelScope.launch {
            initJob.join()
            fetch(base = code, force = false)
        }
    }

    /**
     * Tapping a currency in the results list promotes it to the source (main bar);
     * the previous source takes the tapped currency's slot in the watchlist, so the
     * two visually swap places.
     */
    fun onWatchedCurrencyTap(code: String) {
        val state = _uiState.value
        val oldSource = state.fromCurrency
        if (code == oldSource) return

        val list = state.watchedCurrencies.toMutableList()
        val tappedIdx = list.indexOf(code)
        val oldSourceIdx = list.indexOf(oldSource)
        when {
            tappedIdx >= 0 && oldSourceIdx >= 0 -> {
                list[tappedIdx] = oldSource
                list[oldSourceIdx] = code
            }
            // Old source wasn't in the list (picked via dialog): it just takes the
            // tapped currency's place.
            tappedIdx >= 0 -> list[tappedIdx] = oldSource
        }

        _uiState.update { it.copy(fromCurrency = code, watchedCurrencies = list) }
        viewModelScope.launch {
            repository.saveWatchlist(list)
            initJob.join()
            fetch(base = code, force = false)
        }
    }

    fun onAmountChange(newValue: String) {
        _uiState.update { it.copy(amountInput = sanitizeAmountInput(newValue)) }
    }

    fun addWatchedCurrency(code: String) {
        val current = _uiState.value.watchedCurrencies
        if (code in current) return
        val updated = current + code
        _uiState.update { it.copy(watchedCurrencies = updated) }
        viewModelScope.launch { repository.saveWatchlist(updated) }
    }

    fun removeWatchedCurrency(code: String) {
        val updated = _uiState.value.watchedCurrencies - code
        _uiState.update { it.copy(watchedCurrencies = updated) }
        viewModelScope.launch { repository.saveWatchlist(updated) }
    }

    fun toggleDynamicColor() {
        val updated = !_uiState.value.useDynamicColor
        _uiState.update { it.copy(useDynamicColor = updated) }
        viewModelScope.launch { repository.saveUseDynamicColor(updated) }
    }
}
