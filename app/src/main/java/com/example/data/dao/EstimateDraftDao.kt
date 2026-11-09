package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.EstimateDraft
import kotlinx.coroutines.flow.Flow

@Dao
interface EstimateDraftDao {
    @Query("SELECT * FROM estimate_drafts ORDER BY updatedAt DESC")
    fun getAllDrafts(): Flow<List<EstimateDraft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: EstimateDraft)

    @Query("DELETE FROM estimate_drafts WHERE id = :draftId")
    suspend fun deleteDraft(draftId: String)
}
