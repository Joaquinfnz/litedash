package com.irvingguerra.litedash

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography
import java.text.NumberFormat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Una carga de bencina con estanque lleno: el kilometraje al cargar y los litros. */
data class FuelEntry(val id: Long, val km: Int, val liters: Float)

/**
 * Rendimiento por el método de estanque lleno: los km entre dos cargas divididos
 * por los litros de la segunda. Nada en segundo plano; anotar una carga también
 * actualiza el kilometraje del cambio de aceite.
 */
class FuelRepository(context: Context, private val oil: OilRepository) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    var all by mutableStateOf(read())
        private set

    /** km/L desde la primera carga hasta la última; null con menos de dos cargas. */
    val average: Float?
        get() {
            if (all.size < 2) return null
            val km = all.last().km - all.first().km
            val liters = all.drop(1).sumOf { it.liters.toDouble() }.toFloat()
            return if (km > 0 && liters > 0f) km / liters else null
        }

    /** km/L del último estanque. */
    val lastTank: Float?
        get() {
            if (all.size < 2) return null
            val a = all[all.size - 2]
            val b = all.last()
            return if (b.km > a.km && b.liters > 0f) (b.km - a.km) / b.liters else null
        }

    fun add(km: Int, liters: Float) {
        save((all + FuelEntry(System.currentTimeMillis(), km, liters)).sortedBy { it.km })
        if (km > oil.odometer) oil.update(odometer = km)
    }

    fun remove(e: FuelEntry) = save(all - e)

    private fun save(list: List<FuelEntry>) {
        all = list
        store.edit {
            putString(KEY, JSONArray(list.map { JSONObject().put("id", it.id).put("km", it.km).put("litros", it.liters.toDouble()) }).toString())
        }
    }

    private fun read(): List<FuelEntry> = runCatching {
        val a = JSONArray(store.getString(KEY, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { FuelEntry(it.getLong("id"), it.getInt("km"), it.getDouble("litros").toFloat()) } }.sortedBy { it.km }
    }.getOrDefault(emptyList())

    private companion object {
        const val STORE = "bencina"
        const val KEY = "cargas"
    }
}

/** Qué datos del auto se ven en la barra superior del inicio. Todos se pueden apagar. */
class CarInfoPrefs(context: Context) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    var economy by mutableStateOf(store.getBoolean(KEY_ECONOMY, true)); private set
    var weather by mutableStateOf(store.getBoolean(KEY_WEATHER, true)); private set
    var trip by mutableStateOf(store.getBoolean(KEY_TRIP, true)); private set
    var oil by mutableStateOf(store.getBoolean(KEY_OIL, true)); private set

    fun showEconomy(v: Boolean) { economy = v; store.edit { putBoolean(KEY_ECONOMY, v) } }
    fun showWeather(v: Boolean) { weather = v; store.edit { putBoolean(KEY_WEATHER, v) } }
    fun showTrip(v: Boolean) { trip = v; store.edit { putBoolean(KEY_TRIP, v) } }
    fun showOil(v: Boolean) { oil = v; store.edit { putBoolean(KEY_OIL, v) } }

    private companion object {
        const val STORE = "info_auto"
        const val KEY_ECONOMY = "rendimiento"
        const val KEY_WEATHER = "temperatura"
        const val KEY_TRIP = "viaje"
        const val KEY_OIL = "aceite"
    }
}

@Composable
private fun decimal(v: Float, digits: Int = 1): String {
    val f = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    f.minimumFractionDigits = digits
    f.maximumFractionDigits = digits
    return f.format(v)
}

/** Un dato: el número en blanco y la unidad apagada, para leerlo de reojo. */
@Composable
private fun Datum(value: String, unit: String) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Label(value, Typography.subtitle.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
        Label(" $unit", Typography.small, c.textMuted, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/**
 * Datos del auto en la barra superior: viaje, rendimiento, aceite y temperatura,
 * cada uno solo si está encendido y tiene algo que decir.
 */
@Composable
fun CarInfoBar(info: CarInfoPrefs, fuel: FuelRepository, oil: OilRepository, weather: WeatherRepository, trip: TripRepository) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        if (info.trip && trip.hasPermission()) {
            KeepTripRunning(trip)
            if (trip.startedAt != 0L) {
                val minutes by produceState(0, trip.startedAt) {
                    while (true) {
                        value = ((System.currentTimeMillis() - trip.startedAt) / 60_000).toInt()
                        delay(60_000 - System.currentTimeMillis() % 60_000)
                    }
                }
                Datum(decimal((trip.meters / 1000.0).toFloat()), stringResource(R.string.info_trip_unit, minutes / 60, minutes % 60))
            }
        }
        if (info.economy) fuel.average?.let { Datum(decimal(it), "km/L") }
        if (info.oil) OilChip(oil)
        if (info.weather && weather.active) weather.weather?.let { Datum("${it.temperature}°", "") }
    }
}

/** Ajustes → Tu auto: qué datos mostrar arriba en el inicio. */
@Composable
fun CarInfoSettings(info: CarInfoPrefs, weather: WeatherRepository, trip: TripRepository) {
    val enableWeather = rememberEnableWeather(weather)
    var tripGranted by remember { mutableStateOf(trip.hasPermission()) }
    val askTrip = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tripGranted = it }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoToggle(stringResource(R.string.info_economy), stringResource(R.string.info_economy_help), info.economy) { info.showEconomy(it) }
        InfoToggle(stringResource(R.string.oil_change), stringResource(R.string.info_oil_help), info.oil) { info.showOil(it) }
        InfoToggle(stringResource(R.string.info_weather), stringResource(R.string.info_weather_help), info.weather) {
            info.showWeather(it)
            if (it && !weather.active) enableWeather()
        }
        InfoToggle(stringResource(R.string.info_trip), stringResource(R.string.info_trip_help), info.trip) {
            info.showTrip(it)
            if (it && !tripGranted) askTrip.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

@Composable
private fun InfoToggle(title: String, detail: String, on: Boolean, set: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Label(title, Typography.body, maxLines = 1)
            Label(detail, Typography.small, c.textMuted, maxLines = 2)
        }
        Button(stringResource(if (on) R.string.info_on else R.string.info_off), { set(!on) }, Modifier.width(132.dp), primary = on)
    }
}

/** Ajustes → Tu auto: anotar una carga (km y litros) y las últimas cargas. */
@Composable
fun FuelSettings(fuel: FuelRepository, oil: OilRepository) {
    val c = LocalColors.current
    var km by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextInput(km, { km = it.filter(Char::isDigit).take(7) }, stringResource(R.string.fuel_km_hint), Modifier.weight(1f), keyboardType = KeyboardType.Number)
            TextInput(liters, { liters = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(5) }, stringResource(R.string.fuel_liters_hint), Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
            val k = km.toIntOrNull()
            val l = liters.replace(',', '.').toFloatOrNull()
            Button(stringResource(R.string.fuel_add), {
                if (k != null && l != null && l > 0f) {
                    fuel.add(k, l)
                    km = ""
                    liters = ""
                    focus.clearFocus()
                }
            }, primary = k != null && l != null)
        }
        fuel.average?.let { avg ->
            val last = fuel.lastTank
            Label(
                stringResource(R.string.fuel_summary, decimal(avg), last?.let { decimal(it) } ?: "—"),
                Typography.subtitle,
            )
        }
        val list = fuel.all.reversed().take(5)
        for ((i, e) in list.withIndex()) {
            val prev = fuel.all.getOrNull(fuel.all.indexOf(e) - 1)
            val perL = if (prev != null && e.km > prev.km && e.liters > 0f) decimal((e.km - prev.km) / e.liters) + " km/L" else stringResource(R.string.fuel_first)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(
                    "${NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(e.km)} km · ${decimal(e.liters)} L · $perL",
                    Typography.body,
                    if (i == 0) c.text else c.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Button(stringResource(R.string.remove), { fuel.remove(e) })
            }
        }
    }
}
