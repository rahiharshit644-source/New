package com.soltini.app.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.ui.theme.AccentBlue
import com.soltini.app.ui.theme.DarkBorder
import com.soltini.app.ui.theme.DarkSurface
import com.soltini.app.ui.theme.RedError
import com.soltini.app.ui.theme.TextPrimary
import com.soltini.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * BrowserOverlayPanel
 *
 * Collapsible real-time companion view showing the active Android WebView session.
 * Gives the user full visibility into what Myra is observing and clicking,
 * along with an immediate "Stop" button.
 */
@Composable
fun BrowserOverlayPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val browserController = remember { BrowserController.getInstance(context) }
    val scope = rememberCoroutineScope()

    val isVisible by browserController.isPanelVisible.collectAsStateWithLifecycle()
    val isExpanded by browserController.isPanelExpanded.collectAsStateWithLifecycle()
    val currentUrl by browserController.currentUrl.collectAsStateWithLifecycle()
    val pageTitle by browserController.pageTitle.collectAsStateWithLifecycle()
    val isLoading by browserController.isLoading.collectAsStateWithLifecycle()
    val statusMessage by browserController.statusMessage.collectAsStateWithLifecycle()


    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { /* prevent background clicks */ },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (isExpanded) 0.92f else 0.6f)
                    .border(width = 1.dp, color = DarkBorder, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .testTag("browser_overlay_panel")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = AccentBlue
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Browser Agent",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (pageTitle.isNotBlank()) pageTitle else "Myra In-App Browser",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (statusMessage.isNotBlank()) statusMessage else currentUrl,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // STOP Button
                        Button(
                            onClick = {
                                browserController.stop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedError),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("browser_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Stop",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Expand / Collapse Button
                        IconButton(
                            onClick = { browserController.togglePanelExpanded() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = { browserController.closePanel() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = AccentBlue,
                            trackColor = DarkBorder
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(DarkBorder)
                        )
                    }

                    // Live Android WebView display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.White)
                    ) {
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
