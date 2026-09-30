package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.live.SessionState
import com.example.ui.components.AudioWaveformBar
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.HolographicOrb
import com.example.ui.components.QuickPromptChips
import com.example.ui.components.ToolExecutionHud
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HolographicViolet
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.viewmodel.MahiViewModel

@Composable
fun MahiScreen(
    viewModel: MahiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }

    // Request audio record permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMicPermissionGranted(isGranted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onMicPermissionGranted(hasPermission)
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Dynamic background gradient
    val backgroundBrush = Brush.radialGradient(
        colors = listOf(
            when (uiState.sessionState) {
                SessionState.SPEAKING -> NeonMagenta.copy(alpha = 0.16f)
                SessionState.LISTENING -> ElectricCyan.copy(alpha = 0.14f)
                SessionState.CONNECTING -> HolographicViolet.copy(alpha = 0.15f)
                else -> Color(0xFF13091B)
            },
            CyberDarkBg,
            Color(0xFF04060A)
        ),
        radius = 1600f
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar
            TopBarSection(
                sessionState = uiState.sessionState,
                mood = uiState.mood,
                onDiagnosticsClick = { showDiagnostics = true },
                onAvatarClick = { viewModel.triggerQuickPrompt(uiState.quickPrompts.first()) }
            )

            // Tool Execution HUD Alert
            ToolExecutionHud(
                toolEvent = uiState.activeToolEvent,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Center: Interactive Holographic Orb & State Visualization
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Orb
                val effectiveAmp = if (uiState.sessionState == SessionState.SPEAKING) {
                    uiState.outputAmplitude
                } else {
                    uiState.inputAmplitude
                }

                HolographicOrb(
                    sessionState = uiState.sessionState,
                    amplitude = effectiveAmp,
                    onClick = {
                        if (!uiState.isMicPermissionGranted) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleConnection()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Real-time Sound Waveform Bar
                AudioWaveformBar(
                    amplitude = effectiveAmp,
                    sessionState = uiState.sessionState
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Status text with pulsating hint
                StatusTextSection(
                    sessionState = uiState.sessionState,
                    errorMessage = uiState.errorMessage
                )

                // Subtitle / Spoken transcript snippet bubble
                AnimatedVisibility(
                    visible = uiState.transcriptSnippet.isNotBlank(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CyberSurface.copy(alpha = 0.85f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = uiState.transcriptSnippet,
                            color = Color(0xFFF1F5F9),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }
            }

            // Bottom Section: Sassy Quick Prompts & Primary Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Voice Prompts Carousel
                QuickPromptChips(
                    prompts = uiState.quickPrompts,
                    onPromptSelected = { prompt ->
                        viewModel.triggerQuickPrompt(prompt)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Control Buttons
                BottomControls(
                    sessionState = uiState.sessionState,
                    isMuted = uiState.isMuted,
                    onToggleConnection = {
                        if (!uiState.isMicPermissionGranted) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleConnection()
                        }
                    },
                    onInterrupt = { viewModel.interrupt() },
                    onToggleMute = { viewModel.toggleMute() }
                )
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsSheet(
            selectedVoice = uiState.voiceName,
            selectedModel = uiState.modelName,
            sessionState = uiState.sessionState.name,
            isMicGranted = uiState.isMicPermissionGranted,
            onVoiceSelected = { voice ->
                viewModel.setVoice(voice)
            },
            onDismiss = { showDiagnostics = false }
        )
    }
}

@Composable
private fun TopBarSection(
    sessionState: SessionState,
    mood: String,
    onDiagnosticsClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.clickable { onAvatarClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(2.dp, NeonMagenta, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mahi_avatar),
                    contentDescription = "Mahi AI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MAHI AI",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )

                    // Live beacon
                    val beaconColor by animateColorAsState(
                        targetValue = when (sessionState) {
                            SessionState.SPEAKING -> NeonMagenta
                            SessionState.LISTENING -> NeonGreen
                            SessionState.CONNECTING -> ElectricCyan
                            else -> Color(0xFF64748B)
                        },
                        label = "BeaconColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(beaconColor)
                    )
                }

                Text(
                    text = mood,
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Settings / Diagnostics button
        IconButton(
            onClick = onDiagnosticsClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CyberSurfaceVariant.copy(alpha = 0.7f))
                .border(1.dp, Color(0x3300F0FF), CircleShape)
                .testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = ElectricCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusTextSection(
    sessionState: SessionState,
    errorMessage: String?
) {
    val statusText = when (sessionState) {
        SessionState.DISCONNECTED -> "Tap orb to wake Mahi"
        SessionState.CONNECTING -> "Establishing neural link..."
        SessionState.LISTENING -> "Mahi is listening... (Speak freely)"
        SessionState.SPEAKING -> "Mahi is speaking..."
        SessionState.ERROR -> errorMessage ?: "Connection error. Tap to retry."
    }

    val statusColor = when (sessionState) {
        SessionState.SPEAKING -> NeonMagenta
        SessionState.LISTENING -> ElectricCyan
        SessionState.CONNECTING -> HolographicViolet
        SessionState.ERROR -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

    Text(
        text = statusText,
        color = statusColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun BottomControls(
    sessionState: SessionState,
    isMuted: Boolean,
    onToggleConnection: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute / Unmute Button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(CyberSurfaceVariant)
                .border(1.dp, Color(0x4400F0FF), CircleShape)
                .testTag("mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) Color(0xFFEF4444) else ElectricCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        // Central Power / Wake Button
        val isConnected = sessionState != SessionState.DISCONNECTED && sessionState != SessionState.ERROR
        val powerBtnColor = if (isConnected) NeonMagenta else ElectricCyan

        Button(
            onClick = onToggleConnection,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = powerBtnColor
            ),
            modifier = Modifier
                .size(72.dp)
                .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .testTag("power_button")
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                contentDescription = if (isConnected) "Disconnect" else "Connect",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // Interruption Button (Active when Mahi is speaking)
        val canInterrupt = sessionState == SessionState.SPEAKING
        IconButton(
            onClick = {
                if (canInterrupt) onInterrupt()
            },
            enabled = canInterrupt,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (canInterrupt) NeonMagenta.copy(alpha = 0.25f) else CyberSurfaceVariant.copy(alpha = 0.4f))
                .border(
                    1.dp,
                    if (canInterrupt) NeonMagenta else Color(0x22FFFFFF),
                    CircleShape
                )
                .testTag("interrupt_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Interrupt",
                tint = if (canInterrupt) NeonMagenta else Color(0xFF64748B),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
