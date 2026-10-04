package com.karthi.voicefinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karthi.voicefinder.service.FinderSettings
import com.karthi.voicefinder.voice.MatchResult
import com.karthi.voicefinder.voice.VoiceProfile

data class ReliabilityState(
    val micGranted: Boolean,
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
    val dndAccess: Boolean,
)

class FinderActions(
    val grantPermissions: () -> Unit,
    val recordSample: () -> Unit,
    val saveProfile: () -> Unit,
    val resetProfile: () -> Unit,
    val setSensitivity: (Float) -> Unit,
    val setListening: (Boolean) -> Unit,
    val testResponse: () -> Unit,
    val openBatterySettings: () -> Unit,
    val openDndSettings: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun FinderScreen(
    phrase: String,
    enrollment: EnrollmentState,
    reliability: ReliabilityState,
    listening: Boolean,
    sensitivity: Float,
    lastMatch: MatchResult?,
    actions: FinderActions,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Voice Finder", style = MaterialTheme.typography.headlineMedium)
        Text("Wake phrase: $phrase", style = MaterialTheme.typography.titleMedium)

        if (!reliability.micGranted || !reliability.notificationsGranted) {
            Section("1 · Permissions") {
                Text("Microphone and notification access are required.")
                Button(actions.grantPermissions) { Text("Grant permissions") }
            }
        }

        Section("2 · Enroll your voice") {
            Text("Record the phrase ${VoiceProfile.MIN_SAMPLES}–${VoiceProfile.MAX_SAMPLES} times in a quiet room, at your normal volume.")
            Text("Samples: ${enrollment.samples}" + if (enrollment.profileSaved) "  ·  profile saved ✓" else "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = actions.recordSample,
                    enabled = reliability.micGranted && !enrollment.recording && enrollment.samples < VoiceProfile.MAX_SAMPLES,
                ) { Text("Record sample") }
                if (enrollment.recording) CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(actions.saveProfile, enabled = enrollment.samples >= VoiceProfile.MIN_SAMPLES && !enrollment.recording) {
                    Text("Save profile")
                }
                OutlinedButton(actions.resetProfile) { Text("Reset") }
            }
            enrollment.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        Section("3 · Listening") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Listen in background", Modifier.weight(1f))
                Switch(
                    checked = listening,
                    onCheckedChange = actions.setListening,
                    enabled = enrollment.profileSaved && reliability.micGranted,
                )
            }
            Text("Sensitivity: ${"%.2f".format(sensitivity)}  (higher = triggers more easily)")
            Slider(
                value = sensitivity,
                onValueChange = actions.setSensitivity,
                valueRange = FinderSettings.MIN_SENSITIVITY..FinderSettings.MAX_SENSITIVITY,
            )
            lastMatch?.let {
                Text(
                    "Last heard → phrase ${"%.2f".format(it.phraseScore)}, voice ${"%.2f".format(it.voiceScore)} " +
                        "(≤ 1.00 to trigger) ${if (it.accepted) "✓ matched" else "✗"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(actions.testResponse) { Text("Test response sound") }
        }

        Section("4 · Keep it alive") {
            SettingRow("Unrestricted battery", reliability.batteryUnrestricted, actions.openBatterySettings)
            SettingRow("Do Not Disturb override", reliability.dndAccess, actions.openDndSettings)
            Text("On Xiaomi/Oppo/Vivo/Samsung also enable Autostart and lock the app in Recents.")
            OutlinedButton(actions.openAppSettings) { Text("Open app settings") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${if (ok) "✓" else "✗"}  $label", Modifier.weight(1f))
        if (!ok) OutlinedButton(onFix) { Text("Fix") }
    }
}
