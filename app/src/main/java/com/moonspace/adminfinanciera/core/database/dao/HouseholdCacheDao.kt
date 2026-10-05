package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdCacheDao {
    @Query("SELECT * FROM household_cache WHERE account_id = :accountId LIMIT 1")
    suspend fun getHousehold(accountId: String): HouseholdCacheEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHousehold(entity: HouseholdCacheEntity)

    @Query("UPDATE household_cache SET household_id = :householdId, current_user_role = :role, updated_at = :updatedAt WHERE account_id = :accountId")
    suspend fun updateHousehold(accountId: String, householdId: String?, role: String?, updatedAt: Long): Int

    suspend fun saveHousehold(entity: HouseholdCacheEntity) {
        val updated = updateHousehold(
            entity.accountId,
            entity.householdId,
            entity.currentUserRole,
            entity.updatedAt
        )
        if (updated == 0) insertHousehold(entity)
    }

    @Query("SELECT * FROM household_member_cache WHERE account_id = :accountId ORDER BY email COLLATE NOCASE")
    suspend fun getMembers(accountId: String): List<HouseholdMemberCacheEntity>

    @Query("DELETE FROM household_member_cache WHERE account_id = :accountId")
    suspend fun clearMembers(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMembers(entities: List<HouseholdMemberCacheEntity>)

    suspend fun saveSnapshot(
        household: HouseholdCacheEntity,
        members: List<HouseholdMemberCacheEntity>
    ) {
        saveHousehold(household)
        clearMembers(household.accountId)
        if (members.isNotEmpty()) saveMembers(members)
    }
}
