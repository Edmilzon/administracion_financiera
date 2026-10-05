package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE household_id = :householdId AND is_active = 1 ORDER BY kind, name COLLATE NOCASE")
    fun observeActive(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE household_id = :householdId ORDER BY kind, name COLLATE NOCASE")
    fun observeAll(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND household_id = :householdId LIMIT 1")
    suspend fun findById(householdId: String, categoryId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE household_id = :householdId AND kind = :kind AND name_key = :nameKey LIMIT 1")
    suspend fun findByName(householdId: String, kind: String, nameKey: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId AND kind = :kind AND name_key = :nameKey AND id != :excludedId")
    suspend fun countNameConflict(householdId: String, kind: String, nameKey: String, excludedId: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId AND kind = :kind AND is_active = 1")
    suspend fun countActive(householdId: String, kind: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId")
    suspend fun countForHousehold(householdId: String): Int

    @Insert
    suspend fun insert(entity: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity): Int

    @Transaction
    suspend fun upsert(entity: CategoryEntity) {
        if (insertIfMissing(entity) == -1L && update(entity) == 0) {
            throw IllegalStateException("A local category conflicts with a remote category identifier.")
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE household_id = :householdId AND category_id = :categoryId")
    suspend fun countForCategory(householdId: String, categoryId: String): Int

    @Query("DELETE FROM categories WHERE household_id = :householdId AND id = :categoryId")
    suspend fun deleteById(householdId: String, categoryId: String)

    @Query("DELETE FROM categories WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)
}
