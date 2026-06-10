package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.audio.GameAudio

@Composable
fun GameSplashScreen(
    viewModel: GameViewModel,
    onProgressEnd: () -> Unit
) {
    val progress by viewModel.loadingProgress.collectAsState()

    // Bouncing fade animations for retro splash screen load
    val scaleAnim = rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("game_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Comic poster of Adil generated
        Image(
            painter = painterResource(id = R.drawable.gta_adil_art),
            contentDescription = "GTA Adil Comic Art Backdrop",
            modifier = Modifier
                .fillMaxSize()
                .scale(scaleAnim.value),
            contentScale = ContentScale.Crop,
            alpha = 0.55f
        )

        // Vignette gradient shade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter)
        ) {
            // Retro Styled LOGO: "ADIL"
            Text(
                text = "ADIL",
                color = Color.White,
                fontSize = 78.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                lineHeight = 72.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "SAN ANDREAS RETRO STYLE ACTION",
                color = Color(0xFFFF9800),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 44.dp)
            )

            // Dynamic GTA styling progress indicator loading gauge
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(20.dp)
                    .border(2.dp, Color.White, shape = RoundedCornerShape(2.dp))
                    .background(Color.DarkGray.copy(alpha = 0.5f), shape = RoundedCornerShape(2.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LOADING GRAPHICAL ASSETS... ${(progress * 100).toInt()}%",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun GameMainMenuScreen(
    viewModel: GameViewModel,
    onStartClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("game_main_menu"),
        contentAlignment = Alignment.Center
    ) {
        // Comic poster backdrop representation
        Image(
            painter = painterResource(id = R.drawable.gta_adil_art),
            contentDescription = "GTA Adil Backdrop Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        // Radial shadow vignetting overlay screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Retro Logo Game Title
            Text(
                text = "ADIL",
                color = Color.White,
                fontSize = 90.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 85.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "ACTION DRIVING SHOOTER 3D",
                color = Color(0xFFFFB74D),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 48.dp)
            )

            // Menu choices button list (GTA styled overlay selection)
            Column(
                modifier = Modifier.width(260.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MenuButton(
                    text = "START GAME",
                    tag = "btn_start_game",
                    onClick = {
                        GameAudio.playIntroBeats()
                        onStartClick()
                    }
                )

                MenuButton(
                    text = "ACQUIRED HOUSES & STATS",
                    tag = "btn_view_stats",
                    onClick = {
                        GameAudio.playTone(400f, 150)
                        onStatsClick()
                    }
                )

                // Option to reset progress state completely
                MenuButton(
                    text = "RESET STORY & PROGRESS",
                    tag = "btn_reset_storage",
                    color = Color(0xFFD32F2F),
                    onClick = {
                        viewModel.resetGame()
                        GameAudio.playTone(180f, 300, "square")
                    }
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Character specs info tag
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), shape = RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "CHAR PROFILE: Adil is an average-tall black male featuring a distinctive full beard, low taper fade haircut, and black glasses. Complete mission objectives to unlock city safehouses.",
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun MenuButton(
    text: String,
    tag: String,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color == Color.White) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(2.dp, Color.Black, shape = RoundedCornerShape(4.dp))
            .testTag(tag)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GameStatsScreen(
    viewModel: GameViewModel,
    onBackClick: () -> Unit
) {
    val ownedSet by viewModel.ownedHouseIds.collectAsState()
    val money by viewModel.money.collectAsState()
    val respect by viewModel.respect.collectAsState()
    val initialHousesList = viewModel.houses

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414)) // Cinematic pure dark
            .testTag("game_stats_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "GAME REVIEWS & STATS",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "PLAYER ID: ADIL (SAN ANDREAS SECTOR)",
                color = Color.Gray,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Wallet and Respect Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TOTAL CASH", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("$${money}", color = Color(0xFF4CAF50), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("RESPECT RATING", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${respect} PTS", color = Color(0xFFFFD54F), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ACQUIRABLE REAL ESTATE DEEDS (${ownedSet.size} / 4 OWNED)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // List of houses in city
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(initialHousesList) { h ->
                    val owned = ownedSet.contains(h.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222222), shape = RoundedCornerShape(8.dp))
                            .border(1.dp, if (owned) Color.Green else Color.DarkGray, shape = RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = h.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = h.desc,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (h.price == 0) "FREE" else "$${h.price}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (owned) "OWNED" else "FOR SALE",
                                color = if (owned) Color.Green else Color.Yellow,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Back button return to menu
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_back_to_menu")
            ) {
                Text("RETURN TO MAIN MENU", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
