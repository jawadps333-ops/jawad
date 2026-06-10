package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val screen by viewModel.currentScreen.collectAsState()
                val isWasted by viewModel.showWastedScreen.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (screen) {
                            GameScreen.SPLASH -> {
                                // Direct loading kickoff
                                viewModel.startLoadingAnimation()
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                            }
                            GameScreen.LOADING -> {
                                GameSplashScreen(
                                    viewModel = viewModel,
                                    onProgressEnd = { viewModel.changeScreen(GameScreen.MAIN_MENU) }
                                )
                            }
                            GameScreen.MAIN_MENU -> {
                                GameMainMenuScreen(
                                    viewModel = viewModel,
                                    onStartClick = { viewModel.changeScreen(GameScreen.GAMEPLAY) },
                                    onStatsClick = { viewModel.changeScreen(GameScreen.STATS) }
                                )
                            }
                            GameScreen.STATS -> {
                                GameStatsScreen(
                                    viewModel = viewModel,
                                    onBackClick = { viewModel.changeScreen(GameScreen.MAIN_MENU) }
                                )
                            }
                            GameScreen.GAMEPLAY -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // 1. 3D projection rendering Canvas game
                                    Game3DCanvas(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // 2. Heads up dashboard overlay
                                    RetroHUDOverlay(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                                    )

                                    // 3. Virtual controls gamepad layout
                                    VirtualGamepadOverlay(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                                    )

                                    // Quick Menu Escape Overlay floating button
                                    Button(
                                        onClick = { viewModel.changeScreen(GameScreen.MAIN_MENU) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(top = 135.dp, start = 16.dp)
                                    ) {
                                        Text("MENU", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Wasted Cinematic Overlay
                        AnimatedVisibility(
                            visible = isWasted,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.78f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "WASTED",
                                        color = Color(0xFFD32F2F),
                                        fontSize = 62.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Serif,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 4.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "RESPAWNING AT CITY APARTMENT SAFE HOUSE... HOSPITAL TAX (-$100)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

