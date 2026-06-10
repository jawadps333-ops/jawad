package com.example.game.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.GameAudio
import com.example.game.data.GameDatabase
import com.example.game.data.GameRepository
import com.example.game.data.PlayerStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Screen enumeration
enum class GameScreen {
    SPLASH,
    MAIN_MENU,
    LOADING,
    GAMEPLAY,
    STATS
}

// Game models
data class GameVehicle(
    val id: Int,
    var x: Float,
    var y: Float,
    var angle: Float,
    var speed: Float,
    val colorHex: Long,
    var isPlayerDriven: Boolean = false,
    var health: Int = 100,
    var modelType: Int = 0 // 0=sedan, 1=sport, 2=pickup
)

data class GameNpc(
    val id: Int,
    var x: Float,
    var y: Float,
    var angle: Float,
    var speed: Float,
    var type: String, // "citizen", "gangster", "cop"
    var health: Int = 100,
    var isDead: Boolean = false,
    var speedMultiplier: Float = 1f,
    var actionTimer: Int = 0,
    var targetX: Float = 0f,
    var targetY: Float = 0f
)

data class GameProjectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var isRpg: Boolean = false,
    var decay: Int = 60
)

data class GameMoneyDrop(
    val id: Int,
    val x: Float,
    val y: Float,
    val amount: Int
)

data class GameHouse(
    val id: String,
    val name: String,
    val price: Int,
    val x: Float,
    val y: Float,
    var isOwned: Boolean,
    val desc: String
)

data class GameBuilding(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val colorHex: Long,
    val r: Int = 0, // roof design
    val isApartment: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    init {
        val db = GameDatabase.getDatabase(application)
        repository = GameRepository(db.playerDao)
    }

    // Screens navigation
    private val _currentScreen = MutableStateFlow(GameScreen.SPLASH)
    val currentScreen: StateFlow<GameScreen> = _currentScreen.asStateFlow()

    // Loading progress
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    // Game stats
    private val _money = MutableStateFlow(250)
    val money: StateFlow<Int> = _money.asStateFlow()

    private val _health = MutableStateFlow(100)
    val health: StateFlow<Int> = _health.asStateFlow()

    private val _armor = MutableStateFlow(0)
    val armor: StateFlow<Int> = _armor.asStateFlow()

    private val _respect = MutableStateFlow(10)
    val respect: StateFlow<Int> = _respect.asStateFlow()

    private val _currentWeapon = MutableStateFlow("FISTS") // FISTS, PISTOL, TEC9, RPG
    val currentWeapon: StateFlow<String> = _currentWeapon.asStateFlow()

    private val _wantedLevel = MutableStateFlow(0) // 0 to 5 stars
    val wantedLevel: StateFlow<Int> = _wantedLevel.asStateFlow()

    private val _playerX = MutableStateFlow(140f) // Starts at bottom-left corner
    val playerX: StateFlow<Float> = _playerX.asStateFlow()

    private val _playerY = MutableStateFlow(1080f) // Starts at bottom-left corner
    val playerY: StateFlow<Float> = _playerY.asStateFlow()

    private val _playerAngle = MutableStateFlow(0f) // yaw radians
    val playerAngle: StateFlow<Float> = _playerAngle.asStateFlow()

    private val _drivenVehicleId = MutableStateFlow<Int?>(null)
    val drivenVehicleId: StateFlow<Int?> = _drivenVehicleId.asStateFlow()

    // House buy/state updates triggers
    private val _ownedHouseIds = MutableStateFlow<Set<String>>(emptySet())
    val ownedHouseIds: StateFlow<Set<String>> = _ownedHouseIds.asStateFlow()

    // Active screen announcement banner
    private val _missionText = MutableStateFlow("Grand Theft Adil: Steal the Sports Car parked outside AL MAHA HALL (Bottom-Left Corner)!")
    val missionText: StateFlow<String> = _missionText.asStateFlow()

    private val _showWastedScreen = MutableStateFlow(false)
    val showWastedScreen: StateFlow<Boolean> = _showWastedScreen.asStateFlow()

    // Continuous Joystick Control Inputs for smooth mobile experience
    private val _joystickX = MutableStateFlow(0f)
    val joystickX: StateFlow<Float> = _joystickX.asStateFlow()

    private val _joystickY = MutableStateFlow(0f)
    val joystickY: StateFlow<Float> = _joystickY.asStateFlow()

    fun updateJoystick(x: Float, y: Float) {
        _joystickX.value = x
        _joystickY.value = y
    }

    // Static map dimensions
    val mapSize = 1200f

    // Dynamic Lists for the Canvas Game Loop
    val buildings = mutableStateListOf<GameBuilding>()
    val vehicles = mutableStateListOf<GameVehicle>()
    val npcs = mutableStateListOf<GameNpc>()
    val projectiles = mutableStateListOf<GameProjectile>()
    val moneyDrops = mutableStateListOf<GameMoneyDrop>()
    val houses = mutableStateListOf<GameHouse>()

    // Local loop controller
    private var isGameLoopActive = false
    private var nextEntityId = 1
    var isMissionCompleted = false

    init {
        generateMapLayout()
        observeStoredState()
    }

    private fun observeStoredState() {
        viewModelScope.launch {
            repository.playerState.collect { saved ->
                if (saved != null) {
                    _money.value = saved.money
                    _health.value = saved.health
                    _armor.value = saved.armor
                    _respect.value = saved.respect
                    _playerX.value = saved.posX
                    _playerY.value = saved.posY
                    _playerAngle.value = saved.posAngle
                    _currentWeapon.value = saved.weapon
                    
                    val hostSet = saved.ownedHouses.split(",")
                        .filter { it.isNotEmpty() }
                        .toSet()
                    _ownedHouseIds.value = hostSet
                    
                    houses.forEach { h ->
                        h.isOwned = hostSet.contains(h.id)
                    }
                }
            }
        }
    }

    private fun saveCurrentState() {
        viewModelScope.launch(Dispatchers.IO) {
            val ownedStr = _ownedHouseIds.value.joinToString(",")
            val currentEnt = PlayerStateEntity(
                id = 1,
                money = _money.value,
                health = _health.value,
                armor = _armor.value,
                ownedHouses = ownedStr,
                respect = _respect.value,
                posX = _playerX.value,
                posY = _playerY.value,
                posAngle = _playerAngle.value,
                weapon = _currentWeapon.value
            )
            repository.saveState(currentEnt)
        }
    }

    // Dynamic Procedural GTA 3 / SA Styled Map Grid (focalizer layout)
    private fun generateMapLayout() {
        buildings.clear()
        houses.clear()

        // Setup Houses for Purchasable Properties representing GTA blocks mapped to Google Maps layout
        houses.addAll(
            listOf(
                GameHouse("apartment_1", "Adil's Safehouse Apartment", 0, 1020f, 180f, true, "Starting safehouse residential unit. The target destination of the first mission."),
                GameHouse("house_cheap", "Santa Maria Shorehouse", 600, 180f, 540f, false, "Waterfront boardwalk unit with custom garage space."),
                GameHouse("house_mansion", "Madd Adil's Vinewood Crib", 3000, 540f, 1020f, false, "Spacious Hollywood luxury mansion on top slopes."),
                GameHouse("house_pent", "San Fierro Penthouse Office", 1500, 1020f, 900f, false, "Sleek helipad condo overlooking industrial docks.")
            )
        )

        // Special Detailed Cartoon Landmarks from Amman map layout
        // 1. Al Maha Hall (Bottom-Left Corner): r = 10
        buildings.add(GameBuilding(100f, 1040f, 65f, 65f, 16f, 0xFFE91E63, r = 10, isApartment = false))

        // 2. Al Quds Bakery (Left side, next to highway): r = 11
        buildings.add(GameBuilding(100f, 560f, 50f, 50f, 12f, 0xFFFFB74D, r = 11, isApartment = false))

        // 3. Tansal Turkish Restaurant (Top-Left/Center): r = 12
        buildings.add(GameBuilding(320f, 200f, 55f, 55f, 13f, 0xFFD32F2F, r = 12, isApartment = false))

        // 4. Family Market (Middle-Center of neighborhood): r = 13
        buildings.add(GameBuilding(500f, 560f, 60f, 50f, 15f, 0xFF2196F3, r = 13, isApartment = false))

        // 5. Masjid Al Johny (Bottom-Center/Right): r = 14
        buildings.add(GameBuilding(800f, 920f, 70f, 70f, 22f, 0xFF00C853, r = 14, isApartment = false))

        // 6. Adil's Safehouse Apartment (Top-Right): r = 15
        buildings.add(GameBuilding(1000f, 140f, 55f, 55f, 24f, 0xFF8C5D3A, r = 15, isApartment = true))

        // Building colors: classic nostalgic GTA SA warm stucco, concrete orange, dark red-brick, slate skyscrapers
        val buildingPalette = listOf(
            0xFFCCB38C, // Stucco beige
            0xFF8D6E63, // Soft brown
            0xFF546E7A, // Steel grey-blue
            0xFF7C8570, // Moss concrete
            0xFFB07D62, // Terracotta orange
            0xFF37474F  // Slate glass
        )

        val roadSpacing = 240f // Road grid intervals of 240 units
        val halfRoadWidth = 25f

        // Procedural generation of filler buildings around the grid layout
        for (gridX in 0..10) {
            for (gridY in 0..10) {
                val worldX = gridX * 120f
                val worldZ = gridY * 120f

                // Avoid placing buildings directly on road lanes
                val onXRoad = (worldX % roadSpacing) < halfRoadWidth * 2
                val onYRoad = (worldZ % roadSpacing) < halfRoadWidth * 2

                if (onXRoad || onYRoad) {
                    continue
                }

                // Skip grids containing our unique cartoon landmarks
                if (gridX in 0..2 && gridY in 8..10) continue // Al Maha Hall
                if (gridX in 0..2 && gridY in 4..5) continue  // Al Quds Bakery
                if (gridX in 2..3 && gridY in 1..2) continue  // Tansal Turkish Restaurant
                if (gridX in 4..5 && gridY in 4..5) continue  // Family Market
                if (gridX in 6..7 && gridY in 7..8) continue  // Masjid Al Johny
                if (gridX in 8..10 && gridY in 0..2) continue // Adil's Safehouse / Playground

                // Skip neighborhood parks (green zones)
                val isPark = (gridX in 4..5 && gridY in 1..2)
                if (isPark) {
                    continue
                }

                val bWidth = 40f + (gridX * 2L % 15)
                val bDepth = 40f + (gridY * 3L % 15)
                val bHeight = 14f + (gridX % 3) * 12f + (gridY % 4) * 6f
                val colorHex = buildingPalette[(gridX + gridY) % buildingPalette.size]

                buildings.add(
                    GameBuilding(
                        x = worldX + 15f,
                        z = worldZ + 15f,
                        width = bWidth,
                        depth = bDepth,
                        height = bHeight,
                        colorHex = colorHex,
                        r = (gridX + gridY) % 3
                    )
                )
            }
        }
    }

    fun startLoadingAnimation() {
        _currentScreen.value = GameScreen.LOADING
        _loadingProgress.value = 0f
        viewModelScope.launch {
            GameAudio.playIntroBeats()
            // Stylized loading slide delays
            for (step in 1..40) {
                delay(60)
                _loadingProgress.value = step / 40f
            }
            _currentScreen.value = GameScreen.GAMEPLAY
            startGameLoop()
        }
    }

    fun changeScreen(screen: GameScreen) {
        _currentScreen.value = screen
        if (screen == GameScreen.GAMEPLAY) {
            startGameLoop()
        } else {
            isGameLoopActive = false
        }
    }

    private fun startGameLoop() {
        if (isGameLoopActive) return
        isGameLoopActive = true

        // Spawn some starting vehicles
        if (vehicles.isEmpty()) {
            spawnVehicles()
        }

        // Spawn some pedestrian NPCs
        if (npcs.isEmpty()) {
            spawnNpcs()
        }

        viewModelScope.launch {
            var loopCount = 0
            while (isGameLoopActive) {
                tickPhysics()
                loopCount++
                if (loopCount % 60 == 0) {
                    // Auto-sync game state to database every ~1 second
                    saveCurrentState()
                }
                delay(16) // ~60 Frames Per Second active updates
            }
        }
    }

    private fun spawnVehicles() {
        vehicles.clear()
        // Spawn 8 client cars distributed geographically around road nodes
        val colors = listOf(0xFFE53935, 0xFFFFD54F, 0xFF0D47A1, 0xFF1B5E20, 0xFF212121, 0xFF8E24AA)
        
        // Target 1: A vehicle placed close to the start corner to enable Adil's car theft (near Al Maha Hall)
        vehicles.add(
            GameVehicle(
                id = nextEntityId++,
                x = 160f,
                y = 1020f,
                angle = 0f,
                speed = 0f,
                colorHex = 0xFFD32F2F, // Vibrant red Sports Sabre
                modelType = 1 // Sport
            )
        )

        // Rest of procedurally driven traffic cars on axes
        val coords = listOf(
            Pair(40f, 600f), Pair(240f, 400f), Pair(480f, 800f),
            Pair(720f, 300f), Pair(960f, 900f), Pair(960f, 200f)
        )

        coords.forEachIndexed { i, pt ->
            vehicles.add(
                GameVehicle(
                    id = nextEntityId++,
                    x = pt.first,
                    y = pt.second,
                    angle = (i * 1.57f),
                    speed = 0.8f + (i % 3) * 0.4f,
                    colorHex = colors[i % colors.size],
                    modelType = i % 3
                )
            )
        }
    }

    private fun spawnNpcs() {
        npcs.clear()
        val types = listOf("citizen", "gangster", "citizen", "gangster", "citizen", "cop")
        
        // Spawn 15 pedestrians roaming blocks
        for (i in 0..15) {
            val angle = (Math.random() * 6.28).toFloat()
            val npcX = 80f + (i * 70f) % 1000f
            val npcY = 80f + (i * 50f) % 1000f
            npcs.add(
                GameNpc(
                    id = nextEntityId++,
                    x = npcX,
                    y = npcY,
                    angle = angle,
                    speed = 0.5f,
                    type = types[i % types.size]
                )
            )
        }
    }

    private fun tickPhysics() {
        val px = _playerX.value
        val py = _playerY.value
        val pAngle = _playerAngle.value
        val carId = _drivenVehicleId.value

        val jx = _joystickX.value
        val jy = _joystickY.value

        // 1. If player is driving, lock player's position matching the active vehicle
        if (carId != null) {
            val v = vehicles.find { it.id == carId }
            if (v != null) {
                // Continuous Joystick steering/driving controls
                if (jx.coerceAtAbsolute() > 0.15f) {
                    val steerMultiplier = if (v.speed < 0f) -1f else 1f
                    _playerAngle.value = (pAngle + jx * steerMultiplier * 0.045f) % 6.28318f
                }

                if (jy < -0.15f) {
                    // Accelerate Gas
                    v.speed = (v.speed + 0.12f).coerceAtMost(if (v.modelType == 1) 7.0f else 4.4f)
                } else if (jy > 0.15f) {
                    // Brake / Reverse Gas
                    v.speed = (v.speed - 0.15f).coerceAtLeast(-2.2f)
                } else {
                    // Rolling friction drag
                    v.speed *= 0.96f
                }

                // Read inputs of driver
                v.angle = _playerAngle.value
                // Move vehicle based on its internal rolling speed
                v.x += cos(v.angle) * v.speed
                v.y += sin(v.angle) * v.speed

                // Keep bounds inside mapping limits
                if (v.x < 10f) { v.x = 10f; v.speed = -v.speed * 0.3f }
                if (v.x > mapSize - 10f) { v.x = mapSize - 10f; v.speed = -v.speed * 0.3f }
                if (v.y < 10f) { v.y = 10f; v.speed = -v.speed * 0.3f }
                if (v.y > mapSize - 10f) { v.y = mapSize - 10f; v.speed = -v.speed * 0.3f }

                // Check collisions against static heavy building walls
                buildings.forEach { b ->
                    if (isInsideBuilding(v.x, v.y, b)) {
                        // Dampen speed immediately of collision impact
                        v.speed = -v.speed * 0.4f
                        // Push out of obstacle bounds
                        v.x -= cos(v.angle) * 8f
                        v.y -= sin(v.angle) * 8f
                    }
                }

                _playerX.value = v.x
                _playerY.value = v.y

                // Driving Story objective: drive to player's safehouse apartment (green marker at 1020, 180)
                if (!isMissionCompleted) {
                    val distToApt = distance(v.x, v.y, 1020f, 180f)
                    if (distToApt < 25f && v.speed.coerceAtAbsolute() < 0.2f) {
                        isMissionCompleted = true
                        _money.value += 250
                        _respect.value += 15
                        _missionText.value = "MISSION COMPLETED: Respect +15! Earned $250! Feel free to explore."
                        GameAudio.playCashEarned()
                    }
                }
            }
        } else {
            // Player is walking: Continuous Joystick walking controls
            if (jx.coerceAtAbsolute() > 0.15f) {
                // Steer heading angle
                _playerAngle.value = (pAngle + jx * 0.055f) % 6.28318f
            }

            if (jy.coerceAtAbsolute() > 0.15f) {
                // Walk speed proportional to drag
                val speedVal = 1.9f * -jy
                _playerX.value += cos(pAngle) * speedVal
                _playerY.value += sin(pAngle) * speedVal
            }

            // Keep bounds inside mapping limits
            _playerX.value = _playerX.value.coerceIn(10f, mapSize - 10f)
            _playerY.value = _playerY.value.coerceIn(10f, mapSize - 10f)

            // Check collisions of player on foot against static building wall
            buildings.forEach { b ->
                if (isInsideBuilding(_playerX.value, _playerY.value, b)) {
                    // Push out
                    val pushAngle = pAngle + 3.1415f
                    _playerX.value += cos(pushAngle) * 3f
                    _playerY.value += sin(pushAngle) * 3f
                }
            }
        }

        // 2. Traffic cars movement calculations
        vehicles.forEach { v ->
            if (!v.isPlayerDriven) {
                // Move slowly along straight vectors
                v.x += cos(v.angle) * v.speed
                v.y += sin(v.angle) * v.speed

                // Periodic turning patterns at road nodes limit
                if (Math.random() < 0.015) {
                    v.angle += if (Math.random() > 0.5) 1.57f else -1.57f
                }

                // Relocate if out of bounds limits
                if (v.x < 5f || v.x > mapSize - 5f || v.y < 5f || v.y > mapSize - 5f) {
                    v.x = 200f + (Math.random() * 800f).toFloat()
                    v.y = 200f + (Math.random() * 800f).toFloat()
                    v.speed = 1.0f
                }
            }
        }

        // 3. Projectiles physics
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val proj = iterator.next()
            proj.x += proj.vx
            proj.y += proj.vy
            proj.decay--

            // Check hit against npcs
            npcs.forEach { npc ->
                if (!npc.isDead && distance(proj.x, proj.y, npc.x, npc.y) < 8f) {
                    npc.health -= if (proj.isRpg) 100 else 40
                    proj.decay = 0 // terminate projectile
                    if (npc.health <= 0) {
                        npc.isDead = true
                        _money.value += 120
                        _respect.value += 5
                        _wantedLevel.value = (_wantedLevel.value + 1).coerceAtMost(5)
                        moneyDrops.add(GameMoneyDrop(nextEntityId++, npc.x, npc.y, (40..150).random()))
                        GameAudio.playHitNpc()
                    }
                }
            }

            // Hit cars check
            vehicles.forEach { car ->
                if (distance(proj.x, proj.y, car.x, car.y) < 12f) {
                    car.health -= if (proj.isRpg) 80 else 25
                    proj.decay = 0
                    if (car.health <= 0) {
                        // Blow up vehicle
                        car.speed = 0f
                        _money.value += 200
                        _wantedLevel.value = (_wantedLevel.value + 1).coerceAtMost(5)
                    }
                }
            }

            // Decay handling
            if (proj.decay <= 0) {
                projectiles.remove(proj)
                break
            }
        }

        // 4. Roaming Pedestrians movement and behavior
        npcs.forEach { npc ->
            if (!npc.isDead) {
                npc.actionTimer++
                // Hostile cop/gangster targeting
                if ((npc.type == "cop" && _wantedLevel.value > 0) || (npc.type == "gangster" && distance(npc.x, npc.y, px, py) < 80f)) {
                    // Chase player
                    val dx = px - npc.x
                    val dy = py - npc.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist > 1.5f) {
                        npc.angle = kotlin.math.atan2(dy, dx)
                        npc.x += cos(npc.angle) * 1.1f
                        npc.y += sin(npc.angle) * 1.1f
                    } else if (npc.actionTimer % 45 == 0) {
                        // Attack Player "Punch/stab"
                        deductPlayerHealth(15)
                        GameAudio.playTone(150f, 120, "saw", 0.4f)
                    }
                } else {
                    // Wander randomly
                    if (npc.actionTimer > 120) {
                        npc.angle = (Math.random() * 6.28).toFloat()
                        npc.actionTimer = 0
                    }
                    npc.x += cos(npc.angle) * npc.speed
                    npc.y += sin(npc.angle) * npc.speed
                }

                // Check bounds of map
                if (npc.x < 5f) npc.x = 5f
                if (npc.x > mapSize - 5f) npc.x = mapSize - 5f
                if (npc.y < 5f) npc.y = 5f
                if (npc.y > mapSize - 5f) npc.y = mapSize - 5f

                // Avoid crashing through building walls
                buildings.forEach { b ->
                    if (isInsideBuilding(npc.x, npc.y, b)) {
                        npc.angle += 3.14f // Turn away right back
                        npc.x += cos(npc.angle) * 4f
                        npc.y += sin(npc.angle) * 4f
                    }
                }
            }
        }

        // 5. Check if player ran over NPCs while driving vehicle fast
        if (carId != null) {
            val v = vehicles.find { it.id == carId }
            if (v != null && v.speed.coerceAtAbsolute() > 1.0f) {
                npcs.forEach { npc ->
                    if (!npc.isDead && distance(v.x, v.y, npc.x, npc.y) < 14f) {
                        npc.isDead = true
                        npc.health = 0
                        _money.value += 100 // players earn money by killing people
                        _respect.value += 3
                        _wantedLevel.value = (_wantedLevel.value + 1).coerceAtMost(5)
                        moneyDrops.add(GameMoneyDrop(nextEntityId++, npc.x, npc.y, (100..250).random()))
                        GameAudio.playTone(80f, 250, "noise", 0.4f)
                    }
                }
            }
        }

        // 6. Check cash pick up colliders
        val moneyIterator = moneyDrops.iterator()
        while (moneyIterator.hasNext()) {
            val drop = moneyIterator.next()
            if (distance(px, py, drop.x, drop.y) < 15f) {
                _money.value += drop.amount
                GameAudio.playCashEarned()
                moneyDrops.remove(drop)
                break
            }
        }
    }

    private fun deductPlayerHealth(amount: Int) {
        if (_health.value <= 0) return
        if (_armor.value > 0) {
            _armor.value = (_armor.value - amount).coerceAtLeast(0)
        } else {
            _health.value = (_health.value - amount).coerceAtLeast(0)
        }

        if (_health.value <= 0) {
            // Player is WAStED Screen
            _showWastedScreen.value = true
            isGameLoopActive = false
            GameAudio.playTone(60f, 600, "saw", 0.5f)
            viewModelScope.launch {
                delay(3000)
                respawnAtApt()
            }
        }
    }

    private fun respawnAtApt() {
        // Hospital tax
        _money.value = (_money.value - 100).coerceAtLeast(0)
        _playerX.value = 1020f
        _playerY.value = 180f
        _playerAngle.value = 0f
        _health.value = 100
        _armor.value = 0
        _drivenVehicleId.value = null
        _showWastedScreen.value = false
        _wantedLevel.value = 0
        startGameLoop()
    }

    // Helper math functions
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun isInsideBuilding(x: Float, y: Float, b: GameBuilding): Boolean {
        return x >= b.x && x <= b.x + b.width && y >= b.z && y <= b.z + b.depth
    }

    // Controls inputs called from HUD Buttons
    fun movePlayer(forward: Boolean, sidestep: Boolean = false, left: Boolean = false) {
        val speedVal = if (_drivenVehicleId.value != null) 0.18f else 1.8f
        val angle = _playerAngle.value

        if (forward) {
            if (_drivenVehicleId.value != null) {
                // Accelerate vehicle driven
                val v = vehicles.find { it.id == _drivenVehicleId.value }
                if (v != null) {
                    v.speed = (v.speed + 0.15f).coerceAtMost(if (v.modelType == 1) 7f else 4.5f)
                }
            } else {
                // Walk forward
                _playerX.value += cos(angle) * speedVal
                _playerY.value += sin(angle) * speedVal
            }
        } else {
            // Reverse/Brake
            if (_drivenVehicleId.value != null) {
                val v = vehicles.find { it.id == _drivenVehicleId.value }
                if (v != null) {
                    v.speed = (v.speed - 0.2f).coerceAtLeast(-2f)
                }
            } else {
                // Walk backward
                _playerX.value -= cos(angle) * speedVal
                _playerY.value -= sin(angle) * speedVal
            }
        }

        // Clamp inside map limits
        _playerX.value = _playerX.value.coerceIn(10f, mapSize - 10f)
        _playerY.value = _playerY.value.coerceIn(10f, mapSize - 10f)

        // Collide against walls
        buildings.forEach { b ->
            if (isInsideBuilding(_playerX.value, _playerY.value, b)) {
                // Push back to safe spots
                if (forward) {
                    _playerX.value -= cos(angle) * 3f
                    _playerY.value -= sin(angle) * 3f
                } else {
                    _playerX.value += cos(angle) * 3f
                    _playerY.value += sin(angle) * 3f
                }
            }
        }
    }

    fun turnPlayer(left: Boolean) {
        val turnRate = if (_drivenVehicleId.value != null) 0.05f else 0.08f
        if (left) {
            _playerAngle.value = (_playerAngle.value - turnRate) % 6.28f
        } else {
            _playerAngle.value = (_playerAngle.value + turnRate) % 6.28f
        }
    }

    // Hijack closest vehicle
    fun hijackVehicle() {
        val px = _playerX.value
        val py = _playerY.value

        if (_drivenVehicleId.value != null) {
            // Exit vehicle
            val exitId = _drivenVehicleId.value
            _drivenVehicleId.value = null
            vehicles.find { it.id == exitId }?.let {
                it.isPlayerDriven = false
                it.speed = 0f
                // Eject slightly next to it
                _playerX.value += sin(it.angle) * 12f
                _playerY.value -= cos(it.angle) * 12f
            }
            GameAudio.playHijackSound()
            return
        }

        // Find closest within hijack range (25f)
        val closest = vehicles.filter { !it.isPlayerDriven }
            .minByOrNull { distance(px, py, it.x, it.y) }

        if (closest != null && distance(px, py, closest.x, closest.y) < 32f) {
            _drivenVehicleId.value = closest.id
            closest.isPlayerDriven = true
            closest.speed = 0.5f
            _playerX.value = closest.x
            _playerY.value = closest.y
            _playerAngle.value = closest.angle
            GameAudio.playHijackSound()

            // Story kickoff trigger
            if (!isMissionCompleted && _missionText.value.contains("Grand Theft Adil")) {
                _missionText.value = "KICKASS CAR OWNED! Drive to Adil's Apartment in the Green Beacon (South-East edge of city!)"
            }
        }
    }

    // Shoot weapon
    fun selectNextWeapon() {
        val current = _currentWeapon.value
        _currentWeapon.value = when (current) {
            "FISTS" -> "PISTOL"
            "PISTOL" -> "TEC9"
            "TEC9" -> "RPG"
            else -> "FISTS"
        }
        GameAudio.playTone(800f, 100, "sine", 0.1f)
    }

    fun fireWeapon() {
        val px = _playerX.value
        val py = _playerY.value
        val angle = _playerAngle.value
        val weapon = _currentWeapon.value

        if (weapon == "FISTS") {
            // Punch range
            GameAudio.playTone(150f, 100, "saw", 0.3f)
            // Hit closest npc
            val closestNpc = npcs.find { !it.isDead && distance(px, py, it.x, it.y) < 18f }
            if (closestNpc != null) {
                closestNpc.health -= 35
                if (closestNpc.type == "citizen") {
                    closestNpc.type = "gangster" // Turn angry gangster
                }
                if (closestNpc.health <= 0) {
                    closestNpc.isDead = true
                    _money.value += 50
                    moneyDrops.add(GameMoneyDrop(nextEntityId++, closestNpc.x, closestNpc.y, (10..50).random()))
                }
                GameAudio.playHitNpc()
            }
            return
        }

        // Gun projectile spawn
        GameAudio.playGunshot()
        val vx = cos(angle) * (if (weapon == "RPG") 12f else 22f)
        val vy = sin(angle) * (if (weapon == "RPG") 12f else 22f)

        projectiles.add(
            GameProjectile(
                x = px + cos(angle) * 10f,
                y = py + sin(angle) * 10f,
                vx = vx,
                vy = vy,
                isRpg = (weapon == "RPG")
            )
        )
    }

    // Buy close property
    fun tryBuyClosestHouse() {
        val px = _playerX.value
        val py = _playerY.value
        val wallet = _money.value

        val closest = houses.find { !it.isOwned && distance(px, py, it.x, it.y) < 30f }
        if (closest != null) {
            if (wallet >= closest.price) {
                _money.value -= closest.price
                closest.isOwned = true
                val newSet = _ownedHouseIds.value.toMutableSet()
                newSet.add(closest.id)
                _ownedHouseIds.value = newSet
                _respect.value += 40
                _missionText.value = "PURCHASED: ${closest.name}! Respect has skyrocketed!"
                GameAudio.playCashEarned()
                saveCurrentState()
            } else {
                _missionText.value = "NOT ENOUGH CASH! Need $${closest.price}!"
                GameAudio.playTone(180f, 300, "square", 0.25f)
            }
        }
    }

    // Reset game completely
    fun resetGame() {
        viewModelScope.launch {
            repository.resetState()
            _money.value = 250
            _health.value = 100
            _armor.value = 0
            _respect.value = 10
            _playerX.value = 120f
            _playerY.value = 120f
            _playerAngle.value = 0f
            _currentWeapon.value = "FISTS"
            _ownedHouseIds.value = emptySet()
            isMissionCompleted = false
            _missionText.value = "Grand Theft Adil: Hijack a car to get started!"
            _wantedLevel.value = 0
            _drivenVehicleId.value = null
            houses.forEach { it.isOwned = (it.id == "apartment_1") }
            spawnVehicles()
            spawnNpcs()
            saveCurrentState()
        }
    }

    fun floatAbsolute(f: Float): Float = if (f < 0f) -f else f
}

// Custom simple absolute extension check to avoid AGP configuration blocks
private fun Float.coerceAtAbsolute(): Float = if (this < 0) -this else this
