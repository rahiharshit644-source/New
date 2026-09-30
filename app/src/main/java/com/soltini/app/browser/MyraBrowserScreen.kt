package com.soltini.app.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.imePadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.ui.theme.AccentBlue
import com.soltini.app.ui.theme.AccentTeal
import com.soltini.app.ui.theme.DarkBorder
import com.soltini.app.ui.theme.DarkSurface
import com.soltini.app.ui.theme.DarkSurfaceVariant
import com.soltini.app.ui.theme.RedError
import com.soltini.app.ui.theme.TextPrimary
import com.soltini.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class QuickShortcut(val name: String, val url: String, val iconLabel: String)

/**
 * MyraBrowserScreen
 *
 * Full-fledged standalone web browser screen built directly into Myra.
 * Eliminates the need for Google Chrome while giving users and Myra AI
 * 100% full-powered, unrestricted on-device browser automation.
 */
@Composable
fun MyraBrowserScreen(
    onOpenVoiceAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val browserController = remember { BrowserController.getInstance(context) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentUrl by browserController.currentUrl.collectAsStateWithLifecycle()
    val pageTitle by browserController.pageTitle.collectAsStateWithLifecycle()
    val isLoading by browserController.isLoading.collectAsStateWithLifecycle()
    val statusMessage by browserController.statusMessage.collectAsStateWithLifecycle()
    val canGoBack by browserController.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by browserController.canGoForward.collectAsStateWithLifecycle()
    val isDesktopMode by browserController.isDesktopMode.collectAsStateWithLifecycle()

    var urlInputText by remember { mutableStateOf("") }
    var showVaultDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            urlInputText = currentUrl
        }
    }

    BackHandler(enabled = canGoBack) {
        browserController.goBackManual()
    }

    val quickShortcuts = remember {
        listOf(
            QuickShortcut("Google", "https://www.google.com", "G"),
            QuickShortcut("YouTube", "https://www.youtube.com", "YT"),
            QuickShortcut("GitHub", "https://www.github.com", "GH"),
            QuickShortcut("Wikipedia", "https://www.wikipedia.org", "W"),
            QuickShortcut("Amazon", "https://www.amazon.in", "Amz"),
            QuickShortcut("Reddit", "https://www.reddit.com", "R"),
            QuickShortcut("Twitter / X", "https://twitter.com", "X"),
            QuickShortcut("ChatGPT", "https://chatgpt.com", "AI")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(DarkSurface)
    ) {
        // ── Top Navigation & Omnibox Bar ─────────────────────────────────────
        Surface(
            color = DarkSurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = DarkBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Back
                    IconButton(
                        onClick = { browserController.goBackManual() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { browserController.goForwardManual() },
                        enabled = canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reload / Stop
                    IconButton(
                        onClick = {
                            if (isLoading) browserController.stop() else browserController.reload()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isLoading) "Stop Loading" else "Reload",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Omnibox Text Field
                    OutlinedTextField(
                        value = urlInputText,
                        onValueChange = { urlInputText = it },
                        placeholder = {
                            Text("Search Google or enter URL...", fontSize = 12.sp, color = TextSecondary)
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                                contentDescription = "Security",
                                tint = if (currentUrl.startsWith("https")) AccentTeal else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (urlInputText.isNotBlank()) {
                                IconButton(
                                    onClick = { urlInputText = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                browserController.navigateUserUrl(urlInputText)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("browser_omnibox")
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Home Button
                    IconButton(
                        onClick = {
                            urlInputText = ""
                            browserController.navigateUserUrl("about:blank")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Desktop Mode Toggle
                    IconButton(
                        onClick = { browserController.toggleDesktopMode() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isDesktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                            contentDescription = "Desktop Site",
                            tint = if (isDesktopMode) AccentTeal else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Password Vault Button
                    IconButton(
                        onClick = { showVaultDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Password Vault",
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // AI Status Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = AccentBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "AI Agent",
                                tint = AccentBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (statusMessage.isNotBlank()) statusMessage else "Myra In-App Browser Ready",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                browserController.snapshot()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Analyze Page", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = AccentBlue,
                    trackColor = DarkBorder
                )
            }
        }

        // ── Main Viewport ────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val isBlank = currentUrl.isBlank() || currentUrl == "about:blank"

            // 1. Live Android WebView
            AndroidView(
                factory = { ctx ->
                    // ALWAYS use the controller's WebView (never create a dummy one here).
                    FrameLayout(ctx).apply {
                        val wv = browserController.obtainWebView()
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        browserController.attachContext(ctx)
                        addView(
                            wv,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        wv.onResume()
                    }
                },
                onRelease = { container ->
                    container.removeAllViews()
                    browserController.detachContext()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )

            // 2. Start / Home Dashboard (Shown when on blank page)
            if (isBlank) {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(30.dp))

                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AccentBlue.copy(alpha = 0.2f))
                                    .border(1.dp, AccentBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Myra Browser",
                                    tint = AccentBlue,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Myra Autonomous Browser",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Your all-in-one AI powered web browser with full autonomous control.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Quick Search Box
                            OutlinedTextField(
                                value = urlInputText,
                                onValueChange = { urlInputText = it },
                                placeholder = { Text("Search anything with Google...", fontSize = 13.sp, color = TextSecondary) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AccentBlue) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        browserController.navigateUserUrl(urlInputText)
                                    }
                                ),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurfaceVariant,
                                    unfocusedContainerColor = DarkSurfaceVariant,
                                    focusedBorderColor = AccentBlue,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = "Quick Shortcuts",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Shortcuts Grid
                        items(quickShortcuts.chunked(4)) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (sc in rowItems) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                browserController.navigateUserUrl(sc.url)
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(DarkSurfaceVariant)
                                                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = sc.iconLabel,
                                                color = AccentBlue,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = sc.name,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))

                            // AI Capabilities Banner
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.fillMaxWidth().border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bolne par Myra kya kar sakti hai?", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("• Kisi bhi website par login aur password fill karna", color = TextSecondary, fontSize = 12.sp)
                                    Text("• Account / ID create karna aur registration form bharna", color = TextSecondary, fontSize = 12.sp)
                                    Text("• Dropdowns select karna aur checkboxes tick karna", color = TextSecondary, fontSize = 12.sp)
                                    Text("• Search karke products, news ya tickets book karna", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Credential Vault Dialog ──────────────────────────────────────────────
    if (showVaultDialog) {
        val creds = remember { browserController.getAllCredentials() }
        AlertDialog(
            onDismissRequest = { showVaultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = AccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Myra Password & ID Vault", fontSize = 16.sp, color = TextPrimary)
                }
            },
            text = {
                if (creds.isEmpty()) {
                    Text("No saved login credentials yet. Ask Myra: 'Is site ke credentials save kar lo' to store logins.", color = TextSecondary, fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(creds.entries.toList()) { (domain, obj) ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(domain, color = AccentTeal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("User: " + obj.optString("username"), color = TextPrimary, fontSize = 12.sp)
                                        Text("Pass: ••••••••", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            browserController.deleteCredential(domain)
                                            showVaultDialog = false
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVaultDialog = false }) {
                    Text("Close", color = AccentBlue)
                }
            },
            containerColor = DarkSurface
        )
    }
}
