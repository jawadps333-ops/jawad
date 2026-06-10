package com.example.game.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = 1,
    val money: Int = 250, // Starts with some cash to steel/buy
    val health: Int = 100,
    val armor: Int = 0,
    val ownedHouses: String = "", // Comma-separated IDs like "apartment_1"
    val respect: Int = 30,
    val posX: Float = 120f,
    val posY: Float = 120f,
    val posAngle: Float = 0f,
    val weapon: String = "FISTS" // FISTS, PISTOL, REVOLVER, TEC9
)
