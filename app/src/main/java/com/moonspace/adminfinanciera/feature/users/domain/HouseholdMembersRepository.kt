package com.moonspace.adminfinanciera.feature.users.domain

import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser

enum class HouseholdRole(val apiValue: String) {
    Admin("admin"),
    Member("member");

    companion object {
        fun fromApiValue(value: String): HouseholdRole? = entries.firstOrNull { it.apiValue == value }
    }
}

data class HouseholdMember(
    val userId: String,
    val email: String,
    val role: HouseholdRole
)

data class HouseholdSnapshot(
    val householdId: String?,
    val currentUserRole: HouseholdRole?,
    val members: List<HouseholdMember>
)

data class MemberCreationResult(
    val isNewAccount: Boolean,
    val emailVerificationRequired: Boolean
)

class UserManagementException(
    message: String,
    cause: Throwable? = null,
    val httpStatusCode: Int? = null
) : Exception(message, cause)

interface HouseholdMembersRepository {
    suspend fun load(currentUser: AuthUser): HouseholdSnapshot
    suspend fun createHousehold(currentUser: AuthUser, name: String)
    suspend fun createMember(
        currentUser: AuthUser,
        email: String,
        password: String,
        role: HouseholdRole
    ): MemberCreationResult

    suspend fun updateRole(currentUser: AuthUser, targetUserId: String, role: HouseholdRole)
    suspend fun removeMember(currentUser: AuthUser, targetUserId: String)
}
