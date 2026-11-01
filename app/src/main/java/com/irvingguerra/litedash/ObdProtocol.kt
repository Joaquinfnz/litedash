package com.irvingguerra.litedash

/**
 * El idioma del ELM327 (OBD-II modo 01 y 03), sin Bluetooth ni Android:
 * qué pedir y cómo leer la respuesta. Así se prueba en el computador.
 */
object ObdProtocol {
    /** Comandos de arranque: reinicio, sin eco, sin saltos ni espacios ni cabeceras, protocolo automático. */
    val INIT = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")

    /** Un dato en vivo: su PID (modo 01) y cómo convertir los bytes A, B. */
    enum class Pid(val code: String, val bytes: Int, val convert: (Int, Int) -> Float) {
        RPM("0C", 2, { a, b -> (a * 256 + b) / 4f }),
        SPEED("0D", 1, { a, _ -> a.toFloat() }),
        COOLANT("05", 1, { a, _ -> a - 40f }),
        INTAKE("0F", 1, { a, _ -> a - 40f }),
        LOAD("04", 1, { a, _ -> a * 100f / 255f }),
        THROTTLE("11", 1, { a, _ -> a * 100f / 255f }),
        STFT("06", 1, { a, _ -> (a - 128) * 100f / 128f }),
        LTFT("07", 1, { a, _ -> (a - 128) * 100f / 128f }),
        MAF("10", 2, { a, b -> (a * 256 + b) / 100f }),
    }

    /** Limpia lo que devuelve el ELM: sin eco, espacios, "SEARCHING..." ni el ">" final. */
    fun clean(raw: String): String =
        raw.replace("SEARCHING...", "").replace(">", "").replace(" ", "").replace("\r", "\n")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** La respuesta a "01xx": busca "41xx" y convierte; null si no hay dato (NO DATA, error). */
    fun parse(pid: Pid, raw: String): Float? {
        val text = clean(raw).uppercase()
        val key = "41" + pid.code
        val line = text.lines().firstOrNull { it.startsWith(key) } ?: return null
        val hex = line.removePrefix(key)
        if (hex.length < pid.bytes * 2) return null
        val a = hex.substring(0, 2).toIntOrNull(16) ?: return null
        val b = if (pid.bytes > 1) hex.substring(2, 4).toIntOrNull(16) ?: return null else 0
        return pid.convert(a, b)
    }

    /** "ATRV" responde "12.6V". */
    fun parseVoltage(raw: String): Float? =
        Regex("""(\d{1,2}(?:\.\d)?)V""").find(clean(raw).uppercase())?.groupValues?.get(1)?.toFloatOrNull()

    /**
     * Códigos de falla guardados (modo 03): "43" seguido de pares de bytes.
     * P0172 llega como "0172"; los ceros de relleno se descartan.
     */
    fun parseDtcs(raw: String): List<String> {
        val hex = clean(raw).uppercase().lines().filter { it.startsWith("43") }.joinToString("") { it.removePrefix("43") }
        return hex.chunked(4).filter { it.length == 4 && it != "0000" }.mapNotNull { c ->
            val first = c[0].digitToIntOrNull(16) ?: return@mapNotNull null
            val system = "PCBU"[first shr 2]
            "$system${first and 3}${c.substring(1)}"
        }.distinct()
    }

    /**
     * Consumo instantáneo en km/L con el MAF (aire/combustible 14,7 y bencina de
     * 745 g/L): null detenido o sin MAF.
     */
    fun instantKmPerLiter(speedKmh: Float?, mafGs: Float?): Float? {
        if (speedKmh == null || mafGs == null || speedKmh < 3f || mafGs <= 0f) return null
        val litersPerHour = mafGs * 3600f / (14.7f * 745f)
        return speedKmh / litersPerHour
    }
}
