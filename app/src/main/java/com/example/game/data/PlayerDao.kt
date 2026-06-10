package com.example.game.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM player_state WHERE id = 1 LIMIT 1")
    fun getPlayerState(): Flow<PlayerStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayerState(state: PlayerStateEntity)

    @Query("DELETE FROM player_state")
    suspend fun clearPlayerState()
}
