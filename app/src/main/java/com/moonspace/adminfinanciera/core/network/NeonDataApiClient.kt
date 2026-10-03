package com.moonspace.adminfinanciera.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

enum class NeonDataApiMethod {
    GET,
    POST,
    PATCH,
    DELETE
}

data class NeonDataApiResponse(
    val statusCode: Int,
    val body: String
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

/** Small PostgREST-compatible client. RLS remains the authority for every database operation. */
class NeonDataApiClient(
    private val config: NeonApiConfig,
    private val accessTokenProvider: NeonAccessTokenProvider,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .build()
) {
    suspend fun request(
        resource: String,
        method: NeonDataApiMethod,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null
    ): NeonDataApiResponse = withContext(Dispatchers.IO) {
        val baseUrl = config.dataApiBaseUrl()
            ?: throw IllegalStateException("Neon Data API is not configured with an HTTPS URL.")
        val token = accessTokenProvider.getAccessToken()
            ?: throw IOException("A valid Neon Auth session is required for Data API requests.")
        val normalizedResource = resource.trim('/')
        require(normalizedResource.isNotBlank()) { "A Data API resource is required." }
        require(normalizedResource.split('/').none { it == "." || it == ".." }) {
            "The Data API resource path is invalid."
        }

        val urlBuilder = baseUrl.newBuilder().addPathSegments(normalizedResource)
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
        if (body != null) builder.header("Content-Type", "application/json")
        builder.method(method.name, requestBody)

        httpClient.newCall(builder.build()).execute().use { response ->
            NeonDataApiResponse(
                statusCode = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
