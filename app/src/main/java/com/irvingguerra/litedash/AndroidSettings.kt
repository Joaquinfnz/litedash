package com.irvingguerra.litedash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irvingguerra.litedash.core.ui.Button

/**
 * Accesos a los Ajustes nativos de Android. En radios FYT el menú de la radio
 * reemplaza al de Android y esconde "Acerca del dispositivo" y "Opciones de
 * desarrollador": se abre el componente de com.android.settings directo y, si
 * no existe, la acción estándar.
 */
private fun open(context: Context, action: String, component: String?): Boolean {
    val tries = buildList {
        if (component != null) add(Intent().setComponent(ComponentName("com.android.settings", component)))
        add(Intent(action))
    }
    for (i in tries) {
        if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
    }
    Toast.makeText(context, R.string.android_settings_missing, Toast.LENGTH_LONG).show()
    return false
}

@Composable
fun AndroidSettingsButtons() {
    val context = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(stringResource(R.string.android_settings), { open(context, Settings.ACTION_SETTINGS, "com.android.settings.Settings") })
        Button(stringResource(R.string.android_about), { open(context, Settings.ACTION_DEVICE_INFO_SETTINGS, "com.android.settings.Settings\$DeviceInfoSettingsActivity") })
        Button(stringResource(R.string.android_developer), { open(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "com.android.settings.Settings\$DevelopmentSettingsActivity") })
    }
}
