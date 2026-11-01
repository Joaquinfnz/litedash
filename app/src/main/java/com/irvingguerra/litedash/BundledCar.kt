package com.irvingguerra.litedash

import android.content.Context
import java.io.File

/**
 * Autos que vienen dentro del APK (assets/autos/<carpeta>): se copian a
 * files/autos la primera vez, o si el APK trae imágenes nuevas, antes de que
 * CarRepository lea los paquetes. Sin internet y sin catálogo.
 */
object BundledCar {
    private const val ASSETS = "autos"
    const val XV_ID = "subaru/xv-2013"

    fun install(context: Context) = runCatching {
        val assets = context.assets
        for (folder in assets.list(ASSETS).orEmpty()) {
            val target = File(File(context.filesDir, ASSETS), folder)
            for (name in assets.list("$ASSETS/$folder").orEmpty()) {
                val out = File(target, name)
                val bytes = assets.open("$ASSETS/$folder/$name").use { it.readBytes() }
                if (out.exists() && out.length() == bytes.size.toLong()) continue
                target.mkdirs()
                out.writeBytes(bytes)
            }
        }
    }
}
