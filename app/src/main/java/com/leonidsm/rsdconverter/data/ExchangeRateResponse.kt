package com.leonidsm.rsdconverter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExchangeRateResponse(
    @SerialName("result") val result: String,
    @SerialName("time_last_update_unix") val lastUpdateUnixSeconds: Long = 0,
    @SerialName("base_code") val baseCode: String = "",
    @SerialName("conversion_rates") val conversionRates: Map<String, Double> = emptyMap(),
)

/**
 * Error payload the API returns on 4xx responses, e.g.
 * {"result":"error","error-type":"invalid-key"}.
 */
@Serializable
data class ExchangeRateError(
    @SerialName("result") val result: String = "error",
    @SerialName("error-type") val errorType: String? = null,
)
