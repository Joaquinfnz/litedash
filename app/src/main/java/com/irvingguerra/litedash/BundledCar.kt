package com.irvingguerra.litedash

import android.content.Context
import java.io.File

/**
 * Autos que vienen dentro del APK (assets/autos/<carpeta>, con su giro/ de
 * cuadros): se copian a files/autos la primera vez, o si el APK trae imágenes
 * nuevas, antes de que CarRepository lea los paquetes. Sin internet ni catálogo.
 */
object BundledCar {
    private const val ASSETS = "autos"
    const val DEFAULT_ID = "subaru/xv-grafito"

    fun install(context: Context) = runCatching {
        copy(context, ASSETS, File(context.filesDir, ASSETS))
    }

    /** Copia recursiva; un archivo igual (mismo tamaño) no se vuelve a escribir. */
    private fun copy(context: Context, path: String, target: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            val bytes = context.assets.open(path).use { it.readBytes() }
            if (target.exists() && target.length() == bytes.size.toLong()) return
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            return
        }
        for (name in children) copy(context, "$path/$name", File(target, name))
    }
}
