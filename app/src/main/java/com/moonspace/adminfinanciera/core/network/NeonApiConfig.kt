package com.moonspace.adminfinanciera.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Public HTTPS endpoints only. Database credentials and Neon management keys do not belong here. */
class NeonApiConfig(
    authBaseUrl: String,
    dataApiBaseUrl: String
) {
    private val authBase = parsePublicHttpsUrl(authBaseUrl)
    private val dataApiBase = parsePublicHttpsUrl(dataApiBaseUrl)

    val isAuthConfigured: Boolean
        get() = authBase != null

    val isDataApiConfigured: Boolean
        get() = dataApiBase != null

    /** Neon Auth's console URL already includes its route (for example `/neondb/auth`). */
    fun authApiBaseUrl(): HttpUrl? = authBase?.let { base ->
        val path = base.encodedPath.trimEnd('/')
        if (path.endsWith("/auth")) {
            base
        } else {
            base.newBuilder().addPathSegments("api/auth").build()
        }
    }

    fun dataApiBaseUrl(): HttpUrl? = dataApiBase

    private fun parsePublicHttpsUrl(value: String): HttpUrl? {
        val parsed = value.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https" || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return null
        }
        if (parsed.query != null || parsed.fragment != null) return null
        return parsed
    }
}
