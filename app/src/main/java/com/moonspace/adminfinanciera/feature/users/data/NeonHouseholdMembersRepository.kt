package com.moonspace.adminfinanciera.feature.users.data

import android.content.Context
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.core.network.NeonDataApiMethod
import com.moonspace.adminfinanciera.core.network.NeonDataApiResponse
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.auth.domain.MemberAccountProvisionResult
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMember
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdSnapshot
import com.moonspace.adminfinanciera.feature.users.domain.MemberCreationResult
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class NeonHouseholdMembersRepository(
    context: Context,
    private val config: NeonApiConfig,
    private val dataApiClient: NeonDataApiClient,
    private val authRepository: AuthRepository
) : HouseholdMembersRepository {
    private val appContext = context.applicationContext

    override suspend fun load(currentUser: AuthUser): HouseholdSnapshot {
        requireDataApi()
        val ownRows = request(
            resource = "household_members",
            method = NeonDataApiMethod.GET,
            query = mapOf(
                "select" to "household_id,user_id,email,role",
                "user_id" to "eq.${currentUser.id}"
            )
        ).toRows()

        val ownMembership = ownRows.firstOrNull()
            ?: return HouseholdSnapshot(householdId = null, currentUserRole = null, members = emptyList())
        val householdId = ownMembership.optString("household_id").takeIf(String::isNotBlank)
            ?: fail(R.string.users_invalid_response)
        val currentRole = ownMembership.roleOrFail()

        if (currentRole != HouseholdRole.Admin) {
            return HouseholdSnapshot(
                householdId = householdId,
                currentUserRole = currentRole,
                members = listOf(ownMembership.toHouseholdMember())
            )
        }

        val members = request(
            resource = "household_members",
            method = NeonDataApiMethod.GET,
            query = mapOf(
                "select" to "household_id,user_id,email,role",
                "household_id" to "eq.$householdId",
                "order" to "email.asc"
            )
        ).toRows().map { it.toHouseholdMember() }

        return HouseholdSnapshot(householdId, currentRole, members)
    }

    override suspend fun createHousehold(currentUser: AuthUser, name: String) {
        requireDataApi()
        val current = load(currentUser)
        if (current.householdId != null) fail(R.string.users_space_already_exists)
        request(
            resource = "rpc/create_household",
            method = NeonDataApiMethod.POST,
            body = JSONObject()
                .put("p_name", name.trim())
                .put("p_email", currentUser.email)
        )
    }

    override suspend fun createMember(
        currentUser: AuthUser,
        email: String,
        password: String,
        role: HouseholdRole
    ): MemberCreationResult {
        requireDataApi()
        requireAdmin(currentUser)
        val provisionedAccount = when (val result = authRepository.createMemberAccount(email, password)) {
            is MemberAccountProvisionResult.Ready -> result
            is MemberAccountProvisionResult.Failure -> throw UserManagementException(result.message)
        }

        try {
            request(
                resource = "rpc/add_household_member",
                method = NeonDataApiMethod.POST,
                body = JSONObject()
                    .put("p_user_id", provisionedAccount.user.id)
                    .put("p_email", provisionedAccount.user.email)
                    .put("p_role", role.apiValue)
            )
        } catch (error: UserManagementException) {
            throw UserManagementException(
                appContext.getString(R.string.users_account_created_not_linked),
                error
            )
        }

        return MemberCreationResult(
            isNewAccount = provisionedAccount.isNewAccount,
            emailVerificationRequired = provisionedAccount.emailVerificationRequired
        )
    }

    override suspend fun updateRole(currentUser: AuthUser, targetUserId: String, role: HouseholdRole) {
        requireDataApi()
        requireAdmin(currentUser)
        request(
            resource = "rpc/set_household_member_role",
            method = NeonDataApiMethod.POST,
            body = JSONObject()
                .put("p_target_user_id", targetUserId)
                .put("p_role", role.apiValue)
        )
    }

    override suspend fun removeMember(currentUser: AuthUser, targetUserId: String) {
        requireDataApi()
        requireAdmin(currentUser)
        request(
            resource = "rpc/remove_household_member",
            method = NeonDataApiMethod.POST,
            body = JSONObject().put("p_target_user_id", targetUserId)
        )
    }

    private suspend fun requireAdmin(currentUser: AuthUser) {
        val snapshot = load(currentUser)
        if (snapshot.householdId == null) fail(R.string.users_create_space_first)
        if (snapshot.currentUserRole != HouseholdRole.Admin) fail(R.string.users_permission_denied)
    }

    private suspend fun request(
        resource: String,
        method: NeonDataApiMethod,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null
    ): NeonDataApiResponse {
        val response = try {
            dataApiClient.request(resource, method, query, body)
        } catch (_: IllegalStateException) {
            fail(R.string.auth_data_api_missing)
        } catch (_: IOException) {
            fail(R.string.users_connection_failed)
        } catch (_: Exception) {
            fail(R.string.users_request_failed)
        }

        if (!response.isSuccessful) {
            when (response.statusCode) {
                401, 403 -> fail(R.string.users_permission_denied)
                409 -> fail(R.string.users_member_already_linked)
                else -> fail(R.string.users_request_failed)
            }
        }
        return response
    }

    private fun requireDataApi() {
        if (!config.isDataApiConfigured) fail(R.string.auth_data_api_missing)
    }

    private fun NeonDataApiResponse.toRows(): List<JSONObject> = try {
        if (body.isBlank()) emptyList()
        else {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
        }
    } catch (error: Exception) {
        throw UserManagementException(appContext.getString(R.string.users_invalid_response), error)
    }

    private fun JSONObject.roleOrFail(): HouseholdRole =
        HouseholdRole.fromApiValue(optString("role")) ?: fail(R.string.users_invalid_response)

    private fun JSONObject.toHouseholdMember(): HouseholdMember {
        val userId = optString("user_id").takeIf(String::isNotBlank)
            ?: fail(R.string.users_invalid_response)
        val email = optString("email").takeIf(String::isNotBlank)
            ?: fail(R.string.users_invalid_response)
        return HouseholdMember(userId = userId, email = email, role = roleOrFail())
    }

    private fun fail(messageResource: Int): Nothing =
        throw UserManagementException(appContext.getString(messageResource))
}
