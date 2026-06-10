package com.example.game.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val playerDao: PlayerDao) {
    val playerState: Flow<PlayerStateEntity?> = playerDao.getPlayerState()

    suspend fun saveState(state: PlayerStateEntity) {
        playerDao.savePlayerState(state)
    }

    suspend fun resetState() {
        playerDao.clearPlayerState()
    }
}
