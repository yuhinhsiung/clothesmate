package com.yuhinhsiung.clothesmate.core.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 粗定位封装：权限检查 + 最近一次位置 + 单次更新（M3-01）。 */
interface LocationProvider {
    fun hasCoarsePermission(): Boolean
    suspend fun getCoarseCoordinates(): WeatherCoordinates?
}

@Singleton
@Suppress("DEPRECATION")
class AndroidLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    override fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun getCoarseCoordinates(): WeatherCoordinates? = withContext(Dispatchers.IO) {
        if (!hasCoarsePermission()) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withContext null

        // 1) 10 分钟内的 lastKnown 足够画天气卡；避免每次都等定位回调。
        val lastKnown = runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull(Location::getTime)
        }.getOrNull()
        if (lastKnown != null &&
            System.currentTimeMillis() - lastKnown.time in 0..LAST_KNOWN_MAX_AGE_MS
        ) {
            return@withContext WeatherCoordinates(lastKnown.latitude, lastKnown.longitude)
        }

        // 2) 单次更新。模拟器/室内可能永远不回调，8 秒后降级为 null。
        val provider = runCatching {
            manager.getBestProvider(
                Criteria().apply {
                    accuracy = Criteria.ACCURACY_COARSE
                    powerRequirement = Criteria.POWER_LOW
                },
                true,
            )
        }.getOrNull() ?: return@withContext null

        suspendCancellableCoroutine { continuation ->
            val settled = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            var listener: LocationListener? = null

            fun finish(coordinates: WeatherCoordinates?) {
                if (settled.compareAndSet(false, true)) {
                    listener?.let { runCatching { manager.removeUpdates(it) } }
                    handler.removeCallbacksAndMessages(null)
                    continuation.resume(coordinates)
                }
            }

            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    finish(WeatherCoordinates(location.latitude, location.longitude))
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
            listener = locationListener

            continuation.invokeOnCancellation {
                settled.set(true)
                handler.removeCallbacksAndMessages(null)
                runCatching { manager.removeUpdates(locationListener) }
            }

            try {
                manager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper())
                handler.postDelayed({ finish(null) }, LOCATION_TIMEOUT_MS)
            } catch (_: SecurityException) {
                finish(null)
            } catch (_: IllegalArgumentException) {
                finish(null)
            }
        }
    }

    private companion object {
        const val LAST_KNOWN_MAX_AGE_MS = 10 * 60 * 1000L
        const val LOCATION_TIMEOUT_MS = 8_000L
    }
}
