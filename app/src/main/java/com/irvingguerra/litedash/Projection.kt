package com.irvingguerra.litedash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Card
import com.irvingguerra.litedash.core.ui.Icon
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.feature.apps.App

// Apps conocidas para usar el celular en la pantalla (Android Auto / CarPlay con o sin cable).
private val PROJECTION_PACKAGES = listOf(
    "com.zjinnova.zlink", "cn.manstep.phonemirrorBox", "net.easyconn.carman", "com.suding.speedplay",
    "com.google.android.projection.gearhead",
)

// Si la pantalla trae la suya con otro paquete, se reconoce por el nombre.
private val PROJECTION_WORDS = listOf("carplay", "android auto", "androidauto", "zlink", "autokit", "carlink", "easyconn", "tlink", "phonelink", "carbit")

/** La app de Android Auto / CarPlay que tenga la pantalla, o null. */
fun projectionApp(apps: List<App>): App? =
    PROJECTION_PACKAGES.firstNotNullOfOrNull { p -> apps.firstOrNull { it.pkg == p } }
        ?: apps.firstOrNull { a ->
            val name = a.name.lowercase()
            val pkg = a.pkg.lowercase()
            PROJECTION_WORDS.any { w -> w in name || w.replace(" ", "") in pkg }
        }

/** Pieza Android Auto / CarPlay: abre la app de proyección; si no hay, el cajón para buscarla. */
@Composable
fun ProjectionTile(d: Dependencies, apps: List<App>, onMissing: () -> Unit) {
    val context = LocalContext.current
    val app = projectionApp(apps)
    val c = LocalColors.current
    // CarPlay es lo primero que se busca al subir al auto: la única pieza en color de rally.
    Card(Modifier.fillMaxSize(), onClick = { if (app != null) d.apps.open(context, app) else onMissing() }, accent = true) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.onAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.phoneCar, c.onAccent, 28.dp)
            }
            Column(Modifier.weight(1f)) {
                Label(stringResource(R.string.carplay_open), Typography.title, c.onAccent, maxLines = 1)
                Label(app?.name ?: stringResource(R.string.projection_missing), Typography.small, c.onAccent.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}
