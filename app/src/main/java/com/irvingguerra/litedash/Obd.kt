package com.irvingguerra.litedash

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleStartEffect
import com.irvingguerra.litedash.ObdProtocol.Pid
import com.irvingguerra.litedash.core.ui.Button
import com.irvingguerra.litedash.core.ui.Label
import com.irvingguerra.litedash.core.ui.LocalColors
import com.irvingguerra.litedash.core.ui.Typography
import java.io.InputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ObdStatus {
    data object Off : ObdStatus
    data object NoBluetooth : ObdStatus
    data object NoPermission : ObdStatus
    data object NoDevice : ObdStatus
    data object Connecting : ObdStatus
    data class Connected(val elm: String) : ObdStatus
    data class Failed(val reason: String) : ObdStatus
}

/**
 * ELM327 por Bluetooth clásico (SPP). Viene apagado; encendido, se conecta solo
 * mientras algo en pantalla lo pide ([acquire]) y la app está al frente: al abrir
 * CarPlay u otra app se desconecta, así nunca comparte el Bluetooth con la proyección.
 */
class ObdRepository(private val context: Context) {
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    var enabled by mutableStateOf(store.getBoolean(KEY_ON, false)); private set
    var address by mutableStateOf(store.getString(KEY_MAC, null)); private set
    var status by mutableStateOf<ObdStatus>(ObdStatus.Off); private set
    val values = mutableStateMapOf<Pid, Float>()
    var voltage by mutableStateOf<Float?>(null); private set
    var dtcs by mutableStateOf<List<String>?>(null); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var users = 0
    private var detailUsers = 0
    @Volatile private var wantDtcs = false
    private var socket: BluetoothSocket? = null

    private val adapter: BluetoothAdapter? get() = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()

    fun bluetoothAvailable() = adapter != null

    fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Emparejados en el Bluetooth de Android; los que dicen "OBD" o "ELM" primero. */
    @SuppressLint("MissingPermission")
    fun bonded(): List<Pair<String, String>> = runCatching {
        if (!hasPermission()) return emptyList()
        adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address }
            .sortedByDescending { (n, _) -> n.contains("OBD", true) || n.contains("ELM", true) || n.contains("V-LINK", true) }
    }.getOrDefault(emptyList())

    fun enable(on: Boolean) {
        enabled = on
        store.edit { putBoolean(KEY_ON, on) }
        if (!on) stop() else if (users > 0) start()
    }

    fun choose(mac: String) {
        address = mac
        store.edit { putString(KEY_MAC, mac) }
        if (users > 0) { stop(); start() }
    }

    fun requestDtcs() { wantDtcs = true }

    /** [detail] = todos los datos (pantalla de Ajustes); si no, solo temperatura y voltaje (inicio). */
    fun acquire(detail: Boolean) {
        users++
        if (detail) detailUsers++
        if (enabled && job?.isActive != true) start()
    }

    fun release(detail: Boolean) {
        users = (users - 1).coerceAtLeast(0)
        if (detail) detailUsers = (detailUsers - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    private fun stop() {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        if (status !is ObdStatus.Failed) status = ObdStatus.Off
    }

    private fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive && enabled && users > 0) {
                val failure = runCatching { session() }.exceptionOrNull()
                runCatching { socket?.close() }
                socket = null
                if (!isActive) break
                if (failure != null && status !is ObdStatus.NoDevice && status !is ObdStatus.NoBluetooth && status !is ObdStatus.NoPermission) {
                    status = ObdStatus.Failed(failure.message ?: failure.javaClass.simpleName)
                }
                delay(RETRY_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun session() {
        val a = adapter ?: run { status = ObdStatus.NoBluetooth; return }
        if (!hasPermission()) { status = ObdStatus.NoPermission; return }
        val mac = address ?: bonded().firstOrNull()?.second ?: run { status = ObdStatus.NoDevice; return }
        status = ObdStatus.Connecting
        val device = a.getRemoteDevice(mac)
        runCatching { a.cancelDiscovery() }
        // Los clones ELM327 a veces solo aceptan uno de estos tres caminos.
        val s = listOf(
            { device.createRfcommSocketToServiceRecord(SPP) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket },
        ).firstNotNullOfOrNull { make ->
            runCatching { make().also { it.connect() } }.getOrNull()
        } ?: error(context.getString(R.string.obd_error_connect))
        socket = s
        val input = s.inputStream
        val output = s.outputStream
        var elm = ""
        for (cmd in ObdProtocol.INIT) {
            val r = command(input, output, cmd, if (cmd == "ATZ") 3_000 else 1_500)
            if (cmd == "ATZ") elm = Regex("ELM327\\s*v?[\\d.]+", RegexOption.IGNORE_CASE).find(r)?.value ?: "ELM327"
        }
        // La primera consulta busca el protocolo del auto (puede tardar varios segundos).
        command(input, output, "0100", 10_000)
        status = ObdStatus.Connected(elm)
        while (currentCoroutineContextActive() && enabled && users > 0) {
            val pids = if (detailUsers > 0) Pid.entries else listOf(Pid.COOLANT)
            for (pid in pids) {
                ObdProtocol.parse(pid, command(input, output, "01" + pid.code, 1_500))?.let { values[pid] = it } ?: values.remove(pid)
            }
            voltage = ObdProtocol.parseVoltage(command(input, output, "ATRV", 1_000))
            if (wantDtcs) {
                wantDtcs = false
                dtcs = ObdProtocol.parseDtcs(command(input, output, "03", 4_000))
            }
            delay(if (detailUsers > 0) 400 else 2_500)
        }
    }

    private suspend fun currentCoroutineContextActive() = kotlin.coroutines.coroutineContext.isActive

    /** Envía un comando y lee hasta el ">" del ELM (o hasta [timeoutMs]). */
    private suspend fun command(input: InputStream, output: OutputStream, cmd: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        output.write("$cmd\r".toByteArray())
        output.flush()
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (input.available() > 0) {
                val ch = input.read()
                if (ch < 0) break
                if (ch.toChar() == '>') break
                sb.append(ch.toChar())
            } else {
                delay(15)
            }
        }
        sb.toString()
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val STORE = "obd"
        const val KEY_ON = "activo"
        const val KEY_MAC = "adaptador"
        const val RETRY_MS = 8_000L
    }
}

/** Mantiene la conexión solo mientras esto se ve y la app está al frente. */
@Composable
fun KeepObdRunning(obd: ObdRepository, detail: Boolean) {
    LifecycleStartEffect(obd, detail) {
        obd.acquire(detail)
        onStopOrDispose { obd.release(detail) }
    }
}

@Composable
private fun fmt(v: Float, digits: Int): String {
    val f = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    f.minimumFractionDigits = digits
    f.maximumFractionDigits = digits
    return f.format(v)
}

/** Para la barra del inicio: temperatura del motor y voltaje, si el OBD está conectado. */
@Composable
fun ObdBarData(obd: ObdRepository, datum: @Composable (String, String) -> Unit) {
    if (!obd.enabled) return
    KeepObdRunning(obd, detail = false)
    if (obd.status !is ObdStatus.Connected) return
    obd.values[Pid.COOLANT]?.let { datum("${fmt(it, 0)}°", stringResource(R.string.obd_engine_short)) }
    obd.voltage?.let { datum(fmt(it, 1), "V") }
}

/** Ajustes → Tu auto: encender, elegir el adaptador, ver los datos en vivo y leer códigos. */
@Composable
fun ObdSettings(obd: ObdRepository) {
    val c = LocalColors.current
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(statusText(obd.status), Typography.subtitle, if (obd.status is ObdStatus.Failed) c.danger else c.text, maxLines = 2, modifier = Modifier.weight(1f))
            Button(stringResource(if (obd.enabled) R.string.obd_turn_off else R.string.obd_turn_on), {
                if (!obd.enabled && !obd.hasPermission() && Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
                obd.enable(!obd.enabled)
            }, Modifier.width(150.dp), primary = !obd.enabled)
        }
        if (!obd.enabled) return@Column
        KeepObdRunning(obd, detail = true)
        val devices = remember(refresh, obd.status) { obd.bonded() }
        Label(stringResource(R.string.obd_adapter), Typography.label, c.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, mac) in devices) {
                val selected = obd.address == mac || (obd.address == null && devices.first().second == mac)
                Button(name, { obd.choose(mac) }, primary = selected)
            }
            Button(stringResource(R.string.obd_pair), {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
        if (devices.isEmpty()) Label(stringResource(R.string.obd_no_devices), Typography.small, c.textMuted)
        if (obd.status is ObdStatus.Connected) {
            val v = obd.values
            val speed = v[Pid.SPEED]
            val instant = ObdProtocol.instantKmPerLiter(speed, v[Pid.MAF])
            val rows = listOfNotNull(
                v[Pid.RPM]?.let { stringResource(R.string.obd_rpm) to fmt(it, 0) },
                speed?.let { stringResource(R.string.obd_speed) to "${fmt(it, 0)} km/h" },
                v[Pid.COOLANT]?.let { stringResource(R.string.obd_coolant) to "${fmt(it, 0)} °C" },
                v[Pid.INTAKE]?.let { stringResource(R.string.obd_intake) to "${fmt(it, 0)} °C" },
                obd.voltage?.let { stringResource(R.string.obd_voltage) to "${fmt(it, 1)} V" },
                v[Pid.LOAD]?.let { stringResource(R.string.obd_load) to "${fmt(it, 0)} %" },
                v[Pid.THROTTLE]?.let { stringResource(R.string.obd_throttle) to "${fmt(it, 0)} %" },
                v[Pid.MAF]?.let { "MAF" to "${fmt(it, 1)} g/s" },
                v[Pid.STFT]?.let { stringResource(R.string.obd_stft) to "${fmt(it, 1)} %" },
                v[Pid.LTFT]?.let { stringResource(R.string.obd_ltft) to "${fmt(it, 1)} %" },
                instant?.let { stringResource(R.string.obd_instant) to "${fmt(it, 1)} km/L" },
            )
            for ((label, value) in rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(label, Typography.body, c.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
                    Label(value, Typography.subtitle, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(stringResource(R.string.obd_read_codes), { obd.requestDtcs() })
                obd.dtcs?.let { codes ->
                    Label(if (codes.isEmpty()) stringResource(R.string.obd_no_codes) else codes.joinToString("  "), Typography.subtitle, if (codes.isEmpty()) c.text else c.danger, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun statusText(s: ObdStatus): String = when (s) {
    ObdStatus.Off -> stringResource(R.string.obd_status_off)
    ObdStatus.NoBluetooth -> stringResource(R.string.obd_status_no_bt)
    ObdStatus.NoPermission -> stringResource(R.string.obd_status_no_permission)
    ObdStatus.NoDevice -> stringResource(R.string.obd_status_no_device)
    ObdStatus.Connecting -> stringResource(R.string.obd_status_connecting)
    is ObdStatus.Connected -> stringResource(R.string.obd_status_connected, s.elm)
    is ObdStatus.Failed -> stringResource(R.string.obd_status_failed, s.reason)
}
