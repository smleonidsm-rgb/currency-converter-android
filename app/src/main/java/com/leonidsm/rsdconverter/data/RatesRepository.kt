package com.leonidsm.rsdconverter.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

private val Context.ratesDataStore by preferencesDataStore(name = "rates_cache")

val DEFAULT_WATCHLIST = listOf("RSD", "RUB", "EUR", "USD")

/** How long a cached snapshot is considered fresh; refreshes inside this window are skipped. */
const val CACHE_FRESHNESS_MILLIS = 60L * 60L * 1000L // 1 hour

@Serializable
data class RatesSnapshot(
    val base: String,
    val rates: Map<String, Double>,
    val fetchedAtEpochMillis: Long,
)

/**
 * A snapshot plus the last rates we saw on a *different* calendar day, so the UI
 * can show a day-over-day change arrow without a dedicated history API.
 */
@Serializable
data class RatesRecord(
    val current: RatesSnapshot,
    val previousDayRates: Map<String, Double>? = null,
    val previousDayEpochMillis: Long? = null,
)

private object Keys {
    val RECORDS_JSON = stringPreferencesKey("cached_records_json")
    val WATCHLIST = stringPreferencesKey("watchlist_codes")
    val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val LAST_BASE = stringPreferencesKey("last_base")
}

class RatesRepository(private val context: Context) {

    private val api = NetworkModule.api
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param force when false, a still-fresh cached record for [base] is returned
     *   without hitting the network - the main guard against burning API quota.
     */
    suspend fun fetchLatestRates(
        apiKey: String,
        base: String,
        force: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<RatesRecord> {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return Result.failure(RatesException(RatesError.InvalidKey))
        }

        val allRecords = loadAllRecords().toMutableMap()
        val existing = allRecords[base]
        if (!force && existing != null &&
            nowMillis - existing.current.fetchedAtEpochMillis < CACHE_FRESHNESS_MILLIS
        ) {
            return Result.success(existing)
        }

        return try {
            val response = api.getLatestRates(apiKey = apiKey, base = base)
            val rates = response.conversionRates.filterKeys { it != base }
            val newSnapshot = RatesSnapshot(
                base = base,
                rates = rates,
                fetchedAtEpochMillis = nowMillis,
            )
            val record = buildRecord(existing, newSnapshot)
            allRecords[base] = record
            saveAllRecords(allRecords)
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(RatesException(e.toRatesError()))
        }
    }

    suspend fun cachedRecords(): Map<String, RatesRecord> = loadAllRecords()

    /** Whether [base] has a cached record younger than [CACHE_FRESHNESS_MILLIS]. */
    suspend fun hasFreshCache(base: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val existing = loadAllRecords()[base] ?: return false
        return nowMillis - existing.current.fetchedAtEpochMillis < CACHE_FRESHNESS_MILLIS
    }

    private fun Exception.toRatesError(): RatesError = when (this) {
        is IOException -> RatesError.NoNetwork
        is HttpException -> parseHttpError(this)
        else -> RatesError.Unknown(message)
    }

    private fun parseHttpError(e: HttpException): RatesError {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val errorType = body?.let {
            runCatching { json.decodeFromString<ExchangeRateError>(it).errorType }.getOrNull()
        }
        return when (errorType) {
            "invalid-key" -> RatesError.InvalidKey
            "inactive-account" -> RatesError.InactiveAccount
            "quota-reached" -> RatesError.QuotaReached
            "unsupported-code" -> RatesError.UnsupportedCode(code = "")
            else -> if (e.code() == 429) RatesError.QuotaReached else RatesError.Unknown("HTTP ${e.code()}")
        }
    }

    suspend fun loadWatchlist(): List<String> {
        val raw = context.ratesDataStore.data.first()[Keys.WATCHLIST] ?: return DEFAULT_WATCHLIST
        val codes = raw.split(",").filter { it.isNotBlank() }
        return codes.ifEmpty { DEFAULT_WATCHLIST }
    }

    suspend fun saveWatchlist(codes: List<String>) {
        context.ratesDataStore.edit { prefs ->
            prefs[Keys.WATCHLIST] = codes.joinToString(",")
        }
    }

    suspend fun loadLastBase(): String? =
        context.ratesDataStore.data.first()[Keys.LAST_BASE]

    suspend fun saveLastBase(code: String) {
        context.ratesDataStore.edit { prefs ->
            prefs[Keys.LAST_BASE] = code
        }
    }

    suspend fun loadUseDynamicColor(): Boolean =
        context.ratesDataStore.data.first()[Keys.DYNAMIC_COLOR] ?: false

    suspend fun saveUseDynamicColor(value: Boolean) {
        context.ratesDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = value
        }
    }

    private suspend fun loadAllRecords(): Map<String, RatesRecord> {
        val raw = context.ratesDataStore.data.first()[Keys.RECORDS_JSON] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, RatesRecord>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveAllRecords(records: Map<String, RatesRecord>) {
        context.ratesDataStore.edit { prefs ->
            prefs[Keys.RECORDS_JSON] = json.encodeToString(records)
        }
    }
}
