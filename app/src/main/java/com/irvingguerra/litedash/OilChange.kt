package com.irvingguerra.litedash

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Icons
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography
import java.text.NumberFormat

/**
 * Cambio de aceite por kilómetros. El kilometraje lo escribe el usuario (no hay
 * odómetro en la pantalla); nada corre en segundo plano: se revisa al ver el inicio.
 */
class OilRepository(context: Context) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** Kilometraje actual; -1 = sin escribir. */
    var odometer by mutableIntStateOf(store.getInt(KEY_ODOMETER, -1))
        private set
    /** Kilometraje del último cambio; -1 = sin escribir. */
    var lastChange by mutableIntStateOf(store.getInt(KEY_LAST, -1))
        private set
    var interval by mutableIntStateOf(store.getInt(KEY_INTERVAL, DEFAULT_INTERVAL))
        private set

    val configured get() = odometer >= 0 && lastChange >= 0 && interval > 0
    val nextAt get() = lastChange + interval
    /** Negativo = atrasado. */
    val remaining get() = nextAt - odometer

    /** Avisa en el inicio cuando faltan [WARN_KM] km o menos. */
    fun due() = configured && remaining <= WARN_KM

    fun update(odometer: Int = this.odometer, lastChange: Int = this.lastChange, interval: Int = this.interval) {
        this.odometer = odometer
        this.lastChange = lastChange
        this.interval = interval
        store.edit {
            putInt(KEY_ODOMETER, odometer)
            putInt(KEY_LAST, lastChange)
            putInt(KEY_INTERVAL, interval)
        }
    }

    /** Hecho hoy: el último cambio pasa a ser el kilometraje actual. */
    fun changedNow() {
        if (odometer >= 0) update(lastChange = odometer)
    }

    companion object {
        private const val STORE = "aceite"
        private const val KEY_ODOMETER = "km_actual"
        private const val KEY_LAST = "km_ultimo_cambio"
        private const val KEY_INTERVAL = "cada_km"
        const val DEFAULT_INTERVAL = 10_000
        const val WARN_KM = 1_000
    }
}

@Composable
private fun km(value: Int): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(value)

@Composable
private fun remainingText(repo: OilRepository): String {
    val r = repo.remaining
    return if (r >= 0) stringResource(R.string.oil_remaining, km(r)) else stringResource(R.string.oil_overdue, km(-r))
}

/** Aviso en el inicio cuando se acerca o pasó el cambio; "Hecho" lo registra en el km actual. */
@Composable
fun OilBanner(repo: OilRepository) {
    if (!repo.due()) return
    Notice("${stringResource(R.string.oil_change)} · ${remainingText(repo)}", Icons.bell) {
        Button(stringResource(R.string.reminder_done), { repo.changedNow() }, primary = true)
    }
}

/** Ajustes → Tu auto: kilometraje actual, del último cambio y cada cuánto. */
@Composable
fun OilSettings(repo: OilRepository) {
    val c = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KmField(stringResource(R.string.oil_odometer), repo.odometer) { repo.update(odometer = it) }
        KmField(stringResource(R.string.oil_last_change), repo.lastChange) { repo.update(lastChange = it) }
        KmField(stringResource(R.string.oil_interval), repo.interval) { repo.update(interval = it) }
        if (repo.configured) {
            Label(
                "${stringResource(R.string.oil_next_at, km(repo.nextAt))} · ${remainingText(repo)}",
                Typography.subtitle,
                if (repo.remaining < 0) c.danger else c.text,
            )
            Button(stringResource(R.string.oil_done_now), { repo.changedNow() })
        }
    }
}

/** Campo de kilómetros: solo dígitos; vacío = sin escribir (-1). */
@Composable
private fun KmField(title: String, value: Int, onChange: (Int) -> Unit) {
    val c = LocalColors.current
    // El texto vive aquí para que se pueda borrar sin que vuelva el número de antes.
    var text by remember(value) { mutableStateOf(if (value >= 0) value.toString() else "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(title, Typography.body, c.text, maxLines = 2, modifier = Modifier.weight(1f))
        TextInput(
            text,
            { new ->
                text = new.filter(Char::isDigit).take(7)
                onChange(text.toIntOrNull() ?: -1)
            },
            stringResource(R.string.oil_km_hint),
            Modifier.width(200.dp),
            keyboardType = KeyboardType.Number,
        )
    }
}
