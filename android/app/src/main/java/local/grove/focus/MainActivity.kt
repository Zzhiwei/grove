package local.grove.focus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlinx.coroutines.delay
import local.grove.focus.core.Mode
import local.grove.focus.core.StatRange
import local.grove.focus.core.formatDuration
import local.grove.focus.core.formatTotal
import local.grove.focus.core.stepMinutes
import local.grove.focus.core.totals

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0) // timer still works if denied
        }
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)) // light status bar icons on the dark forest background
        setContent { GroveTheme { GroveScreen(store, this) } }
    }

    override fun onStart() {
        super.onStart()
        cloudSync.schedule() // sync when the app comes to the front

    }
}

@Composable
fun GroveScreen(store: Store, ctx: Context) {
    var now by remember { mutableStateOf(Instant.now()) }
    // The tick only refreshes `now` and checks pomodoro completion; time itself comes from dates.
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            store.completeIfDue(now)?.let { TimerNotifications.celebrate(ctx, it, store.data.timer.minutes) }
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L) // periodic sync while the app is open
            ctx.cloudSync.schedule()
        }
    }
    val data = store.data
    val timer = data.timer
    fun act(action: (Instant) -> Unit) = Instant.now().let { now = it; action(it) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(ForestNight, Pine)))
            .safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🍃 Grove", color = Fern, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Segmented(Mode.entries, timer.mode, { it.label }, enabled = !timer.isActive, onSelect = store::setMode)
        }

        Ring(progress = timer.progress(now), time = formatDuration(timer.display(now)), tag = timer.tag)

        if (timer.mode == Mode.POMODORO && !timer.isActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ store.setMinutes(stepMinutes(timer.minutes, up = false)) },
                    Modifier.semantics { contentDescription = "Shorter" }) { Text("−", fontSize = 22.sp) }
                Text("${timer.minutes} min", color = Mist, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                TextButton({ store.setMinutes(stepMinutes(timer.minutes, up = true)) },
                    Modifier.semantics { contentDescription = "Longer" }) { Text("+", fontSize = 22.sp) }
            }
        }

        TagRow(store, data.tags, timer.tag, enabled = !timer.isActive)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ act(if (timer.isRunning) store::pause else store::start) }, Modifier.weight(1f)) {
                Text(if (timer.isRunning) "Pause" else if (timer.isActive) "Resume" else "Start")
            }
            OutlinedButton({ act { store.stop(it) } }, Modifier.weight(1f), enabled = timer.isActive) {
                Text("Stop & Save")
            }
        }

        HorizontalDivider(color = Moss.copy(alpha = 0.4f))
        Stats(data.sessions.let { sessions -> { range: StatRange -> totals(sessions, range.start(now)) } })

        HorizontalDivider(color = Moss.copy(alpha = 0.4f))
        Account(ctx.cloud, ctx.cloudSync)
    }
}

@Composable
private fun Account(cloud: Cloud, sync: CloudSync) {
    cloud.status.collectAsState().value // recompose on sign-in / sign-out / expiry
    var open by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ open = !open }, Modifier.fillMaxWidth()) {
            Text(
                if (cloud.signedIn) "☁ ${cloud.email ?: "Signed in"}" else "☁ Sync to cloud",
                color = Mist, modifier = Modifier.weight(1f),
            )
            if (sync.syncing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(if (open) "  ▲" else "  ▼", color = Mist)
        }
        if (sync.status.isNotEmpty()) Text(sync.status, color = Fern.copy(alpha = 0.8f), fontSize = 12.sp)
        if (!open) return@Column
        if (cloud.signedIn) {
            Row {
                OutlinedButton({ sync.schedule() }, enabled = !sync.syncing) { Text("Sync Now") }
                Spacer(Modifier.weight(1f))
                TextButton({ sync.signOut() }) { Text("Sign Out") }
            }
        } else {
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            val ready = email.isNotBlank() && password.length >= 6 && !sync.syncing
            fun submit(create: Boolean) = sync.signIn(email.trim(), password, create).invokeOnCompletion {
                if (cloud.signedIn) password = ""
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ submit(create = false) }, enabled = ready) { Text("Sign In") }
                OutlinedButton({ submit(create = true) }, enabled = ready) { Text("Create Account") }
            }
        }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected, onClick = { onSelect(option) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size), icon = {},
            ) { Text(label(option), fontSize = 12.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Ring(progress: Double, time: String, tag: String) {
    Box(Modifier.size(220.dp).semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 12.dp.toPx()
            val inset = Offset(w / 2, w / 2)
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(Bark.copy(alpha = 0.35f), 0f, 360f, false, inset, arcSize, style = Stroke(w))
            drawArc(Fern, -90f, 360f * progress.toFloat(), false, inset, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The tree grows as the session progresses.
            Text(if (progress < 0.34) "🌱" else if (progress < 0.67) "🌿" else "🌳", fontSize = (28 + 20 * progress).sp)
            Text(time, color = Mist, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(tag, color = Fern.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun TagRow(store: Store, tags: List<String>, selected: String, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    var newTag by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton({ open = true }, Modifier.fillMaxWidth(), enabled = enabled) {
                Text("🏷 $selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(open, { open = false }) {
                for (t in tags) {
                    DropdownMenuItem(
                        text = { Text(t) },
                        onClick = { store.selectTag(t); open = false },
                        trailingIcon = if (tags.size > 1) {
                            { TextButton({ store.removeTag(t) }, Modifier.semantics { contentDescription = "Remove $t" }) { Text("✕") } }
                        } else null,
                    )
                }
            }
        }
        OutlinedTextField(
            newTag, { newTag = it }, Modifier.weight(1f), enabled = enabled, singleLine = true,
            placeholder = { Text("New tag") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { store.addTag(newTag); newTag = "" }),
        )
    }
}

@Composable
private fun Stats(totalsFor: (StatRange) -> List<Pair<String, Double>>) {
    var range by rememberSaveable { mutableStateOf(StatRange.TODAY) }
    val rows = totalsFor(range)
    val max = rows.firstOrNull()?.second ?: 1.0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Focus by tag", color = Mist, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Segmented(StatRange.entries, range, { it.label }) { range = it }
        }
        if (rows.isEmpty()) Text("No focus time yet. Plant a seed.", color = Mist.copy(alpha = 0.6f), fontSize = 13.sp)
        for ((tag, seconds) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tag, color = Mist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(90.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxWidth(maxOf(0.02, seconds / max).toFloat()).fillMaxHeight().background(Moss, CircleShape))
                }
                Text(formatTotal(seconds), color = Mist, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
        }
        Text("Total ${formatTotal(rows.sumOf { it.second })}", color = Fern.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}
