package com.moonspace.adminfinanciera.core.network

/** Supplies a short-lived Neon Auth JWT for requests to Neon Data API. */
fun interface NeonAccessTokenProvider {
    suspend fun getAccessToken(): String?
}
