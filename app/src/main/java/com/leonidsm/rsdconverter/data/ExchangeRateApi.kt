package com.leonidsm.rsdconverter.data

import retrofit2.http.GET
import retrofit2.http.Path

interface ExchangeRateApi {
    /**
     * https://www.exchangerate-api.com/docs/standard-requests
     * Free plan refreshes conversion_rates roughly once every 24h.
     */
    @GET("v6/{apiKey}/latest/{base}")
    suspend fun getLatestRates(
        @Path("apiKey") apiKey: String,
        @Path("base") base: String,
    ): ExchangeRateResponse
}
