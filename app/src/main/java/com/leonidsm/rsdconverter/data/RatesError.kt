package com.leonidsm.rsdconverter.data

/**
 * Why a rate fetch failed. Kept distinct from a blanket "offline" state so the
 * UI can tell the user whether to check their connection, their API key, or
 * their plan quota.
 */
sealed class RatesError {
    /** No usable connection - request never reached the server. */
    data object NoNetwork : RatesError()

    /** EXCHANGE_API_KEY is missing, malformed, or rejected by the provider. */
    data object InvalidKey : RatesError()

    /** Monthly request allowance for the plan is used up. */
    data object QuotaReached : RatesError()

    /** Key is valid but the account is not active yet. */
    data object InactiveAccount : RatesError()

    /** Provider does not support one of the currencies we asked for. */
    data class UnsupportedCode(val code: String) : RatesError()

    data class Unknown(val detail: String?) : RatesError()
}

/** Carries a [RatesError] through [Result.failure]. */
class RatesException(val error: RatesError) : Exception(error.toString())
