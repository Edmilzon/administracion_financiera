package com.moonspace.adminfinanciera.feature.auth.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedHashMap

internal data class NeonAuthResponse(
    val userId: String?,
    val email: String?,
    val name: String?,
    val sessionCookie: String?,
    val accessToken: String?,
    val emailVerificationRequired: Boolean = false
)

internal class NeonAuthHttpException(
    val statusCode: Int,
    val errorCode: String? = null
) : IOException("Neon Auth request failed with HTTP $statusCode.")

/** Native HTTP adapter for Neon Auth's Better Auth endpoints. Passwords are sent only for the request. */
internal class NeonAuthRestClient(
    private val authApiBaseUrl: HttpUrl?,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .build()
) {
    suspend fun signIn(email: String, password: String): NeonAuthResponse =
        post("sign-in/email", JSONObject().put("email", email).put("password", password), cookieHeader = null)

    suspend fun signUp(email: String, password: String): NeonAuthResponse =
        post(
            "sign-up/email",
            JSONObject()
                .put("name", email.substringBefore('@').ifBlank { email })
                .put("email", email)
                .put("password", password),
            cookieHeader = null
        )

    suspend fun getSession(cookieHeader: String): NeonAuthResponse =
        execute("get-session", method = "GET", cookieHeader = cookieHeader)

    suspend fun updateProfileName(name: String, cookieHeader: String): NeonAuthResponse =
        post("update-user", JSONObject().put("name", name), cookieHeader)

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        cookieHeader: String
    ): NeonAuthResponse = post(
        "change-password",
        JSONObject()
            .put("currentPassword", currentPassword)
            .put("newPassword", newPassword)
            .put("revokeOtherSessions", false),
        cookieHeader
    )

    suspend fun signOut(cookieHeader: String) {
        execute("sign-out", method = "POST", payload = JSONObject(), cookieHeader = cookieHeader)
    }

    private suspend fun post(
        route: String,
        payload: JSONObject,
        cookieHeader: String?
    ): NeonAuthResponse = execute(route, "POST", payload, cookieHeader)

    private suspend fun execute(
        route: String,
        method: String,
        payload: JSONObject? = null,
        cookieHeader: String?
    ): NeonAuthResponse = withContext(Dispatchers.IO) {
        val apiBase = authApiBaseUrl ?: throw IllegalStateException("Neon Auth HTTPS URL is not configured.")
        val url = apiBase.newBuilder().addPathSegments(route).build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header(
                "Origin",
                apiBase.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
            )
        if (cookieHeader != null) requestBuilder.header("Cookie", cookieHeader)
        if (method == "POST") requestBuilder.header("Content-Type", "application/json")
        val requestBody = payload?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        requestBuilder.method(method, requestBody)

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrNull()
            if (!response.isSuccessful) {
                throw NeonAuthHttpException(response.code, json?.optString("code")?.takeIf(String::isNotBlank))
            }

            val user = json?.optJSONObject("user")
                ?: json?.optJSONObject("data")?.optJSONObject("user")
                ?: json?.takeIf { it.has("id") }
            val mergedCookie = mergeCookies(cookieHeader, response.headers.values("Set-Cookie"))
            NeonAuthResponse(
                userId = user?.optString("id")?.takeIf(String::isNotBlank),
                email = user?.optString("email")?.takeIf(String::isNotBlank),
                name = user?.optString("name")?.takeIf(String::isNotBlank),
                sessionCookie = mergedCookie,
                accessToken = response.header("set-auth-jwt")?.takeIf(String::isNotBlank)
                    ?: json?.optJSONObject("session")?.optString("token")?.takeIf(String::isNotBlank)
                    ?: json?.optString("token")?.takeIf(String::isNotBlank),
                emailVerificationRequired = json?.optBoolean("verificationRequired") == true ||
                    json?.optBoolean("emailVerificationRequired") == true
            )
        }
    }

    private fun mergeCookies(previousCookie: String?, setCookieHeaders: List<String>): String? {
        val cookies = LinkedHashMap<String, String>()
        previousCookie.orEmpty().split(';').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator > 0) cookies[pair.substring(0, separator).trim()] = pair.substring(separator + 1).trim()
        }
        setCookieHeaders.forEach { header ->
            val pair = header.substringBefore(';').trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            val attributes = header.substringAfter(';', "")
            val expired = attributes.split(';').any { it.trim().equals("Max-Age=0", ignoreCase = true) }
            if (expired || pair.substring(separator + 1).isBlank()) cookies.remove(name)
            else cookies[name] = pair.substring(separator + 1).trim()
        }
        return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
            .takeIf(String::isNotBlank)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
