package com.karthi.voicefinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karthi.voicefinder.power.PauseReason
import com.karthi.voicefinder.power.PowerRules
import com.karthi.voicefinder.service.FinderSettings
import com.karthi.voicefinder.voice.MatchResult
import kotlin.math.roundToInt
import com.karthi.voicefinder.voice.VoiceProfile

data class ReliabilityState(
    val micGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val dndAccess: Boolean = false,
    val fullScreenAllowed: Boolean = false,
)

data class FinderUiState(
    val enrollment: EnrollmentState,
    val reliability: ReliabilityState,
    val listening: Boolean,
    val micBlocked: Boolean,
    val micLevel: Float,
    val recordLevel: Float,
    val sensitivity: Float,
    val lastMatch: MatchResult?,
    val soundName: String?,
    val repeatCount: Int,
    val previewing: Boolean,
    val pauseReason: PauseReason?,
    val powerRules: PowerRules,
    val banner: BannerState,
)

class FinderActions(
    val grantPermissions: () -> Unit,
    val recordSample: () -> Unit,
    val saveProfile: () -> Unit,
    val recordWrongPhrase: () -> Unit,
    val clearWrongPhrases: () -> Unit,
    val resetProfile: () -> Unit,
    val setSensitivity: (Float) -> Unit,
    val setListening: (Boolean) -> Unit,
    val resumeListening: () -> Unit,
    val simulateTrigger: () -> Unit,
    val chooseSound: () -> Unit,
    val resetSound: () -> Unit,
    val togglePreview: () -> Unit,
    val changeRepeat: (Int) -> Unit,
    val setPauseOnLowBattery: (Boolean) -> Unit,
    val setLowBatteryPercent: (Int) -> Unit,
    val setPauseWhileCharging: (Boolean) -> Unit,
    val setBannerTitle: (String) -> Unit,
    val setBannerMessage: (String) -> Unit,
    val setBannerTheme: (Int) -> Unit,
    val resetBanner: () -> Unit,
    val openBatterySettings: () -> Unit,
    val openDndSettings: () -> Unit,
    val openFullScreenSettings: () -> Unit,
    val openAppSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(phrase: String, state: FinderUiState, actions: FinderActions, snackbar: SnackbarHostState) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { CenterAlignedTopAppBar(title = { Text("Voice Finder", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusHero(phrase, state, actions) }
            item { SetupChecklist(state, actions) }
            item { VoiceCard(phrase, state, actions) }
            item { SoundCard(state, actions) }
            item { BannerCard(state.banner, actions) }
            item { BatteryCard(state.powerRules, actions) }
            item { DetectionCard(state, actions) }
            item { AboutCard() }
        }
    }
}

@Composable
private fun StatusHero(phrase: String, state: FinderUiState, actions: FinderActions) {
    val ready = state.enrollment.profileSaved && state.reliability.micGranted
    val paused = state.listening && state.pauseReason != null
    val active = state.listening && !state.micBlocked && !paused
    val (title, subtitle) = when {
        paused && state.pauseReason == PauseReason.LOW_BATTERY ->
            "Paused: battery low" to "Resumes by itself above ${state.powerRules.lowBatteryPercent + 3}% or when plugged in"
        paused -> "Paused while charging" to "Resumes by itself when you unplug"
        state.micBlocked -> "Android muted the mic" to "Tap the mic to resume listening"
        state.listening -> "Listening for your voice" to "Say “$phrase”"
        !state.enrollment.profileSaved -> "Set up your voice" to "Record your wake phrase below"
        else -> "Not listening" to "Tap the mic to start"
    }
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "heroColor",
    )
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = container)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PulsingMicButton(
                active = active,
                enabled = ready,
                level = state.micLevel,
                onClick = { if (state.micBlocked) actions.resumeListening() else actions.setListening(!state.listening) },
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (state.listening && state.enrollment.profileSaved && !state.micBlocked) {
                if (active) LevelMeter(state.micLevel, "Live mic level: speak to see it move")
                OutlinedButton(onClick = actions.simulateTrigger) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Test the alert")
                }
            }
        }
    }
}

@Composable
private fun PulsingMicButton(active: Boolean, enabled: Boolean, level: Float, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse",
    )
    val levelScale by animateFloatAsState(1f + level * 0.35f, label = "level")
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier.size(128.dp).scale(maxOf(pulse, levelScale)).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            )
        }
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(112.dp), shape = CircleShape) {
            Icon(
                if (active) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                contentDescription = if (active) "Stop listening" else "Start listening",
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun LevelMeter(level: Float, label: String) {
    val animated by animateFloatAsState(level.coerceIn(0f, 1f), label = "meter")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
    }
}

private class Step(val label: String, val done: Boolean, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@Composable
private fun SetupChecklist(state: FinderUiState, actions: FinderActions) {
    val r = state.reliability
    val listenAction: (() -> Unit)? = when {
        !state.enrollment.profileSaved -> null
        state.micBlocked -> actions.resumeListening
        else -> { { actions.setListening(true) } }
    }
    val steps = listOf(
        Step("Microphone & notifications", r.micGranted && r.notificationsGranted, "Grant", actions.grantPermissions),
        Step("Voice profile recorded", state.enrollment.profileSaved),
        Step("Listening in background", state.listening && !state.micBlocked, if (state.micBlocked) "Resume" else "Start", listenAction),
        Step("Unrestricted battery", r.batteryUnrestricted, "Allow", actions.openBatterySettings),
        Step("STOP button on lock screen", r.fullScreenAllowed, "Allow", actions.openFullScreenSettings),
        Step("Ring even on Do Not Disturb", r.dndAccess, "Allow", actions.openDndSettings),
    )
    val done = steps.count { it.done }
    val progress by animateFloatAsState(done.toFloat() / steps.size, label = "setup")
    SectionCard(Icons.Rounded.Settings, "Setup", "$done/${steps.size}") {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        steps.forEach { step ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (step.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (step.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(12.dp))
                Text(step.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                val action = step.action
                if (!step.done && action != null && step.actionLabel != null) {
                    FilledTonalButton(onClick = action) { Text(step.actionLabel) }
                }
            }
        }
        Text(
            "Xiaomi, Oppo, Vivo, Samsung: also turn on Autostart and lock Voice Finder in Recents.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = actions.openAppSettings) { Text("Open app settings") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceCard(phrase: String, state: FinderUiState, actions: FinderActions) {
    val e = state.enrollment
    SectionCard(Icons.Rounded.RecordVoiceOver, "Your voice", "${e.samples}/${VoiceProfile.MAX_SAMPLES}") {
        Text(
            "Say the whole phrase “$phrase” ${VoiceProfile.MIN_SAMPLES}–${VoiceProfile.MAX_SAMPLES} times in a quiet room, " +
                "at your normal volume and speed. Only the full phrase will trigger it.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(VoiceProfile.MAX_SAMPLES) { i ->
                val color by animateColorAsState(
                    if (i < e.samples) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    label = "dot",
                )
                Box(Modifier.size(22.dp).clip(CircleShape).background(color))
            }
        }
        AnimatedVisibility(e.recording) { LevelMeter(state.recordLevel, "Listening… speak now") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = actions.recordSample,
                enabled = state.reliability.micGranted && !e.recording && e.samples < VoiceProfile.MAX_SAMPLES,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (e.recording) "Recording…" else "Record sample")
            }
            FilledTonalButton(onClick = actions.saveProfile, enabled = e.samples >= VoiceProfile.MIN_SAMPLES && !e.recording) {
                Text("Save profile")
            }
            TextButton(onClick = actions.resetProfile) { Text("Reset") }
        }
        if (e.profileSaved) Text("✓ Voice profile saved", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        if (e.profileSaved) {
            HorizontalDivider()
            Text("Phrases that should NOT trigger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "If another sentence sets it off (like “ஏய் என்ன பண்ற”), record it here, or tap “Wrong phrase” on the " +
                    "alert screen. It will be rejected from then on. Saved: ${e.wrongPhrases}/${VoiceProfile.MAX_NEGATIVES}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.recordWrongPhrase, enabled = !e.recording) {
                    Icon(Icons.Rounded.Block, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record a wrong phrase")
                }
                if (e.wrongPhrases > 0) TextButton(onClick = actions.clearWrongPhrases) { Text("Clear") }
            }
        }
        e.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundCard(state: FinderUiState, actions: FinderActions) {
    SectionCard(Icons.Rounded.MusicNote, "Response sound") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.soundName ?: "“நீங்க எங்க தூக்கி போட்டீங்களோ…”",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.soundName == null) "Built-in default" else "Your custom file",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = actions.togglePreview) {
                Icon(
                    if (state.previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.previewing) "Stop preview" else "Preview sound",
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.chooseSound) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose MP3")
            }
            if (state.soundName != null) {
                OutlinedButton(onClick = actions.resetSound) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use default")
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plays when found", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { actions.changeRepeat(-1) }, enabled = state.repeatCount > FinderSettings.MIN_REPEAT) {
                Icon(Icons.Rounded.Remove, contentDescription = "Fewer plays")
            }
            Text("${state.repeatCount}×", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { actions.changeRepeat(1) }, enabled = state.repeatCount < FinderSettings.MAX_REPEAT) {
                Icon(Icons.Rounded.Add, contentDescription = "More plays")
            }
        }
        Text(
            "Full volume even on silent, then it stops by itself. A STOP button appears on screen, even when locked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BannerCard(banner: BannerState, actions: FinderActions) {
    SectionCard(Icons.Rounded.Palette, "Found screen banner") {
        val theme = BannerThemes.get(banner.theme)
        // Live preview of what appears over the lock screen.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(theme.brush).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    banner.title.ifBlank { FinderSettings.DEFAULT_BANNER_TITLE },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    banner.message.ifBlank { FinderSettings.DEFAULT_BANNER_MESSAGE },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) { Text("STOP", color = theme.top, fontWeight = FontWeight.Black) }
            }
        }
        OutlinedTextField(
            value = banner.title,
            onValueChange = actions.setBannerTitle,
            label = { Text("Title") },
            singleLine = true,
            supportingText = { Text("${banner.title.length}/${FinderSettings.MAX_TITLE_LENGTH}") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = banner.message,
            onValueChange = actions.setBannerMessage,
            label = { Text("Message") },
            maxLines = 3,
            supportingText = { Text("${banner.message.length}/${FinderSettings.MAX_MESSAGE_LENGTH}") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BannerThemes.all.forEachIndexed { index, option ->
                val selected = index == banner.theme
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(option.brush)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { actions.setBannerTheme(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = option.name, tint = Color.White)
                    }
                }
            }
        }
        TextButton(onClick = actions.resetBanner) { Text("Reset to default") }
    }
}

@Composable
private fun BatteryCard(rules: PowerRules, actions: FinderActions) {
    SectionCard(Icons.Rounded.BatterySaver, "Battery saver") {
        SwitchRow(
            title = "Pause when battery is low",
            subtitle = "At or below ${rules.lowBatteryPercent}%. Resumes above ${rules.lowBatteryPercent + 3}% or when plugged in.",
            checked = rules.pauseOnLowBattery,
            onChange = actions.setPauseOnLowBattery,
        )
        AnimatedVisibility(rules.pauseOnLowBattery) {
            Column {
                Text("Pause at ${rules.lowBatteryPercent}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = rules.lowBatteryPercent.toFloat(),
                    onValueChange = { actions.setLowBatteryPercent(it.roundToInt()) },
                    valueRange = FinderSettings.MIN_LOW_PERCENT.toFloat()..FinderSettings.MAX_LOW_PERCENT.toFloat(),
                    steps = (FinderSettings.MAX_LOW_PERCENT - FinderSettings.MIN_LOW_PERCENT) / 5 - 1,
                )
            }
        }
        HorizontalDivider()
        SwitchRow(
            title = "Pause while charging",
            subtitle = "Resumes when you unplug.",
            checked = rules.pauseWhileCharging,
            onChange = actions.setPauseWhileCharging,
        )
        Text(
            "While paused the mic is fully off, so the phone can't be found by voice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DetectionCard(state: FinderUiState, actions: FinderActions) {
    SectionCard(Icons.Rounded.Tune, "Detection") {
        Text("Sensitivity", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.sensitivity,
            onValueChange = actions.setSensitivity,
            valueRange = FinderSettings.MIN_SENSITIVITY..FinderSettings.MAX_SENSITIVITY,
        )
        Row {
            Text("Strict", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("Relaxed", style = MaterialTheme.typography.labelSmall)
        }
        val match = state.lastMatch
        if (match == null) {
            Text(
                "While listening, speak near the phone to see live match scores here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Last thing heard", style = MaterialTheme.typography.labelLarge)
            ScoreBar("Phrase", match.phraseScore)
            ScoreBar("Voice", match.voiceScore)
            Text(
                when {
                    match.accepted -> "✓ Matched: this would trigger"
                    !match.complete -> "✗ Incomplete: say all three words “ஏய் எங்க இருக்க”"
                    match.markedWrong -> "✗ Sounds like a phrase you marked as wrong"
                    else -> "✗ Not a match. Too different? Move the slider right."
                },
                color = if (match.accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Score ≤ 1 passes; shown as closeness so a fuller bar means a better match (the halfway mark is the limit). */
@Composable
private fun ScoreBar(label: String, score: Float) {
    val closeness by animateFloatAsState(((2f - score) / 2f).coerceIn(0f, 1f), label = "score")
    val passes = score <= 1f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { closeness },
            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (passes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text("%.2f".format(score), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AboutCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Why is the mic icon always on?", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                Text(
                    "“OK Google” runs on a special low-power chip that Android opens only to the built-in assistant. " +
                        "Other apps must keep the normal microphone open, and Android always shows the mic indicator " +
                        "while one does. Voice Finder analyses audio only on the phone and only reacts to your phrase " +
                        "in your voice. Nothing is recorded or sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            content()
        }
    }
}
