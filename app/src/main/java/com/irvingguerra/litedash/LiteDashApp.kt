package com.irvingguerra.litedash

import android.app.Application
import java.io.File
import android.service.notification.StatusBarNotification
import com.irvingguerra.litedash.feature.updater.Updater
import com.irvingguerra.litedash.feature.apps.AppRepository
import com.irvingguerra.litedash.feature.car.CarRepository
import com.irvingguerra.litedash.feature.music.MusicRepository
import com.irvingguerra.litedash.feature.music.NotificationListener
import com.irvingguerra.litedash.feature.music.NotificationService
import com.irvingguerra.litedash.feature.navigation.NavigationRepository
import com.irvingguerra.litedash.feature.dashboard.DashboardStore
import com.irvingguerra.litedash.feature.dashboard.DashboardState

/**
 * Las dependencias viven aquí (no en la actividad) porque el servicio de avisos
 * también las usa, aun sin la pantalla abierta. Todo es perezoso.
 */
class LiteDashApp : Application() {
    val dependencies: Dependencies by lazy {
        val mapPackages = NAVIGATION_PACKAGES.toSet() +
            if (BuildConfig.DEBUG) setOf(NavigationRepository.TEST_PACKAGE) else emptySet()
        val prefs = Preferences(this)
        // Los XV en imagen fija de la versión anterior se reemplazan por los renders 3D.
        getSharedPreferences("joaquin", MODE_PRIVATE).takeIf { !it.getBoolean("xv_3d", false) }?.let {
            for (old in listOf("subaru-xv-2013", "subaru-xv-plata", "subaru-xv-rally")) File(File(filesDir, "autos"), old).deleteRecursively()
        }
        BundledCar.install(this)
        val oil = OilRepository(this)
        // Tu XV queda elegido la primera vez; se puede cambiar en Ajustes → Tu auto.
        getSharedPreferences("joaquin", MODE_PRIVATE).let { j ->
            if (!j.getBoolean("xv_3d", false)) {
                if (prefs.carModel == null || prefs.carModel!!.startsWith("subaru/xv")) prefs.carModel = BundledCar.DEFAULT_ID
                // El giro empieza en el tres cuartos de frente (como la foto), no en un ángulo viejo.
                prefs.carAngle = null
                j.edit().putBoolean("xv_3d", true).apply()
            }
        }
        Dependencies(
            apps = AppRepository(this),
            music = MusicRepository(this).apply { preferred = prefs.musicApp },
            navigation = NavigationRepository(this, mapPackages),
            prefs = prefs,
            dashboard = DashboardState(DashboardStore(this)),
            cars = CarRepository(this, BuildConfig.CATALOG_URL),
            updater = Updater(this, BuildConfig.UPDATES_URL, BuildConfig.VERSION_CODE.toLong()),
            weather = WeatherRepository(this, prefs),
            trip = TripRepository(this),
            reminders = ReminderRepository(this),
            oil = oil,
            fuel = FuelRepository(this, oil),
            info = CarInfoPrefs(this),
            wheel = WheelKeys(this, prefs),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Los avisos llegan aquí solo si el usuario dio acceso a notificaciones (el mismo de la música).
        NotificationService.listener = object : NotificationListener {
            override fun onPosted(notification: StatusBarNotification) = dependencies.navigation.posted(notification)
            override fun onRemoved(notification: StatusBarNotification) = dependencies.navigation.removed(notification)
        }
    }
}
